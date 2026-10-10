package com.fpclient.android.wear.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.fpclient.android.wear.auth.WearAuthStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.flow.first

private fun WorkoutActivityType.toHealthExerciseType(): ExerciseType = when (this) {
    WorkoutActivityType.RUN -> ExerciseType.RUNNING
    WorkoutActivityType.WALK -> ExerciseType.WALKING
    WorkoutActivityType.HIKE -> ExerciseType.HIKING
    WorkoutActivityType.BIKE -> ExerciseType.BIKING
    WorkoutActivityType.OTHER -> ExerciseType.WORKOUT
}

/**
 * Picks the Health Services [ExerciseType] used to *drive the PPG* so heart rate streams for every
 * workout type, not just Run.
 *
 * Run works today because RUNNING is universally supported and always exposes HEART_RATE_BPM, so
 * [startHealthServices] starts an exercise that keeps the platform `TYPE_HEART_RATE` listener fed.
 * Other types can be missing HEART_RATE_BPM on some watches, which used to drop straight to the
 * bare platform sensor — the exact "--" readout users saw on Walk.
 *
 * The decision itself lives in [pickHeartRateCapableExerciseType] so it can be unit-tested without
 * constructing a real (ProtoParcelable) [ExerciseCapabilities].
 */
private fun pickHeartRateExerciseType(
    capabilities: ExerciseCapabilities,
    preferred: ExerciseType,
): ExerciseType? = pickHeartRateCapableExerciseType(
    supportedTypes = capabilities.supportedExerciseTypes,
    isHeartRateCapable = { type ->
        // Guarded by `type in supportedTypes` inside the decision, mirroring the original code,
        // which only ever queried capabilities for types it had confirmed are supported.
        DataType.HEART_RATE_BPM in capabilities.getExerciseTypeCapabilities(type).supportedDataTypes
    },
    preferred = preferred,
)

/**
 * Pure policy behind [pickHeartRateExerciseType]:
 *  1. Keep the user's selected [preferred] type whenever it can actually stream HEART_RATE_BPM.
 *  2. Otherwise fall back to the closest HR-capable type in a fixed, sensible order, purely to keep
 *     the sensor alive. This only chooses what Health Services exercise runs; the activity saved and
 *     exported remains the user's real `WorkoutActivityType` (read from the session, not from here).
 *  3. Return null only when the watch exposes HEART_RATE_BPM for no exercise type at all, so the
 *     caller can fall back to the platform sensor as a last resort.
 */
internal fun pickHeartRateCapableExerciseType(
    supportedTypes: Set<ExerciseType>,
    isHeartRateCapable: (ExerciseType) -> Boolean,
    preferred: ExerciseType,
): ExerciseType? {
    fun supportsHeartRate(type: ExerciseType): Boolean =
        type in supportedTypes && isHeartRateCapable(type)

    if (supportsHeartRate(preferred)) return preferred

    // Closest sensible fallbacks, tried in order. RUNNING first (the type known to work everywhere),
    // then the other supported workout types, then the generic WORKOUT.
    val fallbackOrder = listOf(
        ExerciseType.RUNNING,
        ExerciseType.WALKING,
        ExerciseType.HIKING,
        ExerciseType.BIKING,
        ExerciseType.WORKOUT,
    )
    return fallbackOrder.firstOrNull { supportsHeartRate(it) }
        // Last resort: any supported type that happens to expose HEART_RATE_BPM.
        ?: supportedTypes.firstOrNull { supportsHeartRate(it) }
}

class WorkoutRecordingService : Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessionStore: WorkoutSessionStore
    private lateinit var trackStore: WorkoutTrackStore
    private lateinit var syncStore: WatchWorkoutSyncStore
    private lateinit var metrics: WorkoutMetricsAccumulator
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var session: WorkoutSessionSnapshot? = null
    private var availability = WorkoutSensorAvailability()
    private var stepSensor: Sensor? = null
    private var heartRateSensor: Sensor? = null

    /** Monotonic timestamp of the last PPG (re-)registration, used by the HR watchdog. */
    private var heartRateSensorArmedAtMs = 0L

    /** True once any heart-rate value has actually reached [publishHeartRate] during this workout. */
    private var heartRateEverReceived = false

    /**
     * True when the PPG last reported no skin contact. Kept so the UI can explain a "--" readout
     * (watch off wrist) rather than showing a bare dash that reads like a fault. Cleared on the
     * first valid BPM.
     */
    private var heartRateNoContact = false

    /** How many times the HR watchdog has re-armed this workout; caps the recovery loop. */
    private var heartRateRecoveryAttempts = 0

    /**
     * True while *we* are tearing the Health Services exercise down on purpose (the HR watchdog).
     * Without this, the ENDING/ENDED updates that [releaseHealthServicesForPlatformSensor] triggers
     * look identical to the user ending the exercise, and `onExerciseUpdateReceived`'s
     * `state.isEnded` branch would call [stopSession] — killing a live workout a few seconds in.
     */
    private var healthServicesReleasingForHeartRate = false
    private var locationActive = false
    private var sensorsActive = false
    private var healthExerciseActive = false
    private var healthExercisePaused = false
    private var healthServicesAvailable = false
    private var healthHeartRateAvailable = false
    private var healthExerciseClient: ExerciseClient? = null
    private var errorMessage: String? = null
    private var healthCallback: ExerciseUpdateCallback? = null
    private var ticker: Job? = null
    private var ambientMode = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onGpsFix(location)
        override fun onProviderEnabled(provider: String) = refreshAvailability()
        override fun onProviderDisabled(provider: String) = refreshAvailability()
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        sessionStore = WorkoutSessionStore(this)
        trackStore = WorkoutTrackStore(filesDir.resolve(TRACK_DIRECTORY))
        syncStore = WatchWorkoutSyncStore(this)
        WorkoutRecordingBus.publishPendingCount(syncStore.all().size)
        metrics = WorkoutMetricsAccumulator()
        sensorManager = getSystemService(SensorManager::class.java)
        locationManager = getSystemService(LocationManager::class.java)
        createNotificationChannel()
        restoreSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WorkoutRecordingController.ACTION_START -> {
                if (session == null || session?.status == WorkoutStatus.STOPPED) {
                    beginSession(intent.getStringExtra(WorkoutRecordingController.EXTRA_ACTIVITY_TYPE))
                }
                else promoteAndRestartSensors()
            }
            WorkoutRecordingController.ACTION_PAUSE -> pauseSession()
            WorkoutRecordingController.ACTION_RESUME -> resumeSession()
            WorkoutRecordingController.ACTION_STOP -> stopSession()
            WorkoutRecordingController.ACTION_AMBIENT -> {
                ambientMode = intent.getBooleanExtra(WorkoutRecordingController.EXTRA_IS_AMBIENT, false)
                if (session?.status == WorkoutStatus.RECORDING) {
                    ticker?.cancel()
                    startTicker()
                }
            }
            else -> restoreAfterRestart()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        stopLocation()
        stopPlatformSensors()
        trackStore.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    override fun onSensorChanged(event: SensorEvent) {
        if (session?.status != WorkoutStatus.RECORDING) return
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val v = event.values.firstOrNull()?.toInt()
                Log.i(TAG, "onSensorChanged TYPE_HEART_RATE raw=$v accuracy=${event.accuracy}")
                // accuracy == SENSOR_STATUS_NO_CONTACT (2) or UNRELIABLE (0) with a 0 value is the
                // watch being off the wrist — the PPG has nothing to measure. A 0 is not a valid BPM
                // and publishHeartRate() discards it, so track the reason here for the UI.
                heartRateNoContact = v == 0 || event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT
                if (v != null && v != 0) heartRateNoContact = false
                publishHeartRate(v)
            }
            Sensor.TYPE_STEP_COUNTER -> onStepCounter(event.values.firstOrNull())
            Sensor.TYPE_STEP_DETECTOR -> onStepDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.i(TAG, "onAccuracyChanged sensor=${sensor?.type} accuracy=$accuracy")
        if (sensor?.type == Sensor.TYPE_HEART_RATE) {
            // SENSOR_STATUS_NO_CONTACT = 2: the watch is off the wrist.
            heartRateNoContact = accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT
        }
    }

    private fun beginSession(activityTypeName: String?) {
        val now = System.currentTimeMillis()
        errorMessage = null
        metrics = WorkoutMetricsAccumulator()
        heartRateEverReceived = false
        heartRateRecoveryAttempts = 0
        heartRateSensorArmedAtMs = 0L
        healthServicesReleasingForHeartRate = false
        heartRateNoContact = false
        val fresh = WorkoutSessionTransitions.start(
            now,
            WorkoutActivityType.fromStorage(activityTypeName),
            SystemClock.elapsedRealtime(),
        )
        session = fresh
        sessionStore.save(fresh)
        if (!appendBoundary(fresh.startedAtEpochMs, "START")) {
            stopSession()
            return
        }
        publish()
        promoteAndRestartSensors()
        startTicker()
    }

    private fun pauseSession() {
        val current = session?.takeIf { it.status == WorkoutStatus.RECORDING } ?: return
        val now = System.currentTimeMillis()
        session = WorkoutSessionTransitions.pause(current, now) ?: return
        sessionStore.save(session!!)
        if (!appendBoundary(current.startedAtEpochMs, "PAUSE")) {
            stopSession()
            return
        }
        metrics.clearSegment()
        stopSensors()
        pauseHealthExercise()
        ticker?.cancel()
        publish()
        refreshNotification()
    }

    private fun resumeSession() {
        val current = session?.takeIf { it.status == WorkoutStatus.PAUSED } ?: return
        val now = System.currentTimeMillis()
        session = WorkoutSessionTransitions.resume(current, now) ?: return
        sessionStore.save(session!!)
        if (!appendBoundary(current.startedAtEpochMs, "RESUME")) {
            stopSession()
            return
        }
        metrics.clearSegment()
        publish()
        promoteAndRestartSensors()
        startTicker()
    }

    private fun stopSession() {
        val current = session ?: run {
            stopSelf()
            return
        }
        val stoppedAt = System.currentTimeMillis()
        session = WorkoutSessionTransitions.stop(current, stoppedAt)
        val stoppedSession = session!!
        sessionStore.save(session!!)
        runCatching {
            trackStore.append(current.startedAtEpochMs, WorkoutTrackEvent(System.currentTimeMillis(), boundary = "STOP"))
        }
        stopSensors()
        ticker?.cancel()
        val client = healthExerciseClient
        val callback = healthCallback
        val shouldEndExercise = healthExerciseActive
        healthExerciseActive = false
        healthExercisePaused = false
        healthServicesAvailable = false
        healthHeartRateAvailable = false
        healthCallback = null
        serviceScope.launch {
            if (queueStoppedWorkout(stoppedSession, stoppedAt)) playCompletionFeedback()
            if (shouldEndExercise && client != null) {
                runCatching { client.endExerciseAsync().await() }
                if (callback != null) runCatching { client.clearUpdateCallbackAsync(callback).await() }
            }
            publish()
            ServiceCompat.stopForeground(this@WorkoutRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun restoreSession() {
        val restored = sessionStore.load() ?: return
        session = restored
        metrics = WorkoutMetricsAccumulator()
        // Only replay the track when the restored session is actually ongoing. A STOPPED session's
        // metrics are reset by the next beginSession() anyway, and reading a large track file on
        // the main thread here would delay service start (freezing the Start button) for no benefit.
        if (restored.status == WorkoutStatus.RECORDING || restored.status == WorkoutStatus.PAUSED) {
            trackStore.readEvents(restored.startedAtEpochMs).forEach(metrics::add)
        }
        publish()
    }

    private fun restoreAfterRestart() {
        val current = session ?: sessionStore.load()?.also { session = it } ?: run {
            stopSelf()
            return
        }
        metrics = WorkoutMetricsAccumulator()
        trackStore.readEvents(current.startedAtEpochMs).forEach(metrics::add)
        publish()
        when (current.status) {
            WorkoutStatus.RECORDING -> promoteAndRestartSensors()
            WorkoutStatus.STOPPED -> serviceScope.launch {
                queueStoppedWorkout(current, System.currentTimeMillis())
                stopSelf()
            }
            else -> stopSelf()
        }
    }

    private suspend fun queueStoppedWorkout(stoppedSession: WorkoutSessionSnapshot, stoppedAt: Long): Boolean {
        if (syncStore.get(stoppedSession.startedAtEpochMs) != null) return true
        val events = trackStore.readEvents(stoppedSession.startedAtEpochMs)
        val title = "${stoppedSession.activityType.label} workout"
        val exported = runCatching {
            WorkoutExportWriter.write(filesDir.resolve(TRACK_DIRECTORY), stoppedSession, events, stoppedAt, title)
        }.getOrElse {
            publishError("Workout export could not be saved. Check watch storage.")
            return false
        }
        val auth = WearAuthStore(this).state.first()
        syncStore.upsert(
            PendingWatchWorkout(
                sessionId = stoppedSession.startedAtEpochMs,
                gpxFileName = exported.gpxFile.name,
                sidecarFileName = exported.sidecarFile.name,
                activityType = stoppedSession.activityType.name,
                title = title,
                ownerServerUrl = auth.serverUrl.takeIf { auth.isSignedIn }.orEmpty(),
                ownerUsername = auth.username.takeIf { auth.isSignedIn }.orEmpty(),
                createdAtEpochMs = stoppedAt,
            ),
        )
        WorkoutSyncScheduler.enqueue(this)
        return true
    }

    private fun promoteAndRestartSensors() {
        if (session?.status != WorkoutStatus.RECORDING) return
        if (!enterForeground()) return
        startTicker()
        refreshAvailability()
        reportHeartRateBlockerIfNeeded()
        startLocation()
        startPlatformSensors()
        serviceScope.launch { startHealthServices() }
    }

    private fun enterForeground(): Boolean {
        val foregroundTypes = foregroundServiceTypes()
        if (Build.VERSION.SDK_INT >= 34 && foregroundTypes == 0) {
            publishError("Grant heart-rate, activity, or location permission to start a workout.")
            session = null
            sessionStore.clear()
            stopSelf()
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundTypes)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            playStartFeedback()
            true
        } catch (_: SecurityException) {
            publishError("Workout permissions changed. Grant access and try again.")
            session = null
            sessionStore.clear()
            stopSelf()
            false
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException after the user force-stopped the
            // app. Never crash the service mid-workout — abort cleanly with a retryable message.
            publishError("Could not enter workout foreground (${e.javaClass.simpleName}). Retry.")
            session = null
            sessionStore.clear()
            stopSelf()
            false
        }
    }

    private fun foregroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < 34) return 0
        var types = 0
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (hasHeartRatePermission() || hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        return types
    }

    private fun startLocation() {
        if (locationActive || !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || !gpsProviderAvailable()) {
            refreshAvailability()
            return
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                LOCATION_MIN_DISTANCE_METERS,
                locationListener,
                mainLooper,
            )
            locationActive = true
        } catch (_: SecurityException) {
            locationActive = false
        } catch (_: IllegalArgumentException) {
            locationActive = false
        }
        refreshAvailability()
    }

    private fun stopLocation() {
        if (!locationActive) return
        locationManager.removeUpdates(locationListener)
        locationActive = false
    }

    private fun onGpsFix(location: Location) {
        val current = session?.takeIf { it.status == WorkoutStatus.RECORDING } ?: return
        if (!location.hasAccuracy() || !WorkoutMath.isAcceptableAccuracy(location.accuracy.toDouble())) return
        val now = if (location.time > 0L) location.time else System.currentTimeMillis()
        val event = WorkoutTrackEvent(
            timeEpochMs = now,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = location.takeIf { it.hasAltitude() }?.altitude,
            accuracyMeters = location.accuracy.toDouble(),
        )
        appendEvent(current, event)
    }

    private fun startPlatformSensors() {
        if (sensorsActive) return
        val activityAllowed = Build.VERSION.SDK_INT < 29 || hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
        stepSensor = if (activityAllowed) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        } else null

        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sensorsActive = stepSensor != null
        // Also register the platform heart-rate sensor so HR keeps recording if Health Services
        // reports available but never delivers an update (it is torn down only once Health
        // Services actually provides a value).
        ensureHeartRateSensor()
        refreshAvailability()
    }

    /**
     * Arms the platform PPG listener. Idempotent, but *re-armable*: every caller that used to be a
     * no-op because [heartRateSensor] was already set now genuinely re-registers the listener.
     *
     * This is the fix for "--" BPM readouts. While a Health Services exercise runs it holds the PPG
     * exclusively, so an already-registered listener is starved silently — no callback, no error.
     * The old `if (heartRateSensor != null) return` guard meant no recovery path could ever
     * re-register it, leaving HR dead for the rest of the workout (and only "fixed" by restarting
     * the app, which built a fresh service with a null listener).
     */
    private fun ensureHeartRateSensor() {
        if (!hasHeartRatePermission()) {
            Log.w(TAG, "ensureHeartRateSensor: no HR permission, skipping")
            return
        }
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (sensor == null) {
            Log.w(TAG, "ensureHeartRateSensor: no TYPE_HEART_RATE sensor present")
            return
        }
        // Unregister before re-registering: registering the same listener/sensor pair again is a
        // no-op on the framework side, so without this the "re-arm" would not take effect.
        sensorManager.unregisterListener(this, sensor)
        val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        Log.i(TAG, "ensureHeartRateSensor: registerListener ok=$ok sensor=${sensor.name}")
        heartRateSensor = sensor
        sensorsActive = true
        heartRateSensorArmedAtMs = SystemClock.elapsedRealtime()
        refreshAvailability()
    }

    private fun stopFallbackHeartRate() {
        // Keep the hardware PPG heart-rate sensor active continuously during recording
        // so live BPM updates stream reliably across device variants.
    }

    private fun stopPlatformSensors() {
        if (!sensorsActive) return
        sensorManager.unregisterListener(this)
        sensorsActive = false
        heartRateSensor = null
        stepSensor = null
    }

    private fun onStepCounter(rawValue: Float?) {
        val raw = rawValue?.takeIf { it >= 0f } ?: return
        val previous = metrics.rawStepCounter
        val newTotal = when {
            previous == null -> metrics.steps
            raw >= previous -> metrics.steps + (raw - previous).toInt()
            else -> metrics.steps
        }
        val current = session ?: return
        appendEvent(
            current,
            WorkoutTrackEvent(
                timeEpochMs = System.currentTimeMillis(),
                steps = newTotal,
                rawStepCounter = raw,
            ),
        )
    }

    private fun onStepDetected() {
        val current = session ?: return
        appendEvent(
            current,
            WorkoutTrackEvent(timeEpochMs = System.currentTimeMillis(), steps = metrics.steps + 1),
        )
    }

    private fun publishHeartRate(value: Int?) {
        val bpm = value?.takeIf { it in MIN_HEART_RATE_BPM..MAX_HEART_RATE_BPM } ?: return
        val current = session?.takeIf { it.status == WorkoutStatus.RECORDING } ?: return
        heartRateEverReceived = true
        // First live reading clears the heart-rate blocker notice, so it does not linger over a
        // readout that is now working.
        if (errorMessage == HEART_RATE_PERMISSION_HINT) errorMessage = null
        appendEvent(current, WorkoutTrackEvent(timeEpochMs = System.currentTimeMillis(), heartRateBpm = bpm))
    }

    /**
     * A workout can start on location alone, so a missing body-sensors permission would otherwise be
     * invisible: the session records fine and only the BPM readout stays "--". Report it once, with
     * an actionable message, instead of leaving the user to guess.
     */
    private fun reportHeartRateBlockerIfNeeded() {
        if (hasHeartRatePermission()) return
        if (errorMessage != null) return
        errorMessage = HEART_RATE_PERMISSION_HINT
    }

    private suspend fun startHealthServices() {
        if (session?.status != WorkoutStatus.RECORDING) return
        if (!hasHeartRatePermission()) {
            healthServicesAvailable = false
            healthHeartRateAvailable = false
            ensureHeartRateSensor()
            refreshAvailability()
            return
        }
        val client = runCatching { HealthServices.getClient(this).exerciseClient }.getOrNull() ?: run {
            healthServicesAvailable = false
            healthHeartRateAvailable = false
            ensureHeartRateSensor()
            refreshAvailability()
            return
        }
        healthExerciseClient = client
        if (healthExerciseActive) {
            if (healthExercisePaused) {
                runCatching { client.resumeExerciseAsync().await() }
                    .onSuccess { healthExercisePaused = false }
                    .onFailure {
                        healthExerciseActive = false
                        healthHeartRateAvailable = false
                        ensureHeartRateSensor()
                    }
            }
            return
        }
        try {
            val capabilities = client.getCapabilitiesAsync().await()
            val preferredType = session?.activityType?.toHealthExerciseType() ?: ExerciseType.WORKOUT
            // Drive the PPG through a Health Services exercise for EVERY workout type, exactly the
            // way Run already does. The selected type is kept when it can stream heart rate; when a
            // watch does not offer HEART_RATE_BPM for that specific type (or the type itself is
            // unsupported), fall back to the closest HR-capable type purely to *drive the sensor*.
            // The saved/exported activity stays the user's real selection — it is read from
            // session.activityType (a WorkoutActivityType), never from this Health Services type.
            val exerciseType = pickHeartRateExerciseType(capabilities, preferredType)
            if (exerciseType == null) {
                Log.w(TAG, "HS: no exercise type supports HEART_RATE_BPM — falling back to platform sensor")
                healthServicesAvailable = false
                healthHeartRateAvailable = false
                ensureHeartRateSensor()
                refreshAvailability()
                return
            }
            Log.i(TAG, "HS capabilities ok: preferred=$preferredType using=$exerciseType supportsHR=true")

            val callback = object : ExerciseUpdateCallback {
                override fun onRegistered() {
                    Log.i(TAG, "HS onRegistered")
                }

                override fun onRegistrationFailed(throwable: Throwable) {
                    Log.w(TAG, "HS onRegistrationFailed: ${throwable.message}")
                    healthExerciseActive = false
                    healthServicesAvailable = false
                    healthHeartRateAvailable = false
                    ensureHeartRateSensor()
                    refreshAvailability()
                }

                override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                    val latestHeartRate = update.latestMetrics
                        .getData(DataType.HEART_RATE_BPM)
                        .lastOrNull()
                        ?.value
                        ?.toInt()
                    Log.i(TAG, "HS onExerciseUpdateReceived hr=$latestHeartRate state=${update.exerciseStateInfo.state}")
                    if (latestHeartRate != null) publishHeartRate(latestHeartRate)
                    // Only treat an ENDING/ENDED update as "the exercise is over" when WE did not
                    // initiate the teardown. The HR watchdog ends the exercise on purpose to reclaim
                    // the PPG; honouring that as a user-ended workout would stop the recording.
                    if (update.exerciseStateInfo.state.isEnded && !healthServicesReleasingForHeartRate && session?.status == WorkoutStatus.RECORDING) {
                        stopSession()
                    }
                }

                override fun onLapSummaryReceived(lapSummary: androidx.health.services.client.data.ExerciseLapSummary) = Unit

                override fun onAvailabilityChanged(
                    dataType: androidx.health.services.client.data.DataType<*, *>,
                    availability: androidx.health.services.client.data.Availability,
                ) {
                    Log.i(TAG, "HS onAvailabilityChanged dt=$dataType avail=$availability")
                    if (dataType == DataType.HEART_RATE_BPM && availability is DataTypeAvailability) {
                        healthHeartRateAvailable = availability == DataTypeAvailability.AVAILABLE ||
                            availability == DataTypeAvailability.ACQUIRING
                        if (!healthHeartRateAvailable) ensureHeartRateSensor()
                        refreshAvailability()
                    }
                }
            }

            client.setUpdateCallback(callback)
            val existingExercise = runCatching { client.getCurrentExerciseInfoAsync().await() }.getOrNull()
            if (existingExercise?.exerciseType == null || existingExercise.exerciseType == ExerciseType.UNKNOWN) {
                client.startExerciseAsync(
                    ExerciseConfig(
                        exerciseType = exerciseType,
                        dataTypes = setOf(DataType.HEART_RATE_BPM),
                        isAutoPauseAndResumeEnabled = false,
                        isGpsEnabled = false,
                    ),
                ).await()
            } else {
                val resumed = runCatching { client.resumeExerciseAsync().await() }.isSuccess
                if (!resumed) {
                    runCatching { client.clearUpdateCallbackAsync(callback).await() }
                    healthServicesAvailable = false
                    healthHeartRateAvailable = false
                    ensureHeartRateSensor()
                    refreshAvailability()
                    return
                }
            }
            healthCallback = callback
            healthExerciseActive = true
            healthServicesReleasingForHeartRate = false
            healthExercisePaused = false
            healthServicesAvailable = true
            healthHeartRateAvailable = true
            refreshAvailability()
        } catch (_: Exception) {
            healthCallback = null
            healthExerciseActive = false
            healthServicesAvailable = false
            healthHeartRateAvailable = false
            ensureHeartRateSensor()
            refreshAvailability()
        }
    }

    private fun pauseHealthExercise() {
        val client = healthExerciseClient ?: return
        if (!healthExerciseActive || healthExercisePaused) return
        serviceScope.launch {
            runCatching { client.pauseExerciseAsync().await() }
                .onSuccess { healthExercisePaused = true }
                .onFailure {
                    healthExerciseActive = false
                    healthHeartRateAvailable = false
                    ensureHeartRateSensor()
                    refreshAvailability()
                }
        }
    }

    private fun stopSensors() {
        stopLocation()
        stopPlatformSensors()
    }

    private fun appendBoundary(sessionId: Long, boundary: String): Boolean = runCatching {
        trackStore.append(sessionId, WorkoutTrackEvent(System.currentTimeMillis(), boundary = boundary))
    }.onFailure {
        publishError("Workout data could not be saved. Stop the workout and check watch storage.")
    }.isSuccess

    private fun appendEvent(current: WorkoutSessionSnapshot, event: WorkoutTrackEvent) {
        runCatching {
            trackStore.append(current.startedAtEpochMs, event)
            metrics.add(event)
            if (!ambientMode) publish()
        }.onFailure {
            publishError("Workout data could not be saved. Stop the workout and check watch storage.")
            stopSession()
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = serviceScope.launch {
            while (isActive && session?.status == WorkoutStatus.RECORDING) {
                publish()
                watchdogHeartRate()
                delay(if (ambientMode) AMBIENT_TICK_INTERVAL_MS else TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * Revives a starved heart-rate listener. If a workout is recording, the PPG exists and is
     * permitted, yet not a single BPM has arrived within [HEART_RATE_WATCHDOG_MS] of arming, the
     * listener is being starved — by far the most common cause is Health Services holding the PPG
     * exclusively while its exercise runs, which silences the platform listener with no callback and
     * no error. Releasing that exercise hands the sensor back, and [ensureHeartRateSensor] re-arms.
     *
     * This is what turns a permanently "--" workout into a recovering one. Bounded by
     * [MAX_HEART_RATE_RECOVERY_ATTEMPTS] so a watch that is simply not on the wrist cannot spin.
     */
    private fun watchdogHeartRate() {
        if (session?.status != WorkoutStatus.RECORDING) return
        if (heartRateEverReceived) return
        if (!hasHeartRatePermission()) return
        if (heartRateSensorArmedAtMs == 0L) return
        if (heartRateRecoveryAttempts >= MAX_HEART_RATE_RECOVERY_ATTEMPTS) return
        if (SystemClock.elapsedRealtime() - heartRateSensorArmedAtMs < HEART_RATE_WATCHDOG_MS) return
        heartRateRecoveryAttempts++
        Log.w(TAG, "HR watchdog firing: no BPM within window, attempt=$heartRateRecoveryAttempts, hsActive=$healthExerciseActive — releasing HS and re-arming platform sensor")
        if (healthExerciseActive) releaseHealthServicesForPlatformSensor()
        ensureHeartRateSensor()
    }

    /**
     * Ends the Health Services exercise so the platform PPG listener can take the sensor back.
     * Flags are cleared synchronously; the teardown itself is fire-and-forget so the watchdog never
     * blocks the ticker.
     */
    private fun releaseHealthServicesForPlatformSensor() {
        val client = healthExerciseClient
        val callback = healthCallback
        healthExerciseActive = false
        healthExercisePaused = false
        healthServicesAvailable = false
        healthHeartRateAvailable = false
        healthCallback = null
        healthServicesReleasingForHeartRate = true
        serviceScope.launch {
            runCatching { client?.endExerciseAsync()?.await() }
            if (client != null && callback != null) runCatching { client.clearUpdateCallbackAsync(callback).await() }
        }
    }

    private fun publish() {
        val current = session
        val now = System.currentTimeMillis()
        val movingMs = current?.movingMsAt(now) ?: 0L
        WorkoutRecordingBus.publish(
            WorkoutRecordingSnapshot(
                session = current,
                elapsedMs = current?.liveElapsedMs(SystemClock.elapsedRealtime(), now) ?: 0L,
                movingMs = movingMs,
                heartRateBpm = metrics.heartRateBpm,
                distanceMeters = metrics.distanceMeters,
                paceSecondsPerKm = WorkoutMath.paceSecondsPerKm(movingMs, metrics.distanceMeters),
                steps = metrics.steps,
                availability = availability,
                errorMessage = errorMessage,
            ),
        )
    }

    private fun publishError(message: String) {
        errorMessage = message
        publish()
    }

    private fun refreshAvailability() {
        val gps = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && gpsProviderAvailable()
        val heartRatePermission = hasHeartRatePermission()
        val activityAllowed = Build.VERSION.SDK_INT < 29 || hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
        val hrSensorPresent = heartRatePermission && sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
        val stepPresent = activityAllowed && (
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
            )
        availability = WorkoutSensorAvailability(
            gps = gps,
            healthServices = healthServicesAvailable,
            // Honest signal: only claim HR once a BPM has actually arrived, or while a source is
            // still plausibly warming up (Health Services acquiring, or a freshly armed PPG).
            // Claiming availability from "the sensor object exists" is what let the UI advertise
            // "HR" above a permanent "--" readout.
            heartRate = heartRateEverReceived ||
                (healthHeartRateAvailable && healthServicesAvailable) ||
                (hrSensorPresent && !hasExceededHeartRateRecovery()),
            steps = stepPresent,
            noContact = heartRateNoContact,
        )
        publish()
    }

    /** True once the watchdog has given up re-arming the PPG for this workout. */
    private fun hasExceededHeartRateRecovery(): Boolean =
        heartRateRecoveryAttempts >= MAX_HEART_RATE_RECOVERY_ATTEMPTS

    private fun hasHeartRatePermission(): Boolean = WorkoutRecordingController.hasHeartRatePermission(this)

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun gpsProviderAvailable(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) &&
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Workout recording", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun refreshNotification() {
        if (session?.status == WorkoutStatus.RECORDING || session?.status == WorkoutStatus.PAUSED) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val current = session
        val paused = current?.status == WorkoutStatus.PAUSED
        val elapsedMs = current?.liveElapsedMs(SystemClock.elapsedRealtime(), System.currentTimeMillis()) ?: 0L
        val elapsedSeconds = elapsedMs / 1000L
        val title = if (paused) "Workout paused" else "Workout recording"
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.fpclient.android.wear.WearMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val action = if (paused) WorkoutRecordingController.ACTION_RESUME else WorkoutRecordingController.ACTION_PAUSE
        val actionLabel = if (paused) "Resume" else "Pause"
        val actionIcon = if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText("${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')} elapsed")
            .setWhen(System.currentTimeMillis() - elapsedMs)
            .setUsesChronometer(!paused)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(actionIcon, actionLabel, servicePendingIntent(action, action.hashCode()))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", servicePendingIntent(WorkoutRecordingController.ACTION_STOP, 2))

        val status = Status.Builder()
            .addTemplate("#activity# #time#")
            .addPart("activity", Status.TextPart(current?.activityType?.label ?: "Workout"))
            .addPart(
                "time",
                if (paused) {
                    Status.TextPart("${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')} paused")
                } else {
                    Status.StopwatchPart(SystemClock.elapsedRealtime() - elapsedMs)
                },
            )
            .build()
        val ongoingActivity = OngoingActivity.Builder(this, NOTIFICATION_ID, notificationBuilder)
            .setStaticIcon(com.fpclient.android.wear.R.drawable.ic_launcher_foreground)
            .setTouchIntent(contentIntent)
            .setTitle("${current?.activityType?.label ?: "Workout"} in progress")
            .setContentDescription("${current?.activityType?.label ?: "Workout"} recording")
            .setStatus(status)
            .build()
        if (Build.VERSION.SDK_INT < 33 || hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            ongoingActivity.apply(this)
        }
        return notificationBuilder.build()
    }

    private fun playStartFeedback() {
        runCatching {
            @Suppress("DEPRECATION")
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Beep (default notification sound) + vibration when a workout is saved. */
    private fun playCompletionFeedback() {
        runCatching {
            RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ?.play()
        }
        runCatching {
            @Suppress("DEPRECATION")
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun servicePendingIntent(action: String, requestCode: Int) = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, WorkoutRecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "fitpub_workout_recording"
        internal const val TAG = "FitPubWearHR"
        private const val NOTIFICATION_ID = 9301
        private const val TRACK_DIRECTORY = "workouts"
        private const val LOCATION_INTERVAL_MS = 1_000L
        private const val LOCATION_MIN_DISTANCE_METERS = 1f
        private const val TICK_INTERVAL_MS = 1_000L
        private const val AMBIENT_TICK_INTERVAL_MS = 60_000L
        private const val MIN_HEART_RATE_BPM = 20
        private const val MAX_HEART_RATE_BPM = 250

        /** No BPM within this window of arming means the listener is starved, not idle. */
        private const val HEART_RATE_WATCHDOG_MS = 10_000L

        /** Caps watchdog re-arms per workout so a watch off the wrist cannot loop forever. */
        private const val MAX_HEART_RATE_RECOVERY_ATTEMPTS = 3

        /**
         * Shown when a workout is running but the body-sensors permission is absent — the workout
         * starts anyway on location alone, so without this the only symptom is a silent "--" BPM.
         */
        private const val HEART_RATE_PERMISSION_HINT =
            "Heart rate needs the Body sensors permission. Open Settings → Grant sensor access."
    }
}
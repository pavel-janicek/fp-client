package com.fpclient.android.wear.recording

import com.fpclient.android.wear.WearLog
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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.fpclient.android.wear.auth.WearAuthStore
import com.fpclient.android.wear.tile.requestWorkoutTileUpdate
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
    private var stepSensors: List<Sensor> = emptyList()

    /**
     * Step total as derived from TYPE_STEP_COUNTER deltas alone. Kept apart from the
     * detector's per-step increments so the two sources never add up to a double count — the
     * recorded total is always the larger of the two.
     */
    private var counterSteps = 0

    /** Last raw TYPE_STEP_COUNTER value; null until the first callback of each registration. */
    private var lastRawStepCounter: Float? = null
    private var heartRateSensor: Sensor? = null

    /** Monotonic timestamp of the last PPG (re-)registration, used by the HR watchdog. */
    private var heartRateSensorArmedAtMs = 0L

    /** True once any heart-rate value has actually reached [publishHeartRate] during this workout. */
    private var heartRateEverReceived = false

    /**
     * Monotonic timestamp of the last heart-rate callback of *any* kind — including the 0 /
     * no-contact readings the PPG emits while it warms up or is off the wrist. Any callback proves
     * the listener is alive, so the watchdog must leave it alone.
     */
    private var heartRateLastEventAtMs = 0L

    /** Wall-clock time of the last valid BPM published this service run; null until one arrives. */
    private var heartRateAtEpochMs: Long? = null

    /**
     * True when the PPG last reported no skin contact. Kept so the UI can explain a "--" readout
     * (watch off wrist) rather than showing a bare dash that reads like a fault. Cleared on the
     * first valid BPM.
     */
    private var heartRateNoContact = false

    /** How many times the HR watchdog has re-armed this workout; caps the recovery loop. */
    private var heartRateRecoveryAttempts = 0

    private var locationActive = false

    /**
     * Wall-clock time the platform sensors were last registered. An on-change sensor (the step
     * counter) replays its last value on registration with that value's original, pre-workout
     * timestamp; sample times are never placed before this.
     */
    private var sensorsRegisteredAtEpochMs = 0L
    private var sensorsActive = false
    private var errorMessage: String? = null
    private var ticker: Job? = null

    /**
     * True while nobody is looking at the live workout: the screen is dimmed (ambient), the user
     * left the workout screen, or the app is in the background. Live readouts are then throttled
     * to one publish a minute.
     */
    private var lowPowerMode = false

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
                // Start is always a tap on an interactive screen; never inherit a stale low-power
                // flag, which would throttle the live readout (BPM included) to once a minute.
                lowPowerMode = false
                session?.takeIf(::isInterruptedByReboot)?.let(::finishInterruptedSession)
                if (session == null || session?.status == WorkoutStatus.STOPPED) {
                    beginSession(intent.getStringExtra(WorkoutRecordingController.EXTRA_ACTIVITY_TYPE))
                }
                else promoteAndRestartSensors()
            }
            WorkoutRecordingController.ACTION_PAUSE -> pauseSession()
            WorkoutRecordingController.ACTION_RESUME -> resumeSession()
            WorkoutRecordingController.ACTION_STOP -> stopSession()
            WorkoutRecordingController.ACTION_LOW_POWER -> {
                val lowPower = intent.getBooleanExtra(WorkoutRecordingController.EXTRA_LOW_POWER, false)
                if (lowPower != lowPowerMode) WearLog.i(TAG, "lowPowerMode -> $lowPower")
                lowPowerMode = lowPower
                if (session?.status == WorkoutStatus.RECORDING) {
                    ticker?.cancel()
                    startTicker()
                }
            }
            else -> {
                WearLog.i(TAG, "onStartCommand: restart (action=${intent?.action})")
                // Restarted by the system: no workout screen is attached until one says so.
                lowPowerMode = true
                restoreAfterRestart()
            }
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
            Sensor.TYPE_HEART_RATE -> onHeartRateEvent(event)
            Sensor.TYPE_STEP_COUNTER -> onStepCounter(event.values.firstOrNull(), sensorEventEpochMs(event))
            Sensor.TYPE_STEP_DETECTOR -> onStepDetected(sensorEventEpochMs(event))
        }
    }

    private fun onHeartRateEvent(event: SensorEvent) {
        val v = event.values.firstOrNull()?.toInt()
        // Every sample in debug builds (see WearLog); release builds log nothing per sample.
        WearLog.d(TAG, "HR event raw=$v accuracy=${event.accuracy}")
        heartRateLastEventAtMs = SystemClock.elapsedRealtime()
        // accuracy == SENSOR_STATUS_NO_CONTACT (2) or UNRELIABLE (0) with a 0 value is the
        // watch being off the wrist — the PPG has nothing to measure. A 0 is not a valid BPM
        // and publishHeartRate() discards it, so track the reason here for the UI.
        heartRateNoContact = v == 0 || event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT
        if (v != null && v != 0) heartRateNoContact = false
        publishHeartRate(v, sensorEventEpochMs(event))
    }

    /**
     * Wall-clock time the sample was measured. A non-wake-up sensor (the step detector) buffers
     * its samples while the SoC sleeps and delivers them late, so the delivery time would smear
     * the track.
     */
    private fun sensorEventEpochMs(event: SensorEvent): Long = WorkoutMath.sensorEventEpochMs(
        eventNanos = event.timestamp,
        nowEpochMs = System.currentTimeMillis(),
        nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
        notBeforeEpochMs = sensorsRegisteredAtEpochMs,
    )

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        WearLog.i(TAG, "onAccuracyChanged sensor=${sensor?.type} accuracy=$accuracy")
        if (sensor?.type == Sensor.TYPE_HEART_RATE) {
            // SENSOR_STATUS_NO_CONTACT = 2: the watch is off the wrist.
            heartRateNoContact = accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT
        }
    }

    private fun beginSession(activityTypeName: String?) {
        WearLog.i(TAG, "beginSession type=$activityTypeName")
        val now = System.currentTimeMillis()
        errorMessage = null
        metrics = WorkoutMetricsAccumulator()
        heartRateEverReceived = false
        heartRateRecoveryAttempts = 0
        heartRateSensorArmedAtMs = 0L
        heartRateLastEventAtMs = 0L
        heartRateAtEpochMs = null
        heartRateNoContact = false
        val fresh = WorkoutSessionTransitions.start(
            now,
            WorkoutActivityType.fromStorage(activityTypeName),
            SystemClock.elapsedRealtime(),
        )
        session = fresh
        sessionStore.save(fresh)
        requestWorkoutTileUpdate(this)
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
        WearLog.i(TAG, "pauseSession")
        val now = System.currentTimeMillis()
        session = WorkoutSessionTransitions.pause(current, now) ?: return
        sessionStore.save(session!!)
        requestWorkoutTileUpdate(this)
        if (!appendBoundary(current.startedAtEpochMs, "PAUSE")) {
            stopSession()
            return
        }
        metrics.clearSegment()
        stopSensors()
        ticker?.cancel()
        publish()
        refreshNotification()
    }

    private fun resumeSession() {
        val current = session?.takeIf { it.status == WorkoutStatus.PAUSED } ?: return
        WearLog.i(TAG, "resumeSession")
        val now = System.currentTimeMillis()
        session = WorkoutSessionTransitions.resume(current, now) ?: return
        sessionStore.save(session!!)
        requestWorkoutTileUpdate(this)
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
        WearLog.i(TAG, "stopSession status=${current.status}")
        session = WorkoutSessionTransitions.stop(current, stoppedAt)
        val stoppedSession = session!!
        sessionStore.save(session!!)
        requestWorkoutTileUpdate(this)
        runCatching {
            trackStore.append(current.startedAtEpochMs, WorkoutTrackEvent(System.currentTimeMillis(), boundary = "STOP"))
        }
        stopSensors()
        ticker?.cancel()
        serviceScope.launch {
            if (queueStoppedWorkout(stoppedSession, stoppedAt)) playCompletionFeedback()
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
        if (isInterruptedByReboot(current)) {
            finishInterruptedSession(current)
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

    private fun isInterruptedByReboot(current: WorkoutSessionSnapshot): Boolean =
        current.isInterruptedByReboot(SystemClock.elapsedRealtime(), System.currentTimeMillis())

    /**
     * Saves a workout cut off by a reboot as if it had been stopped at its last recorded sample,
     * so the walk is uploaded instead of left behind as a RECORDING session nobody runs. The track
     * may end in zeros (the reboot lost writes not yet on disk); [WorkoutTrackStore.readEvents]
     * skips that tail as an undecodable line.
     */
    private fun finishInterruptedSession(interrupted: WorkoutSessionSnapshot) {
        val events = trackStore.readEvents(interrupted.startedAtEpochMs)
        val endedAt = events.maxOfOrNull { it.timeEpochMs } ?: interrupted.startedAtEpochMs
        WearLog.w(TAG, "finishInterruptedSession: workout ${interrupted.startedAtEpochMs} cut off by a reboot, ending it at $endedAt")
        runCatching {
            trackStore.append(interrupted.startedAtEpochMs, WorkoutTrackEvent(endedAt, boundary = "STOP"))
        }
        val stopped = WorkoutSessionTransitions.stop(interrupted, endedAt)
        session = stopped
        sessionStore.save(stopped)
        requestWorkoutTileUpdate(this)
        serviceScope.launch {
            queueStoppedWorkout(stopped, endedAt)
            // A Start that arrived meanwhile has begun a new workout on this service; keep it.
            if (session?.status == WorkoutStatus.STOPPED) {
                publish()
                stopSelf()
            }
        }
    }

    private suspend fun queueStoppedWorkout(stoppedSession: WorkoutSessionSnapshot, stoppedAt: Long): Boolean {
        if (syncStore.get(stoppedSession.startedAtEpochMs) != null) return true
        val events = trackStore.readEvents(stoppedSession.startedAtEpochMs)
        val exported = runCatching {
            WorkoutExportWriter.write(filesDir.resolve(TRACK_DIRECTORY), stoppedSession, events, stoppedAt)
        }.getOrElse {
            publishError("Workout export could not be saved. Check watch storage.")
            return false
        }
        val exportedFit = runCatching {
            WorkoutFitWriter.write(filesDir.resolve(TRACK_DIRECTORY), stoppedSession, events, stoppedAt)
        }.getOrNull()
        val auth = WearAuthStore(this).state.first()
        syncStore.upsert(
            PendingWatchWorkout(
                sessionId = stoppedSession.startedAtEpochMs,
                gpxFileName = exported.gpxFile.name,
                sidecarFileName = exported.sidecarFile.name,
                fitFileName = exportedFit?.name,
                activityType = stoppedSession.activityType.name,
                visibility = UploadDefaultsSync.visibility(this),
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
    }

    private fun enterForeground(): Boolean {
        val foregroundTypes = foregroundServiceTypes()
        if (Build.VERSION.SDK_INT >= 34 && foregroundTypes == 0) {
            publishError("Grant heart-rate, activity, or location permission to start a workout.")
            session = null
            sessionStore.clear()
            requestWorkoutTileUpdate(this)
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
            requestWorkoutTileUpdate(this)
            stopSelf()
            false
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException after the user force-stopped the
            // app. Never crash the service mid-workout — abort cleanly with a retryable message.
            publishError("Could not enter workout foreground (${e.javaClass.simpleName}). Retry.")
            session = null
            sessionStore.clear()
            requestWorkoutTileUpdate(this)
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
        WearLog.d(TAG, "GPS fix acc=${location.accuracy} alt=${location.altitude} age=${System.currentTimeMillis() - location.time}ms")
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
        // Both step sources, when present. The counter is the authoritative cumulative total but
        // some watches report it rarely — the Xiaomi Watch 2's `pedometer_wrist` delivered a single
        // event across three test workouts — so on its own the live readout sat at 0. The detector
        // fires per step and keeps the readout live; the counter fills in steps the detector
        // dropped (e.g. while the SoC slept with the screen off).
        stepSensors = if (activityAllowed) {
            listOfNotNull(
                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
            )
        } else emptyList()
        // A fresh registration re-baselines the counter: steps walked while paused (sensors off)
        // must not be credited on the first callback after resume.
        lastRawStepCounter = null
        sensorsRegisteredAtEpochMs = System.currentTimeMillis()
        stepSensors.forEach { sensor ->
            val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            WearLog.i(TAG, "startPlatformSensors: registerListener ok=$ok sensor=${sensor.name} type=${sensor.type}")
        }
        sensorsActive = stepSensors.isNotEmpty()
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
            WearLog.w(TAG, "ensureHeartRateSensor: no HR permission, skipping")
            return
        }
        // One listener on one sensor. Registering every TYPE_HEART_RATE variant (wake-up and
        // non-wake-up are the same PPG) recorded each reading twice. Prefer the wake-up variant:
        // it keeps delivering while the screen is off and the SoC would otherwise sleep.
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (sensor == null) {
            WearLog.w(TAG, "ensureHeartRateSensor: no TYPE_HEART_RATE sensor present")
            return
        }
        for (s in sensorManager.getSensorList(Sensor.TYPE_HEART_RATE)) sensorManager.unregisterListener(this, s)
        val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        WearLog.i(TAG, "ensureHeartRateSensor: registerListener ok=$ok sensor=${sensor.name} isWakeUp=${sensor.isWakeUpSensor}")
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
        stepSensors = emptyList()
    }

    private fun onStepCounter(rawValue: Float?, timeEpochMs: Long) {
        val raw = rawValue?.takeIf { it >= 0f } ?: return
        val previous = lastRawStepCounter
        lastRawStepCounter = raw
        when {
            // First callback of this registration: a baseline, aligned to whatever the detector
            // has counted so far.
            previous == null -> counterSteps = metrics.steps
            raw > previous -> counterSteps += (raw - previous).toInt()
            // raw < previous: the counter was reset (reboot / HAL restart); re-baseline silently.
        }
        val newTotal = maxOf(metrics.steps, counterSteps)
        val current = session ?: return
        appendEvent(
            current,
            WorkoutTrackEvent(
                timeEpochMs = timeEpochMs,
                steps = newTotal,
                rawStepCounter = raw,
            ),
        )
    }

    private fun onStepDetected(timeEpochMs: Long) {
        val current = session ?: return
        appendEvent(
            current,
            WorkoutTrackEvent(timeEpochMs = timeEpochMs, steps = metrics.steps + 1),
        )
    }

    private fun publishHeartRate(value: Int?, measuredAtEpochMs: Long) {
        val bpm = value?.takeIf { it in MIN_HEART_RATE_BPM..MAX_HEART_RATE_BPM } ?: return
        val current = session?.takeIf { it.status == WorkoutStatus.RECORDING } ?: return
        heartRateEverReceived = true
        heartRateAtEpochMs = measuredAtEpochMs
        // A real reading means the listener works; let a later stall get the full re-arm budget.
        heartRateRecoveryAttempts = 0
        // First live reading clears the heart-rate blocker notice, so it does not linger over a
        // readout that is now working.
        if (errorMessage == HEART_RATE_PERMISSION_HINT) errorMessage = null
        appendEvent(current, WorkoutTrackEvent(timeEpochMs = measuredAtEpochMs, heartRateBpm = bpm))
    }

    private fun reportHeartRateBlockerIfNeeded() {
        if (hasHeartRatePermission()) return
        if (errorMessage != null) return
        errorMessage = HEART_RATE_PERMISSION_HINT
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
            // No publish per sample: HR, every step and every GPS fix each recomposed the whole
            // workout screen on the main thread several times a second. The ticker publishes once
            // a second while someone is watching, which is as often as the readout changes.
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
                delay(if (lowPowerMode) LOW_POWER_TICK_INTERVAL_MS else TICK_INTERVAL_MS)
            }
        }
    }

    /**
     * Re-arms the PPG listener only when it is genuinely starved: no heart-rate callback at all
     * (not even a 0 / no-contact one) for [WorkoutHeartRateWatchdog.STALL_MS] since it was armed or last spoke.
     *
     * The window must comfortably exceed the PPG's warm-up. On the Xiaomi Watch 2 the first
     * reading lands ~10 s after registration; the old 10 s watchdog re-armed right at that edge,
     * and every re-arm restarted the warm-up — so BPM only appeared after the attempts ran out
     * (~40 s), which looked like "switching apps fixes it". Covers mid-workout stalls too, not
     * just a missing first reading.
     */
    private fun watchdogHeartRate() {
        val rearm = WorkoutHeartRateWatchdog.shouldRearm(
            isRecording = session?.status == WorkoutStatus.RECORDING,
            hasPermission = hasHeartRatePermission(),
            armedAtMs = heartRateSensorArmedAtMs,
            lastEventAtMs = heartRateLastEventAtMs,
            attempts = heartRateRecoveryAttempts,
            nowMs = SystemClock.elapsedRealtime(),
        )
        if (!rearm) return
        heartRateRecoveryAttempts++
        WearLog.w(TAG, "HR watchdog firing: no HR callback for ${WorkoutHeartRateWatchdog.STALL_MS / 1000}s, attempt=$heartRateRecoveryAttempts — re-arming platform sensor")
        ensureHeartRateSensor()
    }

    private fun publish() {
        val current = session
        val now = System.currentTimeMillis()
        val movingMs = current?.movingMsAt(now) ?: 0L
        val snapshot =
            WorkoutRecordingSnapshot(
                session = current,
                elapsedMs = current?.liveElapsedMs(SystemClock.elapsedRealtime(), now) ?: 0L,
                movingMs = movingMs,
                heartRateBpm = metrics.heartRateBpm,
                heartRateAtEpochMs = heartRateAtEpochMs,
                distanceMeters = metrics.distanceMeters,
                paceSecondsPerKm = WorkoutMath.paceSecondsPerKm(movingMs, metrics.distanceMeters),
                steps = metrics.steps,
                availability = availability,
                errorMessage = errorMessage,
            )
        WorkoutRecordingBus.publish(snapshot)
    }

    private fun publishError(message: String) {
        WearLog.w(TAG, "publishError: $message")
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
            healthServices = false,
            heartRate = heartRateEverReceived || (hrSensorPresent && !hasExceededHeartRateRecovery()),
            steps = stepPresent,
            noContact = heartRateNoContact,
        )
        publish()
    }

    /** True once the watchdog has given up re-arming the PPG for this workout. */
    private fun hasExceededHeartRateRecovery(): Boolean =
        heartRateRecoveryAttempts >= WorkoutHeartRateWatchdog.MAX_ATTEMPTS

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
        private const val LOW_POWER_TICK_INTERVAL_MS = 60_000L
        private const val MIN_HEART_RATE_BPM = 20
        private const val MAX_HEART_RATE_BPM = 250

        /**
         * Shown when a workout is running but the body-sensors permission is absent — the workout
         * starts anyway on location alone, so without this the only symptom is a silent "--" BPM.
         */
        private const val HEART_RATE_PERMISSION_HINT =
            "Heart rate needs the Body sensors permission. Open Settings → Grant sensor access."
    }
}
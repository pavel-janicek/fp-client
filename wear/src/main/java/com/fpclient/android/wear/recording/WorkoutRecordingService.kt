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
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await

class WorkoutRecordingService : Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessionStore: WorkoutSessionStore
    private lateinit var trackStore: WorkoutTrackStore
    private lateinit var metrics: WorkoutMetricsAccumulator
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var session: WorkoutSessionSnapshot? = null
    private var availability = WorkoutSensorAvailability()
    private var stepSensor: Sensor? = null
    private var heartRateSensor: Sensor? = null
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
        metrics = WorkoutMetricsAccumulator()
        sensorManager = getSystemService(SensorManager::class.java)
        locationManager = getSystemService(LocationManager::class.java)
        createNotificationChannel()
        restoreSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WorkoutRecordingController.ACTION_START -> {
                if (session == null || session?.status == WorkoutStatus.STOPPED) beginSession()
                else promoteAndRestartSensors()
            }
            WorkoutRecordingController.ACTION_PAUSE -> pauseSession()
            WorkoutRecordingController.ACTION_RESUME -> resumeSession()
            WorkoutRecordingController.ACTION_STOP -> stopSession()
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
            Sensor.TYPE_HEART_RATE -> publishHeartRate(event.values.firstOrNull()?.toInt())
            Sensor.TYPE_STEP_COUNTER -> onStepCounter(event.values.firstOrNull())
            Sensor.TYPE_STEP_DETECTOR -> onStepDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun beginSession() {
        val now = System.currentTimeMillis()
        errorMessage = null
        metrics = WorkoutMetricsAccumulator()
        val fresh = WorkoutSessionTransitions.start(now)
        session = fresh
        sessionStore.save(fresh)
        if (!appendBoundary(fresh.startedAtEpochMs, "START")) {
            stopSession()
            return
        }
        publish()
        promoteAndRestartSensors()
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
    }

    private fun stopSession() {
        val current = session ?: run {
            stopSelf()
            return
        }
        session = WorkoutSessionTransitions.stop(current, System.currentTimeMillis())
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
        trackStore.readEvents(restored.startedAtEpochMs).forEach(metrics::add)
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
        if (current.status == WorkoutStatus.RECORDING) promoteAndRestartSensors()
        else stopSelf()
    }

    private fun promoteAndRestartSensors() {
        if (session?.status != WorkoutStatus.RECORDING) return
        if (!enterForeground()) return
        startTicker()
        refreshAvailability()
        startLocation()
        startPlatformSensors()
        serviceScope.launch { startHealthServices() }
    }

    private fun enterForeground(): Boolean {
        val foregroundTypes = foregroundServiceTypes()
        if (Build.VERSION.SDK_INT >= 34 && foregroundTypes == 0) {
            publishError("Grant heart-rate, activity, or location permission to start a workout.")
            stopSelf()
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundTypes)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            true
        } catch (_: SecurityException) {
            publishError("Workout permissions changed. Grant access and try again.")
            stopSelf()
            false
        }
    }

    private fun foregroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < 34) return 0
        var types = 0
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && gpsProviderAvailable()) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        val heartRatePermission = if (Build.VERSION.SDK_INT >= 36) {
            hasPermission(WorkoutRecordingController.READ_HEART_RATE_PERMISSION)
        } else {
            hasPermission(Manifest.permission.BODY_SENSORS)
        }
        if (heartRatePermission || hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
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
        refreshAvailability()
    }

    private fun startFallbackHeartRate() {
        if (heartRateSensor != null || !hasHeartRatePermission()) return
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return
        sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorsActive = true
        refreshAvailability()
    }

    private fun stopFallbackHeartRate() {
        heartRateSensor?.let { sensorManager.unregisterListener(this, it) }
        heartRateSensor = null
        sensorsActive = stepSensor != null
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
        appendEvent(current, WorkoutTrackEvent(timeEpochMs = System.currentTimeMillis(), heartRateBpm = bpm))
    }

    private suspend fun startHealthServices() {
        if (session?.status != WorkoutStatus.RECORDING) return
        if (!hasHeartRatePermission()) {
            healthServicesAvailable = false
            healthHeartRateAvailable = false
            startFallbackHeartRate()
            refreshAvailability()
            return
        }
        val client = runCatching { HealthServices.getClient(this).exerciseClient }.getOrNull() ?: run {
            healthServicesAvailable = false
            healthHeartRateAvailable = false
            startFallbackHeartRate()
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
                        startFallbackHeartRate()
                    }
            }
            return
        }
        try {
            val capabilities = client.getCapabilitiesAsync().await()
            if (ExerciseType.RUNNING !in capabilities.supportedExerciseTypes) {
                healthServicesAvailable = false
                healthHeartRateAvailable = false
                startFallbackHeartRate()
                refreshAvailability()
                return
            }
            val running = capabilities.getExerciseTypeCapabilities(ExerciseType.RUNNING)
            if (DataType.HEART_RATE_BPM !in running.supportedDataTypes) {
                healthServicesAvailable = false
                healthHeartRateAvailable = false
                startFallbackHeartRate()
                refreshAvailability()
                return
            }

            val callback = object : ExerciseUpdateCallback {
                override fun onRegistered() = Unit

                override fun onRegistrationFailed(throwable: Throwable) {
                    healthExerciseActive = false
                    healthServicesAvailable = false
                    healthHeartRateAvailable = false
                    startFallbackHeartRate()
                    refreshAvailability()
                }

                override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                    val latestHeartRate = update.latestMetrics
                        .getData(DataType.HEART_RATE_BPM)
                        .lastOrNull()
                        ?.value
                        ?.toInt()
                    if (latestHeartRate != null) publishHeartRate(latestHeartRate)
                    if (update.exerciseStateInfo.state.isEnded && session?.status == WorkoutStatus.RECORDING) {
                        stopSession()
                    }
                }

                override fun onLapSummaryReceived(lapSummary: androidx.health.services.client.data.ExerciseLapSummary) = Unit

                override fun onAvailabilityChanged(
                    dataType: androidx.health.services.client.data.DataType<*, *>,
                    availability: androidx.health.services.client.data.Availability,
                ) {
                    if (dataType == DataType.HEART_RATE_BPM && availability is DataTypeAvailability) {
                        healthHeartRateAvailable = availability == DataTypeAvailability.AVAILABLE ||
                            availability == DataTypeAvailability.ACQUIRING
                        if (healthHeartRateAvailable) stopFallbackHeartRate() else startFallbackHeartRate()
                        refreshAvailability()
                    }
                }
            }

            client.setUpdateCallback(callback)
            val existingExercise = runCatching { client.getCurrentExerciseInfoAsync().await() }.getOrNull()
            if (existingExercise?.exerciseType == null || existingExercise.exerciseType == ExerciseType.UNKNOWN) {
                client.startExerciseAsync(
                    ExerciseConfig(
                        exerciseType = ExerciseType.RUNNING,
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
                    startFallbackHeartRate()
                    refreshAvailability()
                    return
                }
            }
            healthCallback = callback
            healthExerciseActive = true
            healthExercisePaused = false
            healthServicesAvailable = true
            healthHeartRateAvailable = true
            refreshAvailability()
        } catch (_: Exception) {
            healthCallback = null
            healthExerciseActive = false
            healthServicesAvailable = false
            healthHeartRateAvailable = false
            startFallbackHeartRate()
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
                    startFallbackHeartRate()
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
            publish()
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
                refreshNotification()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun publish() {
        val current = session
        val now = System.currentTimeMillis()
        val movingMs = current?.movingMsAt(now) ?: 0L
        WorkoutRecordingBus.publish(
            WorkoutRecordingSnapshot(
                session = current,
                elapsedMs = current?.elapsedMsAt(now) ?: 0L,
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
            heartRate = (healthHeartRateAvailable && healthServicesAvailable) || hrSensorPresent,
            steps = stepPresent,
        )
        publish()
    }

    private fun hasHeartRatePermission(): Boolean = if (Build.VERSION.SDK_INT >= 36) {
        hasPermission(WorkoutRecordingController.READ_HEART_RATE_PERMISSION)
    } else {
        hasPermission(Manifest.permission.BODY_SENSORS)
    }

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
        val elapsedSeconds = current?.elapsedMsAt(System.currentTimeMillis())?.div(1000L) ?: 0L
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText("${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')} elapsed")
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(actionIcon, actionLabel, servicePendingIntent(action, action.hashCode()))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", servicePendingIntent(WorkoutRecordingController.ACTION_STOP, 2))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int) = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, WorkoutRecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "fitpub_workout_recording"
        private const val NOTIFICATION_ID = 9301
        private const val TRACK_DIRECTORY = "workouts"
        private const val LOCATION_INTERVAL_MS = 1_000L
        private const val LOCATION_MIN_DISTANCE_METERS = 1f
        private const val TICK_INTERVAL_MS = 1_000L
        private const val MIN_HEART_RATE_BPM = 20
        private const val MAX_HEART_RATE_BPM = 250
    }
}
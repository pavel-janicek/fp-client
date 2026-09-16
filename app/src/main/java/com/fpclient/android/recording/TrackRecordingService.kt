package com.fpclient.android.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fpclient.android.FitPubApplication
import com.fpclient.android.MainActivity
import com.fpclient.android.R
import com.fpclient.android.util.Format
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service for on-device track recording (Iterations 8a + 8b).
 * Declared in the manifest with foregroundServiceType="location". Owns the session
 * lifecycle (start/pause/resume/stop), the ongoing notification (elapsed time +
 * pause/resume/stop actions), the GPS engine (framework LocationManager — the project
 * deliberately avoids Google Play services), incremental track-point persistence, and
 * the shared state bus the UI observes.
 *
 * GPS engine (8b): requestLocationUpdates at 2 s / 2 m; fixes with accuracy worse than
 * [TrackMath.MAX_ACCURACY_M] are discarded. Every accepted fix is appended to an
 * append-only JSONL file ([TrackPointStore]) and folded into [TrackStats] published on
 * the bus. While paused, GPS updates are detached entirely.
 *
 * Survivability contract:
 *  - Returns START_STICKY; when the OS restarts it after process death the intent is
 *    null and the session is restored from [TrackRecordingStateStore] into exactly the
 *    state it had (recording keeps its elapsed time, because it is wall-clock derived).
 *  - Track points survive process death by construction (append-only file, flushed per
 *    fix). Stats are rebuilt on restore by replaying that file — the file is the single
 *    source of truth, nothing is recomputed from process memory.
 */
class TrackRecordingService : LifecycleService() {

    private lateinit var store: TrackRecordingStateStore
    private lateinit var pointStore: TrackPointStore
    private var snapshot: TrackSessionSnapshot? = null
    private var ticker: Job? = null
    private var statsAccumulator = TrackStatsAccumulator()
    private var locationManager: LocationManager? = null
    private var gpsActive = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onFix(location)
        }

        // Empty overrides required pre-API 30 (the interface has default implementations
        // only from API 30; minSdk is 26, so they must exist explicitly).
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        store = TrackRecordingStateStore(this)
        pointStore = TrackPointStore(File(filesDir, RecordingShareManager.TRACKS_DIR))
        locationManager = getSystemService(LocationManager::class.java)
        createNotificationChannel()
        // Restore before anything else so UI launched later observes the right state
        // instead of a transient "idle".
        store.load()?.let { restored ->
            snapshot = restored
            TrackRecordingBus.publish(restored)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            TrackRecordingController.ACTION_START ->
                if (snapshot == null) {
                    // The chosen activity type rides on the start intent. A second start
                    // while a session runs is a no-op — just re-promote the foreground.
                    beginSession(intent.getStringExtra(TrackRecordingController.EXTRA_ACTIVITY_TYPE))
                } else enterForeground()
            TrackRecordingController.ACTION_PAUSE ->
                snapshot?.takeIf { it.state == RecordingState.RECORDING }?.let { pause(it) }
            TrackRecordingController.ACTION_RESUME ->
                snapshot?.takeIf { it.state == RecordingState.PAUSED }?.let { resume(it) }
            TrackRecordingController.ACTION_STOP -> stopSession()
            // START_STICKY restart after the OS killed the process: intent is null.
            else -> restoreAfterProcessDeath()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Last-chance persist: whatever state we die in must be restorable.
        snapshot?.let { store.save(it) }
        stopGps()
        pointStore.closeWriter()
        super.onDestroy()
    }

    private fun beginSession(chosenActivityType: String?) {
        TrackRecordingBus.publishStorageError(null)
        val now = System.currentTimeMillis()
        val fresh = TrackSessionSnapshot(
            state = RecordingState.RECORDING,
            startedAtEpochMs = now,
            accumulatedMs = 0L,
            lastResumeAtEpochMs = now,
            activityType = chosenActivityType?.takeIf { it.isNotBlank() }
                ?: TrackSessionSnapshot.DEFAULT_ACTIVITY_TYPE,
        )
        snapshot = fresh
        statsAccumulator = TrackStatsAccumulator()
        TrackRecordingBus.publish(fresh)
        TrackRecordingBus.publishStats(TrackStats())
        // A new session supersedes the previous workout's summary (Iteration 8d); its
        // files stay on disk and remain reachable through the pending-upload list.
        TrackRecordingBus.clearFinished()
        store.save(fresh)
        enterForeground()
        if (snapshot == null || pauseForLowStorage()) return
        startGps(fresh.startedAtEpochMs)
        startTicker()
    }

    private fun pause(current: TrackSessionSnapshot) {
        val now = System.currentTimeMillis()
        val paused = current.copy(
            state = RecordingState.PAUSED,
            accumulatedMs = current.movingMsAt(now),
            lastResumeAtEpochMs = null,
        )
        snapshot = paused
        TrackRecordingBus.publish(paused)
        store.save(paused)
        // Segment boundary for the GPX export (Iteration 8d): each pause/resume pair
        // becomes a separate <trkseg>, so the exported track shows where the workout
        // stopped instead of joining the gap with a straight line.
        try {
            pointStore.appendMarker(current.startedAtEpochMs, TrackPointStore.MARKER_PAUSE)
        } catch (_: java.io.IOException) {
            TrackRecordingBus.publishStorageError("Track storage failed. Recording paused; free space before resuming.")
        } catch (_: SecurityException) {
            TrackRecordingBus.publishStorageError("Track storage is inaccessible. Recording paused.")
        } finally {
            pointStore.closeWriter()
        }
        stopGps() // no fixes are accepted while paused; save the radio
        stopTicker()
        enterForeground() // refresh notification: paused title + Resume action
    }

    /** Returns true when recording must not continue; keeps already saved fixes intact. */
    private fun pauseForLowStorage(): Boolean {
        if (RecordingHealth.hasStorage(filesDir.usableSpace)) return false
        snapshot?.takeIf { it.state == RecordingState.RECORDING }?.let { pause(it) }
        TrackRecordingBus.publishStorageError(
            "Storage is low. Recording paused; free at least 10 MiB before resuming.",
        )
        return true
    }


    private fun resume(current: TrackSessionSnapshot) {
        val now = System.currentTimeMillis()
        val resumed = RecordingHealth.resumeIfWritable(current, now, filesDir.usableSpace) {
            pointStore.appendMarker(current.startedAtEpochMs, TrackPointStore.MARKER_RESUME)
        }
        if (resumed == null) {
            pointStore.closeWriter()
            TrackRecordingBus.publishStorageError("Cannot resume: free storage and try again. Your recording is still paused.")
            enterForeground()
            return
        }
        TrackRecordingBus.publishStorageError(null)
        snapshot = resumed
        TrackRecordingBus.publish(resumed)
        store.save(resumed)
        enterForeground()
        startGps(resumed.startedAtEpochMs)
        startTicker()
    }

    private fun stopSession() {
        val current = snapshot
        stopTicker()
        stopGps()
        pointStore.closeWriter()
        // The track file is deliberately KEPT: Iteration 8d turns it into the GPX export
        // and the post-workout summary. Only the active-session marker is cleared.
        snapshot = null
        TrackRecordingBus.publish(null)
        store.clear()
        // Stop is the moment Iteration 8d takes over: publish the finished session for the
        // summary screen (stats + every fix) and register it as a pending upload with its
        // GPX 1.1 export already written to app-private storage — so the workout stays
        // reachable even if the process dies before the summary is used. The export is a
        // single small file write, done here (not in a coroutine that stopSelf would
        // cancel) so the entry is guaranteed to exist.
        if (current != null) finishSession(current)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Publishes the just-stopped session and registers its GPX as a pending upload. */
    private fun finishSession(current: TrackSessionSnapshot) {
        val points = try {
            pointStore.readAll(current.startedAtEpochMs)
        } catch (_: Exception) {
            // A partially unreadable file must not lose the summary; whatever decoded is
            // shown, and the pending entry is only created when an export succeeded.
            emptyList()
        }
        val accumulator = TrackStatsAccumulator()
        points.forEach { accumulator.add(it) }
        TrackRecordingBus.publishFinished(
            FinishedRecording(
                snapshot = current,
                stats = accumulator.stats,
                points = points,
                endedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        try {
            FitPubApplication.container(this).recordingShareManager.registerPending(current)
        } catch (_: Exception) {
            // Registration is best-effort: the summary's share button registers the entry
            // again on a failed upload.
        }
    }


    /** START_STICKY restart with a null intent: put the persisted session back on screen. */
    private fun restoreAfterProcessDeath() {
        val restored = snapshot ?: store.load()
        if (restored == null) {
            // Spurious restart without a session — we are not recording, so do not hold
            // a foreground slot (or the notification) for nothing.
            stopSelf()
            return
        }
        snapshot = restored
        TrackRecordingBus.publish(restored)
        replayStatsFromDisk(restored.startedAtEpochMs)
        enterForeground()
        if (snapshot != null && restored.state == RecordingState.RECORDING && !pauseForLowStorage()) {
            startGps(restored.startedAtEpochMs)
            startTicker()
        }
    }

    /**
     * Rebuilds the live stats by replaying the persisted track file. The file is the
     * single source of truth for distance/elevation/count — this makes a process-death
     * restore exactly consistent no matter what the dying process had in memory.
     */
    private fun replayStatsFromDisk(startedAtEpochMs: Long) {
        statsAccumulator = TrackStatsAccumulator()
        val restoredPoints = try {
            pointStore.readAll(startedAtEpochMs)
        } catch (_: Exception) {
            // A partially unreadable file must not take the service down; the stats then
            // simply continue from whatever decoded cleanly.
            emptyList()
        }
        restoredPoints.forEach { statsAccumulator.add(it) }
        TrackRecordingBus.publishStats(statsAccumulator.stats)
        // The mini-map polyline is rebuilt too, so a restored session shows its full track.
        TrackRecordingBus.publishPoints(restoredPoints)
    }

    // ------------------------------------------------------------------ GPS engine

    private fun startGps(startedAtEpochMs: Long) {
        val lm = locationManager ?: return
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return // the UI permission gate prevents this in practice; fail soft here
        }
        if (gpsActive) return
        try {
            // PLAN 8b: ~1-3 s interval, ~2 m min distance.
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_INTERVAL_MS,
                GPS_MIN_DISTANCE_M,
                locationListener,
                mainLooper,
            )
            gpsActive = true
        } catch (_: SecurityException) {
            // Permission revoked mid-session; the notification flow keeps running.
        } catch (_: IllegalArgumentException) {
            // GPS provider absent (unlikely on phones); fail soft.
        }
    }

    private fun stopGps() {
        if (!gpsActive) return
        locationManager?.removeUpdates(locationListener)
        gpsActive = false
    }

    /** One GPS fix arrived: filter, persist, fold into the live stats. Main thread. */
    private fun onFix(location: Location) {
        val current = snapshot ?: return
        if (current.state != RecordingState.RECORDING) return
        if (pauseForLowStorage()) return
        if (!location.hasAccuracy() || !TrackMath.isAcceptableAccuracy(location.accuracy.toDouble())) return

        val point = TrackPoint(
            lat = location.latitude,
            lon = location.longitude,
            ele = if (location.hasAltitude()) location.altitude else 0.0,
            time = if (location.time > 0) location.time else System.currentTimeMillis(),
            accuracy = location.accuracy.toDouble(),
        )
        try {
            pointStore.append(current.startedAtEpochMs, point)
        } catch (_: Exception) {
            TrackRecordingBus.publishStorageError("Track write failed. Recording paused; free storage before resuming.")
            pause(current)
            return
        }
        statsAccumulator.add(point)
        TrackRecordingBus.publishStats(statsAccumulator.stats)
        // The live polyline + position dot on the Record screen's mini-map.
        TrackRecordingBus.publishPoint(point)
    }

    /**
     * Promotes the service to the foreground with the location type. On API 34+ a
     * location-typed startForeground throws if the location permission was revoked while
     * the process was dead — degrade to stopping instead of crashing.
     */
    private fun enterForeground() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (_: SecurityException) {
            stopSession()
        }
    }



    private fun buildNotification(): Notification {
        val s = snapshot
        val elapsed = s?.elapsedAt(System.currentTimeMillis()) ?: 0L
        val title = when (s?.state) {
            RecordingState.PAUSED -> "Recording paused"
            else -> "Recording track"
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun action(name: String, label: String, icon: Int) = NotificationCompat.Action.Builder(
            icon,
            label,
            PendingIntent.getService(
                this,
                name.hashCode(),
                Intent(this, TrackRecordingService::class.java).setAction(name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_record)
            .setContentTitle(title)
            .setContentText("Elapsed ${Format.duration(elapsed / 1000)}")
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        when (s?.state) {
            RecordingState.RECORDING ->
                builder.addAction(action(TrackRecordingController.ACTION_PAUSE, "Pause", android.R.drawable.ic_media_pause))
            RecordingState.PAUSED ->
                builder.addAction(action(TrackRecordingController.ACTION_RESUME, "Resume", android.R.drawable.ic_media_play))
            else -> Unit
        }
        builder.addAction(action(TrackRecordingController.ACTION_STOP, "Stop", android.R.drawable.ic_menu_close_clear_cancel))
        return builder.build()
    }

    /** Refreshes the notification every second with the current elapsed time. */
    private fun startTicker() {
        stopTicker()
        ticker = lifecycleScope.launch {
            var ticksSincePersist = 0
            while (isActive) {
                delay(1_000)
                if (pauseForLowStorage()) break
                if (canNotify()) {
                    NotificationManagerCompat.from(this@TrackRecordingService)
                        .notify(NOTIFICATION_ID, buildNotification())
                }
                // Bound the loss on process death: the wall-clock math means elapsed time
                // itself survives, but keep the store fresh for the moving segment.
                if (++ticksSincePersist >= PERSIST_EVERY_TICKS) {
                    ticksSincePersist = 0
                    snapshot?.let { store.save(it) }
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Track recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing status while a GPS track is being recorded"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 4001
        private const val PERSIST_EVERY_TICKS = 30

        /** GPS request cadence — PLAN 8b: ~1-3 s interval, ~2 m min distance. */
        private const val GPS_INTERVAL_MS = 2_000L
        private const val GPS_MIN_DISTANCE_M = 2f
    }
}

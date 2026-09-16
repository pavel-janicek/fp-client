package com.fpclient.android.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fpclient.android.MainActivity
import com.fpclient.android.R
import com.fpclient.android.util.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service skeleton for on-device track recording (Iteration 8a groundwork).
 * Declared in the manifest with foregroundServiceType="location". The GPS engine lands in
 * Iteration 8b; this class already owns the full session lifecycle (start/pause/resume/
 * stop), the ongoing notification (elapsed time + pause/resume/stop actions), persistence
 * for process death, and the shared state bus the UI observes.
 *
 * Survivability contract:
 *  - Returns START_STICKY; when the OS restarts it after process death the intent is
 *    null and the session is restored from [TrackRecordingStateStore] into exactly the
 *    state it had (recording keeps its elapsed time, because it is wall-clock derived).
 *  - The snapshot is persisted on every state transition and periodically while recording,
 *    bounding what a sudden process death can lose.
 */
class TrackRecordingService : LifecycleService() {

    private lateinit var store: TrackRecordingStateStore
    private var snapshot: TrackSessionSnapshot? = null
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        store = TrackRecordingStateStore(this)
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
                if (snapshot == null) beginSession() else enterForeground()
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
        super.onDestroy()
    }

    private fun beginSession() {
        val now = System.currentTimeMillis()
        val fresh = TrackSessionSnapshot(
            state = RecordingState.RECORDING,
            startedAtEpochMs = now,
            accumulatedMs = 0L,
            lastResumeAtEpochMs = now,
        )
        snapshot = fresh
        TrackRecordingBus.publish(fresh)
        store.save(fresh)
        enterForeground()
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
        stopTicker()
        enterForeground() // refresh notification: paused title + Resume action
    }

    private fun resume(current: TrackSessionSnapshot) {
        val now = System.currentTimeMillis()
        val resumed = current.copy(
            state = RecordingState.RECORDING,
            lastResumeAtEpochMs = now,
        )
        snapshot = resumed
        TrackRecordingBus.publish(resumed)
        store.save(resumed)
        enterForeground()
        startTicker()
    }

    private fun stopSession() {
        stopTicker()
        snapshot = null
        TrackRecordingBus.publish(null)
        store.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        enterForeground()
        if (restored.state == RecordingState.RECORDING) startTicker()
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
    }
}

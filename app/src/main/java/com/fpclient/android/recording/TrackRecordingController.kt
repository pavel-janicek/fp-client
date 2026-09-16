package com.fpclient.android.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Thin start/pause/resume/stop helpers used by the UI. [start] goes through
 * startForegroundService() (the service must then call startForeground immediately, and
 * does); pause/resume/stop only ever target an already-running foreground service, where
 * plain startService() is allowed even with the app in the background — which is exactly
 * the situation when the actions are triggered from the ongoing notification.
 */
object TrackRecordingController {

    const val ACTION_START = "com.fpclient.android.recording.action.START"
    const val ACTION_PAUSE = "com.fpclient.android.recording.action.PAUSE"
    const val ACTION_RESUME = "com.fpclient.android.recording.action.RESUME"
    const val ACTION_STOP = "com.fpclient.android.recording.action.STOP"

    /** Intent extra carrying the activity type chosen on the Record pre-start screen. */
    const val EXTRA_ACTIVITY_TYPE = "com.fpclient.android.recording.extra.ACTIVITY_TYPE"

    /** True while a session exists (recording or paused). */
    fun isActive(): Boolean = TrackRecordingBus.session.value != null

    fun start(context: Context, activityType: String = TrackSessionSnapshot.DEFAULT_ACTIVITY_TYPE) {
        ContextCompat.startForegroundService(
            context,
            intent(context, ACTION_START).putExtra(EXTRA_ACTIVITY_TYPE, activityType),
        )
    }

    fun pause(context: Context) {
        context.startService(intent(context, ACTION_PAUSE))
    }

    fun resume(context: Context) {
        context.startService(intent(context, ACTION_RESUME))
    }

    fun stop(context: Context) {
        context.startService(intent(context, ACTION_STOP))
    }

    private fun intent(context: Context, action: String): Intent =
        Intent(context, TrackRecordingService::class.java).setAction(action)
}

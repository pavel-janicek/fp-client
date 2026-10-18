package com.fpclient.android.wear.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.fpclient.android.wear.WearLog

/**
 * After a reboot, hands a workout that was still recording to [WorkoutRecordingService], which
 * saves and uploads it. Without this the workout stayed "recording" with no service behind it
 * until the next Start resumed it. Also checked after an app update, so updating releases a
 * workout that an earlier reboot already left stuck.
 */
class WorkoutRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val session = WorkoutSessionStore(context).load() ?: return
        if (!session.isInterruptedByReboot(SystemClock.elapsedRealtime(), System.currentTimeMillis())) return
        WearLog.i(WorkoutRecordingService.TAG, "WorkoutRebootReceiver: ${intent.action}, recovering workout ${session.startedAtEpochMs}")
        runCatching { WorkoutRecordingController.recoverAfterReboot(context) }
            .onFailure { WearLog.w(WorkoutRecordingService.TAG, "WorkoutRebootReceiver: service start failed", it) }
    }
}

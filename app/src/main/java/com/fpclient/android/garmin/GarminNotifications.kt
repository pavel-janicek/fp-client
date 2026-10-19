package com.fpclient.android.garmin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fpclient.android.R
import com.fpclient.android.notifications.PushNotifications

/**
 * "Workout received from your Garmin" — in 10b the visible proof that background delivery works
 * on a real watch. A channel of its own, so it can be muted without muting FitPub activity.
 */
internal object GarminNotifications {
    private const val CHANNEL_ID = "fitpub_garmin"
    private const val NOTIFICATION_ID = 5301

    fun postReceived(context: Context, entry: GarminInboxEntry) {
        if (!PushNotifications.canPost(context)) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Garmin watch", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Workouts received from the FP Wear app on a Garmin watch"
            },
        )
        val text = "${entry.label.ifBlank { "Workout" }} from ${entry.deviceName}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Garmin workout received")
            .setContentText(text)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and the notify() call.
        }
    }
}

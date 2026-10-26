package com.fpclient.android.garmin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fpclient.android.R
import com.fpclient.android.notifications.PushNotifications

/**
 * "Garmin activity imported to FitPub" after a Garmin Connect import (PLAN "Garmin Connect API").
 * A channel of its own, so it can be muted without muting FitPub activity.
 */
internal object GarminNotifications {
    private const val CHANNEL_ID = "fitpub_garmin"
    private const val NOTIFICATION_ID = 5301

    fun postImported(context: Context, text: String) {
        if (!PushNotifications.canPost(context)) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Garmin", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Activities imported from Garmin Connect"
            },
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Garmin activity imported to FitPub")
            .setContentText(text)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and the notify() call.
        }
    }
}

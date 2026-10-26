package com.fpclient.android.garmin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fpclient.android.MainActivity
import com.fpclient.android.R
import com.fpclient.android.notifications.PushNotifications

/**
 * "Garmin activity imported to FitPub" after a Garmin Connect import (PLAN "Garmin Connect API").
 * A channel of its own, so it can be muted without muting FitPub activity.
 *
 * A tap opens the imported activity (PLAN 4.2.0), where its title, description and visibility
 * can be changed. Each activity gets its own notification, tagged with its id, so two imports in
 * one sync each open their own detail.
 */
internal object GarminNotifications {
    private const val CHANNEL_ID = "fitpub_garmin"
    private const val NOTIFICATION_ID = 5301

    fun postImported(context: Context, text: String, activityId: String) {
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
        if (activityId.isNotBlank()) builder.setContentIntent(openActivityIntent(context, activityId))
        try {
            NotificationManagerCompat.from(context).notify(activityId.ifBlank { null }, NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and the notify() call.
        }
    }

    /**
     * Opens `/activities/<id>` on top of the main screen, the same path a mailbox push carries
     * (MainActivity handles [PushNotifications.EXTRA_OPEN_PATH]). The request code is per
     * activity: with one shared code, FLAG_UPDATE_CURRENT would point every earlier
     * notification at the newest activity.
     */
    private fun openActivityIntent(context: Context, activityId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(PushNotifications.EXTRA_OPEN_PATH, "/activities/$activityId")
        return PendingIntent.getActivity(
            context,
            activityId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

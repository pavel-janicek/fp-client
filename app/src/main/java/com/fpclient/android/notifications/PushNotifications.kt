package com.fpclient.android.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fpclient.android.MainActivity
import com.fpclient.android.R
import com.fpclient.android.data.dto.PushPayloadDto
import com.fpclient.android.ui.navigation.Routes

/**
 * Android side of the background notification poll (Iteration 8f): the notification channel,
 * the local notification itself, and the runtime-permission check.
 *
 * Everything user-visible goes through one channel, [CHANNEL_ID], so the OS-level toggle in
 * Settings → Apps → FP Client → Notifications ("FitPub activity") controls exactly this
 * feature and nothing else — the ongoing track-recording notification stays on its own
 * `track_recording` channel.
 */
object PushNotifications {

    /** All user-visible state of the poll goes through this one channel (its own OS toggle). */
    const val CHANNEL_ID = "fitpub_push"

    /** Extra that makes the tapped notification open the Activity tab. */
    const val EXTRA_OPEN_TAB = "com.fpclient.android.extra.OPEN_TAB"

    /**
     * Extra carrying the server-relative path from a mailbox push payload (Iteration 8h),
     * e.g. `/activities/<id>` — MainActivity opens it on top of the main screen.
     */
    const val EXTRA_OPEN_PATH = "com.fpclient.android.extra.OPEN_PATH"

    /** One summary notification at a time: each poll replaces the previous one. */
    const val NOTIFICATION_ID = 5101

    /** Fallback collapse tag when a payload arrives without one. */
    private const val DEFAULT_TAG = "fitpub-push"

    /**
     * Base for per-tag notification ids so a payload tag (`fitpub-activity_shared`, …) can
     * never collide with the 8f summary's [NOTIFICATION_ID]; the high bit keeps every id in
     * its own range while staying stable per tag (same tag → same id → OS replaces it).
     */
    private const val TAG_ID_BASE = 1 shl 30

    /** Creates the channel (idempotent — safe to call on every poll and at app start). */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FitPub activity",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reactions, comments, shares and new followers from your FitPub instance"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Whether a local notification may be posted. From API 33 this is the
     * POST_NOTIFICATIONS runtime permission; on API 32 and below there is nothing to ask for
     * (the channel toggle is the only user control).
     */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posts (or replaces) the summary notification for a poll result. */
    fun post(context: Context, content: PushContent) {
        if (!canPost(context)) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(openAppIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and the notify() call.
        }
    }

    /** Clears the summary notification (e.g. after the user opens the notifications tab). */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Posts one decrypted mailbox push (Iteration 8h). The payload's `tag` is used both as the
     * Android collapse tag and to derive a stable notification id, so repeated pushes of the
     * same kind (e.g. `fitpub-activity_liked`) replace each other instead of stacking; the
     * payload's `url` is handed to the tap intent so the activity behind it opens directly.
     */
    fun postItem(context: Context, item: PushPayloadDto) {
        if (!canPost(context)) return
        ensureChannel(context)
        val tag = item.tag?.takeIf { it.isNotBlank() } ?: DEFAULT_TAG
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
            .setContentIntent(openAppIntent(context, item.url))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
        try {
            NotificationManagerCompat.from(context).notify(tag, tagId(tag), builder.build())
        } catch (_: SecurityException) {
            // The permission was revoked between the check above and the notify() call.
        }
    }

    /** Stable per-tag id in its own range (see [TAG_ID_BASE]). */
    private fun tagId(tag: String): Int = TAG_ID_BASE + (tag.hashCode() and 0x3fffffff)

    /**
     * Tapping the notification deep-links to the notifications tab: the list is where the
     * summary came from, and opening it also clears the summary (see `NotificationsTabContent`).
     * [path], when present, additionally opens the payload's target (e.g. the activity).
     */
    private fun openAppIntent(context: Context, path: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_TAB, Routes.BottomTab.NOTIFICATIONS.name)
        if (!path.isNullOrBlank()) intent.putExtra(EXTRA_OPEN_PATH, path)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

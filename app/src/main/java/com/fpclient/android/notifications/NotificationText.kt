package com.fpclient.android.notifications

import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.dto.NotificationTypes

/**
 * Single source of truth for how a notification is phrased in words.
 *
 * It is shared by the in-app notifications list (`NotificationsTab`) and the background push
 * worker (Iteration 8f), so a system notification always reads exactly like the row it was
 * derived from — the push feature adds delivery, never a second vocabulary.
 *
 * Android-free on purpose: the same wording is unit-tested on the JVM.
 */
object NotificationText {

    /** Fallback actor for rows the server sent without an actor identity (federation gaps). */
    const val UNKNOWN_ACTOR = "Someone"

    /** The actor's display name, falling back to the handle, then to [UNKNOWN_ACTOR]. */
    fun actorLabel(notification: NotificationDto): String =
        notification.actorDisplayName ?: notification.actorUsername ?: UNKNOWN_ACTOR

    /** The one-line description shown in the list row and in the push notification. */
    fun describe(notification: NotificationDto): String {
        val actor = actorLabel(notification)
        return when (notification.type) {
            NotificationTypes.ACTIVITY_LIKED ->
                "$actor reacted ${notification.reactionEmoji ?: "❤️"} to your activity"
            NotificationTypes.COMMENT_ADDED, NotificationTypes.ACTIVITY_COMMENTED ->
                "$actor commented: \"${notification.commentText ?: ""}\""
            NotificationTypes.ACTIVITY_SHARED -> "$actor shared your activity"
            NotificationTypes.USER_FOLLOWED -> "$actor started following you"
            NotificationTypes.FOLLOW_REQUEST -> "$actor requested to follow you"
            NotificationTypes.FOLLOW_REQUEST_ACCEPTED -> "$actor accepted your follow request"
            else -> "$actor interacted with you"
        }
    }
}

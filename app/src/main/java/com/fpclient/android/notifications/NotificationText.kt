package com.fpclient.android.notifications

import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.dto.NotificationTypes
import com.fpclient.android.util.ActorHandle

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

    /**
     * The actor's full `@username@host` handle, which is what every *identity* in this app is
     * keyed by (see `ActorHandle`).
     *
     * A notification row is one of the few places where the bare `actorUsername` is still
     * around, and using it here was actively wrong: it is only the local part, so a follower
     * from `makni.cz` rendered as `@sam`, which reads as "a Sam on this instance" and — worse —
     * navigates to a local profile that does not exist, ending in "User not found".
     *
     * The server always sends `actorUri`, so the host is always recoverable; the display name
     * is still preferred for readability, with the handle behind it.
     */
    fun actorHandle(notification: NotificationDto): String? =
        ActorHandle.full(
            username = notification.actorUsername,
            actorUri = notification.actorUri,
        )

    /**
     * The actor's name for a notification line.
     *
     * Prefers the display name, and appends the full handle when — and only when — there is an
     * *instance* to name. "Alice started following you" hides the one fact federation exists for,
     * so a remote actor reads as `Alice (sam@makni.cz)`.
     *
     * The short form is kept where it carries the same information:
     *  - a **local** actor, where `sam@makni.cz` would name the reader's own instance;
     *  - a row with **no** `actorUri`, where there is no host to show at all and `Sam (sam)`
     *    would be noise. The server does send `actorUri` for every real notification; this is
     *    the degradation path for legacy rows and federation gaps;
     *  - a username that is already a full handle, which is passed through verbatim.
     */
    fun actorLabel(notification: NotificationDto): String {
        val display = notification.actorDisplayName?.takeIf { it.isNotBlank() }
        val username = notification.actorUsername?.trim()?.takeIf { it.isNotBlank() }
        val handle = actorHandle(notification)
        val hasInstance = handle != null && ActorHandle.isFullHandle(handle)
        val isLocal = notification.actorLocal == true
        return when {
            // No display name: show the identity. An existing handle is passed through, a remote
            // actor gains the instance the server named, and a local actor keeps the plain
            // `@name` — naming the reader's own instance on every row would be noise.
            display == null -> when {
                username?.contains('@') == true -> username
                isLocal -> "@" + username.orEmpty().removePrefix("@")
                hasInstance -> handle!!
                else -> username ?: UNKNOWN_ACTOR
            }
            // Display name plus a remote instance, so the reader can tell the two apart.
            hasInstance && !isLocal -> "$display (${handle!!.trimStart('@')})"
            else -> display
        }
    }

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

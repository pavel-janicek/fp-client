package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------

@Serializable
data class NotificationDto(
    val id: String? = null,
    val type: String? = null,
    val actorUri: String? = null,
    val actorDisplayName: String? = null,
    val actorUsername: String? = null,
    val actorAvatarUrl: String? = null,
    val activityId: String? = null,
    val activityTitle: String? = null,
    val commentId: String? = null,
    val commentText: String? = null,
    val reactionEmoji: String? = null,
    val targetUrl: String? = null,
    val read: Boolean = false,
    val createdAt: String? = null,
    val readAt: String? = null,
    val followingBack: Boolean? = null,
    val followBackAllowed: Boolean? = null,
    val followRequestPending: Boolean? = null,
    val actorLocal: Boolean? = null,
)

@Serializable
data class PageEnvelopeNotificationDto(
    val content: List<NotificationDto> = emptyList(),
    val page: PageMetaDto = PageMetaDto(),
)

@Serializable
data class UnreadCountDto(
    val count: Long = 0,
)

// Notification types (server enum values)
object NotificationTypes {
    const val ACTIVITY_LIKED = "ACTIVITY_LIKED"
    const val COMMENT_ADDED = "COMMENT_ADDED"
    const val USER_FOLLOWED = "USER_FOLLOWED"
    const val FOLLOW_REQUEST = "FOLLOW_REQUEST"
    const val FOLLOW_REQUEST_ACCEPTED = "FOLLOW_REQUEST_ACCEPTED"
    const val ACTIVITY_COMMENTED = "ACTIVITY_COMMENTED"
    /** Someone shared the user's activity; background delivery (Iteration 8f) announces it. */
    const val ACTIVITY_SHARED = "ACTIVITY_SHARED"
    const val ACTIVITY_MENTION = "ACTIVITY_MENTION"
    const val PRIVACY_ZONE_TRIGGERED = "PRIVACY_ZONE_TRIGGERED"
    const val SYSTEM_ANNOUNCEMENT = "SYSTEM_ANNOUNCEMENT"
    const val QUOTE_CREATED = "QUOTE_CREATED"
    const val QUOTE_APPROVAL_REQUIRED = "QUOTE_APPROVAL_REQUIRED"
    const val QUOTE_APPROVAL_RESOLVED = "QUOTE_APPROVAL_RESOLVED"
    const val ACTIVITY_FROM_FOLLOWING = "ACTIVITY_FROM_FOLLOWING"
}
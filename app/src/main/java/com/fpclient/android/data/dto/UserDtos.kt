package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

@Serializable
data class UserDto(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val bioHtml: String? = null,
    val profileVisibility: String? = null,
    val defaultTimeline: String? = null,
    val unitSystem: String? = null,
    val timezone: String? = null,
    val avatarUrl: String? = null,
    val hasUploadedAvatar: Boolean? = null,
    val profileHeaderUrl: String? = null,
    val hasUploadedProfileHeader: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val homeZoom: Int? = null,
    val defaultActivityPrivacyPreferences: JsonObject? = null,
    val followersCount: Long? = null,
    val followingCount: Long? = null,
    val activityCount: Long? = null,
    val isFollowing: Boolean? = null,
    // Federation-aware fields (present on follower/following lists and remote search hits).
    // The ActivityPub actor URI, e.g. https://fitpub.example/users/alice or
    // https://mastodon.social/users/bob.
    val actorUri: String? = null,
    /** The actor's server domain, e.g. `fitpub.example` or `mastodon.social`. */
    val domain: String? = null,
    /** Full handle `username@domain`; null for local-only responses. */
    val handle: String? = null,
) {
    /** Full `@username@host` handle so remote actors keep their home instance. */
    val fullHandle: String
        get() = com.fpclient.android.util.ActorHandle.full(username, actorUri, handle, domain) ?: "@${username.orEmpty()}"
}

/**
 * Lightweight actor profile returned by the server's `GET /api/web/users/discover-remote`
 * endpoint. Used when opening the profile of a federated (remote) user the server has
 * only discovered via WebFinger — these accounts don't have the full [UserDto] fields
 * (no activities/heatmap) but do carry enough to render a minimal profile card.
 */
@Serializable
data class ActorDto(
    val actorUri: String? = null,
    val username: String? = null,
    val domain: String? = null,
    val handle: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val bioHtml: String? = null,
    val local: Boolean = false,
    val instanceType: String? = null,
    /** Follow relationship status from the current viewer to this actor (from discover-remote). */
    val followStatus: String? = null,
) {
    /** Full `@username@host` handle, falling back to the wire fields. */
    val fullHandle: String
        get() = com.fpclient.android.util.ActorHandle.full(username, actorUri, handle, domain) ?: "@${username.orEmpty()}"
}

@Serializable
data class UserSearchResultDto(
    val content: List<UserDto> = emptyList(),
    val page: PageMetaDto = PageMetaDto(),
)

@Serializable
data class UserUpdateRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val profileVisibility: String? = null,
    val defaultTimeline: String? = null,
    val unitSystem: String? = null,
    val timezone: String? = null,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val homeZoom: Int? = null,
    val defaultActivityPrivacyPreferences: JsonObject? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class FollowStatusDto(
    val username: String? = null,
    // The server's GET /{username}/follow-status actually answers with
    // {"isFollowing": bool, "status": "NONE|PENDING|ACCEPTED|REJECTED"}.
    val isFollowing: Boolean = false,
    val status: String? = null,
    // Defensive fields for richer payloads (e.g. follower-list responses); the
    // follow-status endpoint does not send them, so never branch on these alone.
    val isFollowRequestPending: Boolean = false,
    val isFollowRequestReceived: Boolean = false,
    val isFollowingBack: Boolean = false,
    val canFollow: Boolean = false,
    val canUnfollow: Boolean = false,
    val isOwnProfile: Boolean = false,
) {
    /**
     * An accepted follow relationship. Server maps `isFollowing` to status ACCEPTED;
     * branch on this (never on [isFollowing] alone) so the `status` string is honored.
     */
    val isAccepted: Boolean
        get() = isFollowing || canUnfollow || status.equals("ACCEPTED", ignoreCase = true)

    /**
     * An outgoing follow request awaiting the target's approval (server status PENDING).
     * Cancelled through the same unfollow endpoint as an accepted follow.
     */
    val isPending: Boolean
        get() = isFollowRequestPending || status.equals("PENDING", ignoreCase = true)

    /** Incoming follow request sent to us (not part of the follow-status payload yet). */
    val isReceived: Boolean
        get() = isFollowRequestReceived
}

@Serializable
data class FollowResultDto(
    val following: Boolean = false,
)

@Serializable
data class PreviewDtosContainer(
    val user: UserDto? = null,
)

@Serializable
data class UserListPageDto(
    val content: List<UserDto> = emptyList(),
    val page: PageMetaDto = PageMetaDto(),
)

@Serializable
data class EmailChangeStatusResponse(
    val pending: Boolean = false,
    val newEmail: String? = null,
    val expiresAt: String? = null,
)

@Serializable
data class StartEmailChangeRequest(val newEmail: String)

@Serializable
data class VerifyEmailChangeRequest(val code: String)

@Serializable
data class PasswordResetRequest(val usernameOrEmail: String)

@Serializable
data class PasswordResetConfirmRequest(
    val token: String,
    val newPassword: String,
)

@Serializable
data class UsernameRecoveryRequest(val usernameOrEmail: String)

@Serializable
data class UsernameRecoveryRequestResponse(
    val challengeId: String? = null,
    val message: String? = null,
)

@Serializable
data class VerifyUsernameRecoveryRequest(val challengeId: String, val code: String)

@Serializable
data class UsernameRecoveryVerificationResponse(
    val redirectUrl: String? = null,
    val username: String? = null,
)

@Serializable
data class UsernameRecoveryRegistrationRequest(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String? = null,
    val bio: String? = null,
    val timezone: String? = null,
    val registrationPassword: String? = null,
    val turnstileToken: String? = null,
)

@Serializable
data class AccountDeletionRequest(val password: String)

// Keep the wire name the server actually produces for counts.
@Serializable
data class UserSearchEnvelope(
    val content: List<UserDto> = emptyList(),
)

// Names mirror server enums
object ProfileVisibilities {
    val ALL = listOf("PUBLIC", "LOCAL", "FOLLOWERS", "PRIVATE")
}

object UnitSystems {
    const val METRIC = "METRIC"
    const val IMPERIAL = "IMPERIAL"
    val ALL = listOf(METRIC, IMPERIAL)
}

object DefaultTimelines {
    const val FEDERATED = "FEDERATED"
    const val PUBLIC = "PUBLIC"
    const val USER = "USER"
    val ALL = listOf(FEDERATED, PUBLIC, USER)
}
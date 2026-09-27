package com.fpclient.android.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Common / shared
// ---------------------------------------------------------------------------

@Serializable
data class MessageResponse(val message: String? = null)

@Serializable
data class PageMetaDto(
    val size: Int = 0,
    val number: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

/** Spring Data "via_dto" page body: { "content": [...], "page": { ... } }. */
@Serializable
data class PageEnvelopeDto<T>(val content: List<T> = emptyList(), val page: PageMetaDto = PageMetaDto())

@Serializable
data class GeoJsonGeometry(
    val type: String? = null,
    val coordinates: kotlinx.serialization.json.JsonElement? = null,
)

// ---------------------------------------------------------------------------
// Authentication & registration
// ---------------------------------------------------------------------------

@Serializable
data class AuthResponse(
    val token: String? = null,
    val tokenType: String? = null,
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
)

@Serializable
data class LoginRequest(val usernameOrEmail: String, val password: String)

@Serializable
data class RegisterRequest(
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
data class VerifyRegistrationRequest(val email: String, val code: String)

@Serializable
data class ResendRegistrationCodeRequest(val email: String, val turnstileToken: String? = null)

@Serializable
data class RegistrationStatusResponse(val enabled: Boolean = false, val passwordRequired: Boolean = false)

// ---------------------------------------------------------------------------
// Activity types / shared enums (strings mirror server enum names)
// ---------------------------------------------------------------------------

object ActivityTypes {
    val ALL = listOf(
        "RUN", "RIDE", "HIKE", "WALK", "SWIM", "ALPINE_SKI", "BACKCOUNTRY_SKI",
        "NORDIC_SKI", "SNOWBOARD", "ROWING", "KAYAKING", "CANOEING", "INLINE_SKATING",
        "ROCK_CLIMBING", "MOUNTAINEERING", "TENNIS", "FISTBALL", "YOGA", "WORKOUT", "OTHER",
    )
    fun icon(activityType: String?): String = when (activityType?.uppercase()) {
        "RUN" -> "🏃"
        "RIDE" -> "🚴"
        "HIKE" -> "🥾"
        "WALK" -> "🚶"
        "SWIM" -> "🏊"
        "ALPINE_SKI", "BACKCOUNTRY_SKI", "NORDIC_SKI" -> "⛷️"
        "SNOWBOARD" -> "🏂"
        "ROWING" -> "🚣"
        "KAYAKING", "CANOEING" -> "🛶"
        "INLINE_SKATING" -> "🛼"
        "ROCK_CLIMBING" -> "🧗"
        "MOUNTAINEERING" -> "⛰️"
        "TENNIS" -> "🎾"
        "FISTBALL" -> "🏐"
        "YOGA" -> "🧘"
        "WORKOUT" -> "💪"
        else -> "🏋️"
    }
}

object ActivityVisibilities {
    val PUBLIC = "PUBLIC"
    val FOLLOWERS = "FOLLOWERS"
    val LOCAL = "LOCAL"
    val PRIVATE = "PRIVATE"
    val ALL = listOf("PUBLIC", "FOLLOWERS", "LOCAL", "PRIVATE")
}

object ReactionPalette {
    val ALL = listOf("❤️", "👍", "🔥", "💪", "🏔️", "🤯", "🥲")
    val DEFAULT = "❤️"
}
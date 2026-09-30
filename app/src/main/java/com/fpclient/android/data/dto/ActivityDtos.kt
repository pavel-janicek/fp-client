package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ---------------------------------------------------------------------------
// Activities
// ---------------------------------------------------------------------------

@Serializable
data class ActivityDto(
    val id: String = "",
    val userId: String? = null,
    val activityType: String? = null,
    val title: String? = null,
    val description: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val timezone: String? = null,
    val visibility: String? = null,
    val visibilityLocked: Boolean? = null,
    val totalDistance: Double? = null,
    val totalDurationSeconds: Long? = null,
    val elevationGain: Double? = null,
    val elevationLoss: Double? = null,
    val metrics: ActivityMetricsDto? = null,
    val privacyPreferencesMode: String? = null,
    val routeDownloadAvailable: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val activityLocation: String? = null,
    /**
     * The server's `ActivityDTO.creationSource` (FIT/GPX/TCX upload, manual entry, ...).
     * It used to be declared here as `entryMethod`, which is not a name the server ever
     * sends, so the field was permanently null.
     */
    @SerialName("creationSource") val creationSource: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val user: UserDto? = null,
    val author: UserDto? = null,
    val owner: UserDto? = null,
    @SerialName("ownerUsername") val ownerUsername: String? = null,
    @SerialName("ownerDisplayName") val ownerDisplayName: String? = null,
    @SerialName("ownerAvatarUrl") val ownerAvatarUrl: String? = null,
    val actorUri: String? = null,
    val isLocal: Boolean = false,
    val simplifiedTrack: GeoJsonGeometry? = null,
    val hasGpsTrack: Boolean? = null,
    val indoor: Boolean? = null,
    val subSport: String? = null,
    val indoorDetectionMethod: String? = null,
    val context: ActivityContextDto? = null,
    val likesCount: Long? = null,
    val commentsCount: Long? = null,
    val likedByCurrentUser: Boolean? = null,
    val reactionCounts: Map<String, Long>? = null,
    val currentUserReaction: String? = null,
    /** Number of boosts (ActivityPub announces) of this activity. */
    val boostsCount: Long? = null,
    /** True when the signed-in user has boosted this activity. */
    val boostedByCurrentUser: Boolean? = null,
    /** True when the signed-in user is allowed to boost this activity. */
    val boostEligible: Boolean? = null,
    val privacyZones: List<PrivacyZonePreviewDto>? = null,
) {
    /**
     * Whether the trim workspace can be offered for this activity. The web editor gates its own
     * Trim button on exactly these two things (`activities/edit.html`: `creationSource ===
     * 'MANUAL' || !hasGpsTrack` hides it); anything else the server cannot trim (an unsupported
     * source format, fewer than three points, points without timestamps) still answers the trim
     * request with a 400 whose message the app shows as-is.
     */
    val isTrimmable: Boolean
        get() = creationSource != "MANUAL" && hasGpsTrack == true

    val resolvedUsername: String?
        get() = username ?: ownerUsername ?: author?.username ?: owner?.username ?: user?.username
            ?: actorUri?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotBlank() }
    val resolvedDisplayName: String?
        get() = displayName ?: ownerDisplayName ?: author?.displayName ?: owner?.displayName ?: user?.displayName
    val resolvedAvatarUrl: String?
        get() = avatarUrl ?: ownerAvatarUrl ?: author?.avatarUrl ?: owner?.avatarUrl ?: user?.avatarUrl

    /** Full `@username@host` handle so remote authors keep their home instance. */
    val fullHandle: String?
        get() = com.fpclient.android.util.ActorHandle.full(resolvedUsername, actorUri)
}

/** A single boost (repost/announce) of an activity by an actor. Mirrors the server's BoostDTO. */
@Serializable
data class BoostDto(
    val id: String? = null,
    val activityId: String? = null,
    val actorUri: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val local: Boolean = true,
) {

    /** Full `@username@host` handle so remote boosters keep their home instance. */
    val fullHandle: String?
        get() = com.fpclient.android.util.ActorHandle.full(
            actorUri?.substringAfterLast('/')?.substringBefore('?'),
            actorUri,
        )
}

@Serializable
data class ActivityMetricsDto(
    val averageSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val averagePaceSeconds: Long? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averageCadence: Int? = null,
    val maxCadence: Int? = null,
    val averagePower: Int? = null,
    val maxPower: Int? = null,
    val normalizedPower: Int? = null,
    val calories: Int? = null,
    val averageTemperature: Double? = null,
    val maxElevation: Double? = null,
    val minElevation: Double? = null,
    val totalAscent: Double? = null,
    val totalDescent: Double? = null,
    val movingTimeSeconds: Long? = null,
    val stoppedTimeSeconds: Long? = null,
    val totalSteps: Int? = null,
    val trainingStressScore: Double? = null,
)

@Serializable
data class ActivityContextDto(
    val name: String? = null,
    val title: String? = null,
    val normalized: String? = null,
    val iconClass: String? = null,
)

@Serializable
data class PrivacyZonePreviewDto(
    val id: String? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
)

/** Compact activity used by profile / user activity lists. */
@Serializable
data class ActivitySummaryDto(
    val id: String = "",
    val activityType: String? = null,
    val title: String? = null,
    val startedAt: String? = null,
    val timezone: String? = null,
    val totalDistance: Double? = null,
    val totalDurationSeconds: Long? = null,
    val hasGpsTrack: Boolean = false,
    val simplifiedTrack: GeoJsonGeometry? = null,
    val indoor: Boolean = false,
)

@Serializable
data class PageEnvelopeActivitySummaryDto(
    val content: List<ActivitySummaryDto> = emptyList(),
    val page: PageMetaDto = PageMetaDto(),
)

/** Timeline activity card. */
@Serializable
data class TimelineActivityDto(
    val id: String = "",
    val activityType: String? = null,
    val title: String? = null,
    val description: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val timezone: String? = null,
    val totalDistance: Double? = null,
    val totalDurationSeconds: Long? = null,
    val movingTimeSeconds: Long? = null,
    val stoppedTimeSeconds: Long? = null,
    val elevationGain: Double? = null,
    val elevationLoss: Double? = null,
    val visibility: String? = null,
    val createdAt: String? = null,
    val activityLocation: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val actorUri: String? = null,
    val isLocal: Boolean = false,
    /**
     * The activity's canonical URL on its origin server (set for federated
     * items, where the local `id` is only a metadata mirror and the in-app
     * detail would 404 — the web client opens this URI instead).
     */
    val activityUri: String? = null,
    val simplifiedTrack: GeoJsonGeometry? = null,
    val mapImageUrl: String? = null,
    val likesCount: Long? = null,
    val commentsCount: Long? = null,
    val likedByCurrentUser: Boolean? = null,
    val reactionCounts: Map<String, Long>? = null,
    val currentUserReaction: String? = null,
    val indoor: Boolean? = null,
    val context: ActivityContextDto? = null,
    val metrics: TimelineMetricsDto? = null,
) {
    /** Full `@username@host` handle so remote authors keep their home instance. */
    val fullHandle: String?
        get() = com.fpclient.android.util.ActorHandle.full(username, actorUri)
}

@Serializable
data class TimelineMetricsDto(
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averageSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val averagePaceSeconds: Long? = null,
    val averagePower: Int? = null,
    val calories: Int? = null,
    val movingTimeSeconds: Long? = null,
    val stoppedTimeSeconds: Long? = null,
)

@Serializable
data class PageEnvelopeTimelineDto(
    val content: List<TimelineActivityDto> = emptyList(),
    val page: PageMetaDto = PageMetaDto(),
)

@Serializable
data class ManualActivityRequest(
    val activityType: String,
    val title: String? = null,
    val description: String? = null,
    val startedAt: String,
    val timezone: String,
    val durationSeconds: Long,
    val distanceMeters: Double? = null,
    val elevationGainMeters: Double? = null,
    val indoor: Boolean,
    val context: String? = null,
    val visibility: String? = null,
    val startLocationId: Int? = null,
)

// ---------------------------------------------------------------------------
// Track / GeoJSON (source of truth for rendering the activity on a map)
// ---------------------------------------------------------------------------

/** A GPS track point, either raw (from trackPoints) or parsed from GeoJSON. */
@Serializable
data class TrackPointDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val elevation: Double? = null,
    val privacySegment: Int = 0,
)

/** Response of GET /api/activities/{id}/track (GeoJSON FeatureCollection). */
@Serializable
data class TrackFeatureCollectionDto(
    val type: String? = null,
    val features: List<TrackFeatureDto> = emptyList(),
)

@Serializable
data class TrackFeatureDto(
    val type: String? = null,
    val geometry: TrackGeometryDto? = null,
    val properties: TrackPropertiesDto? = null,
)

@Serializable
data class TrackGeometryDto(
    val type: String? = null,
    val coordinates: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class TrackPropertiesDto(
    val title: String? = null,
    val activityType: String? = null,
    val distance: Double? = null,
    val duration: Long? = null,
)

// ---------------------------------------------------------------------------
// Activity update (title / description / visibility / type / trim)
// ---------------------------------------------------------------------------

@Serializable
data class ActivityUpdateRequest(
    val title: String,
    val description: String? = null,
    /** Required by the server (PUT replaces metadata); preserve the current value when editing only title/description. */
    val visibility: String,
    val activityType: String? = null,
    /**
     * The server replaces the context with whatever this field carries, so it must be sent
     * back for edits that are not about the context: a missing value clears a COMMUTE/RACE
     * context, and clearing RACE also drops the activity's official result
     * (`ActivityLifecycleService.updateActivity`).
     */
    val context: String? = null,
    val indoor: Boolean? = null,
    /** Keeps [ActivityTrimSelection] of the original track; null means "leave the track alone". */
    val trim: ActivityTrimSelection? = null,
    val expectedUpdatedAt: String? = null,
)

/**
 * The metadata a `PUT /api/web/activities/{id}` replaces. Each missing field is treated by the
 * server as "clear it", so a screen that edits one thing (or only trims the track) must send the
 * activity's current values back for the rest.
 */
fun ActivityDto.toUpdateRequest(trim: ActivityTrimSelection? = null): ActivityUpdateRequest =
    ActivityUpdateRequest(
        title = title.orEmpty(),
        description = description,
        visibility = visibility ?: ActivityVisibilities.PUBLIC,
        // `context.name` is the enum name the server accepts back (COMMUTE/RACE).
        context = context?.name,
        trim = trim,
    )

// ---------------------------------------------------------------------------
// Activity trimming — the workspace behind `GET /api/web/activities/{id}/trim`
// ---------------------------------------------------------------------------

/**
 * The server's `ActivityTrimDataDTO`: the activity's **original, untrimmed** track (parsed
 * again from the uploaded FIT/GPX/TCX file) plus the index range that is currently stored, so
 * a trim can be widened again as well as narrowed.
 */
@Serializable
data class ActivityTrimDataDto(
    val points: List<ActivityTrimPointDto> = emptyList(),
    val currentStartIndex: Int = 0,
    val currentEndIndex: Int = -1,
    val currentElevationGain: Double? = null,
    val currentElevationLoss: Double? = null,
    val originalElevationGain: Double? = null,
    val originalElevationLoss: Double? = null,
)

/** One point of the original track. Indices address this list, not the stored track. */
@Serializable
data class ActivityTrimPointDto(
    val index: Int = 0,
    val timestamp: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val elevation: Double? = null,
    val distance: Double? = null,
    val speed: Double? = null,
    val elevationSegment: Int = 0,
)

/** `{"startIndex": n, "endIndex": m}` — the range of original points to keep (server validates `start < end`). */
@Serializable
data class ActivityTrimSelection(
    val startIndex: Int,
    val endIndex: Int,
)

// ---------------------------------------------------------------------------
// Locations
// ---------------------------------------------------------------------------

@Serializable
data class LocationSuggestionDto(
    val id: Int? = null,
    val name: String? = null,
    val adminArea: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
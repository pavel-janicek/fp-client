package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Peaks
//
// Peaks are a **profile** feature, not an analytics one: the server's own web client keeps
// them in `templates/profile/` (peaks + peak detail) and has no peaks under
// `templates/analytics/`, so the app mirrors that placement.
// ---------------------------------------------------------------------------

/**
 * `UserPeakDTO` — a summit the user has reached, as counted across their visible activities.
 *
 * [type] is the server's `PeakType` enum (`PEAK`, `VOLCANO`, `MOUNTAIN_PASS`, `SADDLE`).
 * [wikipedia] and the coordinates are optional and frequently absent for a plain local hill.
 */
@Serializable
data class UserPeakDto(
    val id: Long = 0,
    val name: String? = null,
    val wikipedia: String? = null,
    val type: String? = null,
    val elevation: Int? = null,
    val visitCount: Long = 0,
    val latestActivityId: String? = null,
    val latestVisitedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** A one-word badge for the summit kind, degrading gracefully for a future enum value. */
    val kindLabel: String
        get() = when (type) {
            "VOLCANO" -> "Volcano"
            "MOUNTAIN_PASS" -> "Pass"
            "SADDLE" -> "Saddle"
            "PEAK" -> "Peak"
            else -> type?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
                ?: "Peak"
        }
}

/**
 * `UserPeakPageDTO` — deliberately **flat**, not the `{content, page}` envelope every other
 * paginated endpoint on the server uses. Reusing `PageEnvelopeDto` here would silently
 * produce an always-empty list, so the shape is mirrored literally.
 */
@Serializable
data class UserPeakPageDto(
    val content: List<UserPeakDto> = emptyList(),
    val number: Int = 0,
    val size: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Long = 0,
)

/**
 * `PeakActivityTrackDTO` — one activity that reached the peak, with its track as a raw
 * GeoJSON object (the server's `filterTrackPointsToGeoJson` returns a `Map`, not a typed
 * DTO). Hand the `route` to `TrackParser.fromGeometry`, which already speaks this format.
 */
@Serializable
data class PeakActivityTrackDto(
    val activityId: String? = null,
    val route: JsonObject? = null,
)

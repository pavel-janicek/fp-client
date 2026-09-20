package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Privacy zones
// ---------------------------------------------------------------------------

@Serializable
data class PrivacyZoneDto(
    val id: String? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
    /** Server's `PrivacyZoneDTO.isActive` (Lombok `getIsActive()` serializes as `isActive`). */
    val isActive: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PrivacyZoneCreateRequest(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
)

@Serializable
data class PrivacyZoneUpdateRequest(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
)

/**
 * Body of `PATCH /api/web/privacy-zones/{id}/toggle`. The endpoint is not a server-side flip:
 * `PrivacyZoneResource.togglePrivacyZone` reads the desired state from this map and answers
 * 400 ("Required request body is missing") when the body is absent.
 */
@Serializable
data class PrivacyZoneToggleRequest(
    val isActive: Boolean,
)

// ---------------------------------------------------------------------------
// Heatmap
// ---------------------------------------------------------------------------

@Serializable
data class HeatmapResponse(
    val points: List<HeatmapPointDto> = emptyList(),
    val bounds: HeatmapBoundsDto? = null,
)

@Serializable
data class HeatmapPointDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val intensity: Int = 1,
)

@Serializable
data class HeatmapBoundsDto(
    val minLatitude: Double? = null,
    val minLongitude: Double? = null,
    val maxLatitude: Double? = null,
    val maxLongitude: Double? = null,
)
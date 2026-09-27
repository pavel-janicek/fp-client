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

// ---------------------------------------------------------------------------
// Heatmap — the server's wire format
// ---------------------------------------------------------------------------

/**
 * `GET /api/web/heatmap/me` answers a **GeoJSON FeatureCollection**, not a bespoke
 * `{points, bounds}` object:
 *
 * ```json
 * {"type":"FeatureCollection","maxIntensity":7,"activityCount":42,
 *  "features":[{"type":"Feature",
 *               "geometry":{"type":"Point","coordinates":[16.37,48.21]},
 *               "properties":{"intensity":7}}]}
 * ```
 *
 * The client used to read this into [HeatmapResponse], which expects `points`/`bounds` and
 * therefore always deserialised to an *empty* list — a silent failure, because every field
 * had a default. These types mirror the payload exactly, and [HeatmapMapper] converts it to
 * the UI model the heatmap card draws.
 */
@Serializable
data class HeatmapFeatureCollectionDto(
    val type: String? = null,
    val features: List<HeatmapFeatureDto> = emptyList(),
    val maxIntensity: Int? = null,
    val activityCount: Long? = null,
)

@Serializable
data class HeatmapFeatureDto(
    val type: String? = null,
    val geometry: HeatmapGeometryDto? = null,
    val properties: HeatmapPropertiesDto? = null,
)

@Serializable
data class HeatmapGeometryDto(
    val type: String? = null,
    /** GeoJSON position: `[longitude, latitude]` — x first, which is the opposite of the
     *  `GeoPoint(latitude, longitude)` the map overlay wants. */
    val coordinates: List<Double> = emptyList(),
)

@Serializable
data class HeatmapPropertiesDto(
    val intensity: Int? = null,
)

/**
 * Converts the server's GeoJSON heatmap into the [HeatmapResponse] the card renders.
 *
 * Android-free and pure, so the axis order — the one thing that can silently put a heatmap
 * in the ocean — is unit-tested; see `HeatmapMapperTest`.
 */
object HeatmapMapper {

    fun toUi(dto: HeatmapFeatureCollectionDto): HeatmapResponse {
        val points = dto.features.mapNotNull { feature ->
            val coordinates = feature.geometry?.coordinates ?: return@mapNotNull null
            // GeoJSON is [lon, lat]; anything shorter is not a position we can draw.
            if (coordinates.size < 2) return@mapNotNull null
            HeatmapPointDto(
                latitude = coordinates[1],
                longitude = coordinates[0],
                intensity = feature.properties?.intensity ?: 1,
            )
        }
        return HeatmapResponse(points = points, bounds = boundsOf(points))
    }

    /** The bounding box the map zooms to; `null` when there is nothing to frame. */
    private fun boundsOf(points: List<HeatmapPointDto>): HeatmapBoundsDto? {
        if (points.isEmpty()) return null
        return HeatmapBoundsDto(
            minLatitude = points.minOf { it.latitude!! },
            minLongitude = points.minOf { it.longitude!! },
            maxLatitude = points.maxOf { it.latitude!! },
            maxLongitude = points.maxOf { it.longitude!! },
        )
    }
}
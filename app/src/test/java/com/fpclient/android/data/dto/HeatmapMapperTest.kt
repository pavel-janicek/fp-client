package com.fpclient.android.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heatmap endpoint speaks GeoJSON, not the `{points, bounds}` shape the app used to
 * expect. Because every field of the old DTO had a default, the mismatch failed *silently* —
 * an empty list, no error, no heatmap. These pin the real wire format, above all the
 * `[longitude, latitude]` axis order, which is the one mistake that would draw a plausible
 * map in the wrong hemisphere.
 */
class HeatmapMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsTheServersGeoJsonFeatureCollection() {
        val payload = """
            {"type":"FeatureCollection","maxIntensity":7,"activityCount":42,
             "features":[
               {"type":"Feature","geometry":{"type":"Point","coordinates":[16.3738,48.2082]},
                "properties":{"intensity":7}},
               {"type":"Feature","geometry":{"type":"Point","coordinates":[11.4237,48.2625]},
                "properties":{"intensity":2}}
             ]}
        """.trimIndent()

        val result = HeatmapMapper.toUi(json.decodeFromString<HeatmapFeatureCollectionDto>(payload))

        assertEquals(2, result.points.size)
        // coordinates are [lon, lat] — latitude is the SECOND element.
        assertEquals(48.2082, result.points[0].latitude!!, 1e-9)
        assertEquals(16.3738, result.points[0].longitude!!, 1e-9)
        assertEquals(7, result.points[0].intensity)
        assertEquals(2, result.points[1].intensity)
    }

    @Test
    fun carriesTheGenerationStatusOfServersSince549() {
        // A user with activities whose heatmap job has not run yet: no cells, status reported.
        val payload = """
            {"type":"FeatureCollection","features":[],"maxIntensity":0,
             "generationStatus":"PENDING","hasHeatmap":false,"activityCount":3}
        """.trimIndent()

        val result = HeatmapMapper.toUi(json.decodeFromString<HeatmapFeatureCollectionDto>(payload))

        assertEquals("PENDING", result.generationStatus)
        assertEquals(false, result.hasHeatmap)
        assertTrue(result.points.isEmpty())
    }

    @Test
    fun anOlderServerWithoutStatusKeepsTheOldBehaviour() {
        val payload = """{"type":"FeatureCollection","features":[],"maxIntensity":0,"activityCount":0}"""

        val result = HeatmapMapper.toUi(json.decodeFromString<HeatmapFeatureCollectionDto>(payload))

        assertNull(result.generationStatus)
        assertNull(result.hasHeatmap)
        assertNull(HeatmapMapper.statusMessage(result.generationStatus))
    }

    @Test
    fun statusMessagesMatchTheWebHeatmapPage() {
        // Word for word from displayGenerationStatus() in the server's static/js/heatmap.js.
        assertEquals("Your heatmap is being generated.", HeatmapMapper.statusMessage("PROCESSING"))
        assertEquals("Your heatmap is queued for automatic generation.", HeatmapMapper.statusMessage("PENDING"))
        assertEquals("Your heatmap could not be updated.", HeatmapMapper.statusMessage("FAILED"))
        assertEquals("Your heatmap is up to date.", HeatmapMapper.statusMessage("UP_TO_DATE"))
        assertEquals(
            "Your heatmap will be generated automatically after you upload activities with GPS tracks.",
            HeatmapMapper.statusMessage("NO_HEATMAP"),
        )
    }

    @Test
    fun anUnknownFutureStatusShowsNothingRatherThanFailing() {
        val payload = """{"type":"FeatureCollection","features":[],"generationStatus":"SOMETHING_NEW","hasHeatmap":true}"""

        val result = HeatmapMapper.toUi(json.decodeFromString<HeatmapFeatureCollectionDto>(payload))

        assertEquals("SOMETHING_NEW", result.generationStatus)
        assertNull(HeatmapMapper.statusMessage(result.generationStatus))
    }

    @Test
    fun computesTheBoundingBoxTheMapZoomsTo() {
        val dto = HeatmapFeatureCollectionDto(
            features = listOf(
                feature(16.0, 48.0),
                feature(11.0, 49.0),
            ),
        )

        val bounds = HeatmapMapper.toUi(dto).bounds!!

        assertEquals(48.0, bounds.minLatitude!!, 1e-9)
        assertEquals(49.0, bounds.maxLatitude!!, 1e-9)
        assertEquals(11.0, bounds.minLongitude!!, 1e-9)
        assertEquals(16.0, bounds.maxLongitude!!, 1e-9)
    }

    @Test
    fun anEmptyHeatmapHasNoBoundsAndNoPoints() {
        val result = HeatmapMapper.toUi(HeatmapFeatureCollectionDto())

        assertTrue(result.points.isEmpty())
        assertNull(result.bounds)
    }

    /** A feature whose geometry is unusable must be skipped, not crash the whole map. */
    @Test
    fun skipsFeaturesWithoutAUsablePosition() {
        val dto = HeatmapFeatureCollectionDto(
            features = listOf(
                HeatmapFeatureDto(),
                HeatmapFeatureDto(geometry = HeatmapGeometryDto(coordinates = listOf(16.0))),
                feature(16.0, 48.0),
            ),
        )

        val result = HeatmapMapper.toUi(dto)

        assertEquals(1, result.points.size)
        assertEquals(48.0, result.points.single().latitude!!, 1e-9)
    }

    private fun feature(lon: Double, lat: Double) = HeatmapFeatureDto(
        type = "Feature",
        geometry = HeatmapGeometryDto(type = "Point", coordinates = listOf(lon, lat)),
        properties = HeatmapPropertiesDto(intensity = 1),
    )

    @Test
    fun readsFitPub14MetadataWithTilesInsteadOfCells() {
        // FitPub 1.4 (#556): metadata only, the server draws the tiles itself.
        val payload = """
            {"revision":"6f1c2a4e-0b9d-4f7a-9c35-2d8e1f0a7b11","generationStatus":"UP_TO_DATE",
             "hasHeatmap":true,"activityCount":42,
             "bounds":{"minLon":14.2,"minLat":49.9,"maxLon":14.7,"maxLat":50.2},
             "updatedAt":"2026-10-27T08:00:00Z","saturation":100,"legend":"Approximate frequency of use"}
        """.trimIndent()

        val result = HeatmapMapper.toUi(json.decodeFromString<HeatmapFeatureCollectionDto>(payload))

        assertEquals("6f1c2a4e-0b9d-4f7a-9c35-2d8e1f0a7b11", result.revision)
        assertTrue(result.points.isEmpty())
        assertEquals(true, result.hasHeatmap)
        assertEquals("UP_TO_DATE", result.generationStatus)
        // Lon/lat named, not positional: min latitude is minLat, not minLon.
        assertEquals(49.9, result.bounds!!.minLatitude!!, 1e-9)
        assertEquals(14.2, result.bounds!!.minLongitude!!, 1e-9)
        assertEquals(50.2, result.bounds!!.maxLatitude!!, 1e-9)
        assertEquals(14.7, result.bounds!!.maxLongitude!!, 1e-9)
    }

    @Test
    fun aHeatmapWithoutRoutesYetHasNoRevisionOrBounds() {
        val payload = """{"revision":null,"generationStatus":"NO_HEATMAP","hasHeatmap":false,"activityCount":0,"bounds":null}"""

        val result = HeatmapMapper.toUi(json.decodeFromString<HeatmapFeatureCollectionDto>(payload))

        assertNull(result.revision)
        assertNull(result.bounds)
        assertEquals(false, result.hasHeatmap)
    }

    @Test
    fun olderServersKeepTheirCellsAndNoRevision() {
        val payload = """
            {"type":"FeatureCollection","maxIntensity":1,
             "features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[16.0,48.0]},"properties":{"intensity":1}}]}
        """.trimIndent()

        val result = HeatmapMapper.toUi(json.decodeFromString<HeatmapFeatureCollectionDto>(payload))

        assertNull(result.revision)
        assertEquals(1, result.points.size)
        assertEquals(48.0, result.bounds!!.minLatitude!!, 1e-9)
    }
}

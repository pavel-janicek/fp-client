package com.fpclient.android.util

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityTest {
    @Test
    fun trackParser_readsLineStringLongitudeLatitudeOrder() {
        val geometry = kotlinx.serialization.json.Json.parseToJsonElement(
            "{\"type\":\"LineString\",\"coordinates\":[[10.5,51.2,100],[10.6,51.3]]}"
        )
        val points = TrackParser.fromGeometry("LineString", geometry.jsonObject["coordinates"])

        assertEquals(1, points.size)
        assertEquals(2, points.single().size)
        assertEquals(51.2, points.single()[0].latitude, 0.0001)
        assertEquals(10.5, points.single()[0].longitude, 0.0001)
    }

    @Test
    fun trackParser_skipsUnsupportedGeometry() {
        val geometry = kotlinx.serialization.json.Json.parseToJsonElement("[[1,2],[3,4]]")
        assertTrue(TrackParser.fromGeometry("Polygon", geometry).isEmpty())
    }

    @Test
    fun format_coversMetricImperialAndDuration() {
        assertEquals("1.50 km", Format.distance(1500.0, "METRIC"))
        assertEquals("0.93 mi", Format.distance(1500.0, "IMPERIAL"))
        assertEquals("1:01:05", Format.duration(3665))
        assertEquals("5:30 /km", Format.pace(330, "METRIC"))
        assertEquals("8:51 /mi", Format.pace(330, "IMPERIAL"))
        assertEquals("22.4 mph", Format.speedKmh(36.0, "IMPERIAL"))
    }

    @Test
    fun format_paceFallsBackToAverageSpeed() {
        // Timeline feed omits averagePaceSeconds for Hikes but sends averageSpeed (km/h);
        // 3600 / 3.95 = 911 s/km — the same value the detail endpoint reports.
        assertEquals("15:11 /km", Format.pace(null, 3.95, null, null, "METRIC"))
        // Server-provided pace wins over the derived value.
        assertEquals("15:11 /km", Format.pace(911, 3.95, null, null, "METRIC"))
    }

    @Test
    fun format_paceFallsBackToDurationOverDistance() {
        // Web app's own formula: duration seconds / distance km.
        assertEquals("15:45 /km", Format.pace(null, null, 2294, 2426.65, "METRIC"))
        // Imperial conversion is exercised by the primary path (Format.pace(330, "IMPERIAL") above).
        assertEquals("—", Format.pace(null, null, 2294, 0.0, "METRIC"))
        assertEquals("—", Format.pace(null, null, null, 2426.65, "METRIC"))
    }

    @Test
    fun urlBuilder_joinsPathsAndBuildsAvatarUrls() {
        assertEquals("https://example.test/api/users", UrlBuilder.join("https://example.test/", "/api/users"))
        assertEquals("https://example.test/avatar.png", UrlBuilder.avatar("https://example.test", "/avatar.png"))
        assertEquals("https://cdn.example/avatar.png", UrlBuilder.avatar("https://example.test", "https://cdn.example/avatar.png"))
        assertEquals(null, UrlBuilder.avatar("https://example.test", null))
    }
}

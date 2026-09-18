package com.fpclient.android.util

import com.fpclient.android.recording.TrackMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the circle geometry used by the privacy-zone placement minimap. */
class GeoMathTest {

    private val centerLat = 48.137
    private val centerLon = 11.575

    @Test
    fun circlePoints_allLieOnTheRequestedRadiusRing() {
        for (radius in listOf(50.0, 500.0, 10_000.0)) {
            for ((lat, lon) in GeoMath.circlePoints(centerLat, centerLon, radius)) {
                val distance = TrackMath.haversineMeters(centerLat, centerLon, lat, lon)
                // 1% tolerance: destination-point vs haversine on a sphere agree closely.
                assertEquals(radius, distance, radius * 0.01)
            }
        }
    }

    @Test
    fun circlePoints_producesTheRequestedNumberOfClosedRingVertices() {
        val points = GeoMath.circlePoints(centerLat, centerLon, 500.0, segments = 36)
        assertEquals(36, points.size)
        // First vertex at bearing 0 is due north of the center (northern hemisphere).
        val (firstLat, firstLon) = points.first()
        assertTrue(firstLat > centerLat)
        assertEquals(centerLon, firstLon, 1e-6)
    }

    @Test
    fun circlePoints_wrapsLongitudesNearTheAntimeridian() {
        for ((_, lon) in GeoMath.circlePoints(0.0, 179.999, 5_000.0)) {
            assertTrue(lon in -180.0..180.0)
        }
        for ((_, lon) in GeoMath.circlePoints(0.0, -179.999, 5_000.0)) {
            assertTrue(lon in -180.0..180.0)
        }
    }

    @Test
    fun normalizeLongitude_wrapsIntoHalfOpenRange() {
        assertEquals(0.0, GeoMath.normalizeLongitude(0.0), 0.0)
        // The antimeridian normalizes to -180 (half-open range), the same meridian.
        assertEquals(-180.0, GeoMath.normalizeLongitude(180.0), 1e-9)
        assertEquals(-180.0, GeoMath.normalizeLongitude(-180.0), 1e-9)
        assertEquals(-179.0, GeoMath.normalizeLongitude(181.0), 1e-9)
        assertEquals(179.0, GeoMath.normalizeLongitude(-181.0), 1e-9)
        assertEquals(1.0, GeoMath.normalizeLongitude(-719.0), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun circlePoints_rejectsNonPositiveRadius() {
        GeoMath.circlePoints(centerLat, centerLon, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun circlePoints_rejectsTooFewSegments() {
        GeoMath.circlePoints(centerLat, centerLon, 500.0, segments = 2)
    }
}

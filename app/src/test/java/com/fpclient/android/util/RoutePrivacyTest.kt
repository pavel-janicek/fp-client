package com.fpclient.android.util

import com.fpclient.android.data.dto.PrivacyZonePreviewDto
import com.fpclient.android.util.RoutePrivacy.Coverage
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePrivacyTest {

    // About 111 m per 0.001° of latitude.
    private val home = PrivacyZonePreviewDto(id = "z", name = "Home", latitude = 50.0, longitude = 14.0, radiusMeters = 500)
    private fun north(steps: Int) = (0 until steps).map { 50.0 + it * 0.001 to 14.0 }

    @Test
    fun routeEntirelyInsideAZone_isFull() {
        assertEquals(Coverage.FULL, RoutePrivacy.coverage(north(4), listOf(home)))
    }

    @Test
    fun oneStrayPointOutside_isStillFull() {
        // The server refuses a route with fewer than two visible points.
        assertEquals(Coverage.FULL, RoutePrivacy.coverage(north(4) + (50.02 to 14.0), listOf(home)))
    }

    @Test
    fun routeLeavingTheZone_isPartial() {
        assertEquals(Coverage.PARTIAL, RoutePrivacy.coverage(north(20), listOf(home)))
    }

    @Test
    fun routeAwayFromZones_isNone() {
        assertEquals(Coverage.NONE, RoutePrivacy.coverage(north(5).map { (la, lo) -> la + 1.0 to lo }, listOf(home)))
    }

    @Test
    fun noZonesOrIncompleteZones_isNone() {
        assertEquals(Coverage.NONE, RoutePrivacy.coverage(north(4), emptyList()))
        assertEquals(Coverage.NONE, RoutePrivacy.coverage(north(4), listOf(home.copy(radiusMeters = null))))
        assertEquals(Coverage.NONE, RoutePrivacy.coverage(emptyList(), listOf(home)))
    }
}

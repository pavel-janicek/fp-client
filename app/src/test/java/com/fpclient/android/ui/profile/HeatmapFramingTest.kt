package com.fpclient.android.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.osmdroid.util.BoundingBox

/**
 * `MapView.zoomToBoundingBox` loops forever in `Projection.getCloserPixel` when the room left
 * for the box (size − 2 × border) is negative, because the fitted zoom is then NaN. That was
 * the 3.3.0 Me tab ANR (framing ran before the map's first layout, at width 0).
 */
class HeatmapFramingTest {

    private val czechia = BoundingBox(51.05, 18.86, 48.55, 12.09)

    @Test
    fun border_isNullBeforeTheMapHasASize() {
        assertNull(HeatmapFraming.border(czechia, 0, 0, 98))
        assertNull(HeatmapFraming.border(czechia, 1152, 0, 98))
    }

    @Test
    fun border_keepsRoomForTheDataOnASmallMap() {
        assertEquals(98, HeatmapFraming.border(czechia, 1152, 560, 98))
        assertEquals(49, HeatmapFraming.border(czechia, 1152, 100, 98))
        assertEquals(0, HeatmapFraming.border(czechia, 1, 1, 98))
    }

    @Test
    fun border_isNullForANonFiniteBox() {
        assertNull(HeatmapFraming.border(BoundingBox(Double.NaN, 18.86, 48.55, 12.09), 1152, 560, 98))
    }
}

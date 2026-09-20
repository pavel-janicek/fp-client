package com.fpclient.android.ui.record

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the "the user took the map over" rule of the Record flow's mini-map. Following the
 * live position may only be switched off by an actual pan: the map's own programmatic
 * camera moves (`animateTo`, `setCenter`, `zoomToSpan`) must never do it — a scroll-event
 * based rule did exactly that, which left the camera stuck where the screen was opened
 * instead of centering on the user.
 */
class RecordMapGestureTest {

    private val slop = 8f

    @Test
    fun `a one-finger drag past the touch slop is a user pan`() {
        assertTrue(isUserPan(pointerCount = 1, dx = 40f, dy = 0f, touchSlop = slop))
        assertTrue(isUserPan(pointerCount = 1, dx = 0f, dy = -40f, touchSlop = slop))
        assertTrue(isUserPan(pointerCount = 1, dx = -9f, dy = 0f, touchSlop = slop))
    }

    @Test
    fun `a tap or a wobble within the touch slop keeps following on`() {
        assertFalse(isUserPan(pointerCount = 1, dx = 0f, dy = 0f, touchSlop = slop))
        assertFalse(isUserPan(pointerCount = 1, dx = 3f, dy = -4f, touchSlop = slop))
        assertFalse(isUserPan(pointerCount = 1, dx = slop, dy = slop, touchSlop = slop))
    }

    @Test
    fun `pinch zooming does not stop following`() {
        // Two fingers: a zoom keeps the position centered, so it is not the user panning away.
        assertFalse(isUserPan(pointerCount = 2, dx = 120f, dy = 120f, touchSlop = slop))
    }
}

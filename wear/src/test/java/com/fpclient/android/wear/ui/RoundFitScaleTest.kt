package com.fpclient.android.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The guarantee behind [RoundSafeList]: at the scale [roundFitScale] returns, an item's whole
 * bounding box lies inside the circle, wherever the item is scrolled to.
 */
class RoundFitScaleTest {

    // A 454 px round watch (Play's review screenshot), radius less the edge margin.
    private val radius = 454f / 2f * (1f - EDGE_MARGIN_FRACTION)

    private fun cornerDistance(w: Float, h: Float, d: Float, s: Float): Float {
        val x = s * w / 2f
        val y = abs(d) + s * h / 2f
        return sqrt(x * x + y * y)
    }

    @Test
    fun everyItemFitsAtEveryScrollPosition() {
        // Full-width buttons and chips, a one-line label, the wrapped Body sensors message.
        val items = listOf(406f to 104f, 406f to 52f, 360f to 40f, 360f to 130f, 406f to 300f)
        for ((w, h) in items) {
            var d = -260f
            while (d <= 260f) {
                val s = roundFitScale(w, h, d, radius)
                assertTrue("w=$w h=$h d=$d s=$s", s in 0f..1f)
                if (s > 0f) {
                    assertTrue(
                        "w=$w h=$h d=$d corner ${cornerDistance(w, h, d, s)} > $radius",
                        cornerDistance(w, h, d, s) <= radius + 0.01f,
                    )
                }
                d += 1f
            }
        }
    }

    @Test
    fun scaleIsTheLargestThatFits() {
        // Where it scales down, a corner touches the circle: not shrunk more than needed.
        val s = roundFitScale(406f, 104f, 150f, radius)
        assertTrue(s < 1f)
        assertEquals(radius, cornerDistance(406f, 104f, 150f, s), 0.05f)
    }

    @Test
    fun itemsInTheMiddleKeepTheirSize() {
        // The Pause button of the review screenshot, centred: no change.
        assertEquals(1f, roundFitScale(406f, 104f, 0f, radius), 0f)
    }

    @Test
    fun itemsBeyondTheCircleVanish() {
        assertEquals(0f, roundFitScale(406f, 104f, radius, radius), 0f)
        assertEquals(0f, roundFitAlpha(0.4f), 0f)
        assertEquals(1f, roundFitAlpha(0.71f), 0f)
    }
}

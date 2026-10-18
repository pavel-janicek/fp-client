package com.fpclient.android.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/**
 * The web heatmap's rendering rules (server `static/js/heatmap.js` + Leaflet.heat 0.2.0 +
 * simpleheat) as ported in [HeatLayer]. A drift here would draw a different-looking map.
 */
class HeatLayerTest {

    @Test
    fun intensityIsLogScaledAgainstTheAccountMaximumLikeTheWeb() {
        assertEquals(1.0, HeatLayer.normalizedIntensity(397, 397), 1e-12)
        assertEquals(ln(2.0) / ln(398.0), HeatLayer.normalizedIntensity(1, 397), 1e-12)
        // Coarse viewport cells can exceed the finest-grid maximum: capped like the web.
        assertEquals(1.0, HeatLayer.normalizedIntensity(5_000, 397), 1e-12)
        // No usable maximum: the web's 0.5 fallback.
        assertEquals(0.5, HeatLayer.normalizedIntensity(7, null), 1e-12)
        assertEquals(0.5, HeatLayer.normalizedIntensity(7, 0), 1e-12)
    }

    @Test
    fun radiusAndBlurFollowCalculateDynamicRadius() {
        assertEquals(25.0, HeatLayer.radiusFor(5.0), 1e-12)
        assertEquals(25.0, HeatLayer.radiusFor(8.0), 1e-12)
        assertEquals(18.0, HeatLayer.radiusFor(10.0), 1e-12)
        assertEquals(16.0, HeatLayer.radiusFor(12.0), 1e-12)
        assertEquals(12.0, HeatLayer.radiusFor(14.0), 1e-12)
        assertEquals(10.0, HeatLayer.radiusFor(15.0), 1e-12)
        assertEquals(8.0, HeatLayer.radiusFor(16.0), 1e-12)
        assertEquals(6.0, HeatLayer.radiusFor(18.0), 1e-12)
        assertEquals(6.0, HeatLayer.radiusFor(20.0), 1e-12)
        assertEquals(10.0, HeatLayer.blurFor(25.0), 1e-12)
        assertEquals(4.0, HeatLayer.blurFor(6.0), 1e-12)
    }

    @Test
    fun zoomWeightIsLeafletHeatsV() {
        assertEquals(1.0, HeatLayer.zoomWeight(18.0), 1e-12)
        assertEquals(0.5, HeatLayer.zoomWeight(17.0), 1e-12)
        assertEquals(1.0 / 4096, HeatLayer.zoomWeight(4.0), 1e-15) // clamped at 12 levels
        assertEquals(1.0, HeatLayer.zoomWeight(19.0), 1e-12)
    }

    @Test
    fun pointsInOneCellMergeAtTheirWeightedCentreWithSummedValue() {
        // extent 20 → cells of 10 px; (2,2) and (6,6) share a cell, (50,50) has its own.
        val cells = HeatLayer.bin(
            xs = floatArrayOf(2f, 6f, 50f),
            ys = floatArrayOf(2f, 6f, 50f),
            weights = doubleArrayOf(0.1, 0.3, 0.2),
            count = 3, width = 100, height = 100, extent = 20.0, v = 1.0,
        ).sortedBy { it.x }

        assertEquals(2, cells.size)
        assertEquals(5f, cells[0].x) // (2×0.1 + 6×0.3) / 0.4 = 5
        assertEquals(5f, cells[0].y)
        assertEquals(0.4, cells[0].value, 1e-12)
        assertEquals(0.2, cells[1].value, 1e-12)
    }

    @Test
    fun cellValuesAreCappedAtMaxAndPointsFarOutsideTheViewAreDropped() {
        val cells = HeatLayer.bin(
            xs = floatArrayOf(10f, 11f, -500f),
            ys = floatArrayOf(10f, 11f, 10f),
            weights = doubleArrayOf(1.0, 1.0, 1.0),
            count = 3, width = 100, height = 100, extent = 20.0, v = 1.0,
        )
        assertEquals(1, cells.size)
        assertEquals(HeatLayer.MAX, cells.single().value, 1e-12)
    }

    @Test
    fun theGridIsAnchoredToTheMapSoPanningDoesNotRegroupCells() {
        fun groups(shift: Float, scroll: Long) = HeatLayer.bin(
            xs = floatArrayOf(8f - shift, 12f - shift),
            ys = floatArrayOf(5f, 5f),
            weights = doubleArrayOf(0.1, 0.1),
            count = 2, width = 100, height = 100, extent = 20.0, v = 1.0, scrollX = scroll,
        ).size
        // 8 and 12 straddle a 10 px cell border: two cells.
        assertEquals(2, groups(0f, 0))
        // Pan by 3 px: the points move on screen and the scroll moves with them — still two.
        assertEquals(2, groups(3f, 3))
    }

    @Test
    fun cellAlphaIsSimpleheatsGlobalAlpha() {
        assertEquals(HeatLayer.MIN_OPACITY, HeatLayer.cellAlpha(0.001), 1e-12)
        assertEquals(0.5, HeatLayer.cellAlpha(0.375), 1e-12)
        assertEquals(1.0, HeatLayer.cellAlpha(HeatLayer.MAX), 1e-12)
    }

    @Test
    fun paletteInterpolatesInPremultipliedSpaceLikeACanvasGradient() {
        val palette = HeatLayer.PALETTE
        assertEquals(256, palette.size)
        // Next to the transparent stop the colour is the dark-blue stop itself, not a darker
        // mix with black — that is what premultiplied interpolation does.
        assertEquals(0x00008B, palette[0])
        // Around 0.5: the green stop.
        val mid = palette[128]
        assertEquals(255, (mid shr 8) and 0xFF)
        assertTrue((mid shr 16) and 0xFF <= 8)
        assertEquals(0, mid and 0xFF)
        // The top: red with a trace of the red-orange stop.
        val top = palette[255]
        assertEquals(255, (top shr 16) and 0xFF)
        assertTrue((top shr 8) and 0xFF <= 5)
    }

    @Test
    fun colorizeTakesThePaletteColourAndKeepsTheAccumulatedAlpha() {
        val palette = IntArray(256) { it } // colour value = its index, easy to read back
        val pixels = intArrayOf(0x00000000, 0x40000000, 0xFF000000.toInt())

        HeatLayer.colorize(pixels, palette)

        assertEquals(0x00000000, pixels[0]) // untouched
        assertEquals((0x40 shl 24) or 0x40, pixels[1])
        assertEquals((0xFF shl 24) or 0xFF, pixels[2])
    }

    private val view = HeatLayer.View(zoom = 11.0, scrollX = 1000, scrollY = 2000, width = 1080, height = 600, dataVersion = 1)

    @Test
    fun frameFor_rendersTheFirstFrameAndASettledView() {
        assertEquals(HeatLayer.Frame.RENDER, HeatLayer.frameFor(null, view, settled = false))
        assertEquals(HeatLayer.Frame.RENDER, HeatLayer.frameFor(view, view.copy(scrollX = 1500), settled = true))
    }

    @Test
    fun frameFor_shiftsTheLastLayerWhileTheMapPans() {
        // A pan (and every frame of a fling) must not re-project every cell on the main thread.
        assertEquals(HeatLayer.Frame.SHIFT, HeatLayer.frameFor(view, view.copy(scrollX = 1040, scrollY = 1990), settled = false))
        assertEquals(HeatLayer.Frame.SHIFT, HeatLayer.frameFor(view, view, settled = false))
    }

    @Test
    fun frameFor_hidesTheLayerWhileZoomingOrResizing() {
        assertEquals(HeatLayer.Frame.HIDE, HeatLayer.frameFor(view, view.copy(zoom = 11.4), settled = false))
        assertEquals(HeatLayer.Frame.HIDE, HeatLayer.frameFor(view, view.copy(height = 2000), settled = false))
        assertEquals(HeatLayer.Frame.HIDE, HeatLayer.frameFor(view, view.copy(scrollX = 1040, dataVersion = 2), settled = false))
    }

    @Test
    fun frameFor_rendersFreshDataForAnUnchangedView() {
        assertEquals(HeatLayer.Frame.RENDER, HeatLayer.frameFor(view, view.copy(dataVersion = 2), settled = false))
    }
}

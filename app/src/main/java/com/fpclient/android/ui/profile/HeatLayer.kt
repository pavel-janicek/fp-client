package com.fpclient.android.ui.profile

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The web heatmap's rendering rules, ported so the app draws the same gradient blobs instead of
 * flat dots. Android-free (the [HeatmapOverlay] does the drawing), so every rule is unit-tested.
 *
 * Sources, all from the FitPub server repository:
 *  - `static/js/heatmap.js` `renderHeatmap()` / `calculateDynamicRadius()` — the options:
 *    log-scaled intensity, zoom-dependent radius, blur, `max`, `minOpacity`, the gradient;
 *  - Leaflet.heat 0.2.0 `HeatLayer._redraw()` — binning the points into cells of `r / 2`
 *    and the zoom weight `v`;
 *  - simpleheat (bundled in `leaflet-heat.js`) — a blurred circle per cell drawn with
 *    `globalAlpha = max(value / max, minOpacity)`, then every pixel recoloured from a
 *    256-entry palette **indexed by its accumulated alpha**, which keeps that alpha.
 */
internal object HeatLayer {

    /** `max` option: cell values are capped here and alpha is `value / MAX`. */
    const val MAX = 0.75

    /** `minOpacity` option: the faintest a drawn cell can be. */
    const val MIN_OPACITY = 0.25

    /** `maxZoom` option: at this zoom a point counts fully (`v = 1`). */
    const val MAX_ZOOM = 18.0

    /** The web's gradient stops: position → RGBA (alpha 0..1). */
    val GRADIENT: List<Pair<Double, Rgba>> = listOf(
        0.0 to Rgba(0, 0, 0, 0.0),
        0.1 to Rgba(0, 0, 139, 0.2),
        0.2 to Rgba(0, 0, 255, 0.35),
        0.3 to Rgba(0, 128, 255, 0.45),
        0.4 to Rgba(0, 255, 255, 0.55),
        0.5 to Rgba(0, 255, 0, 0.6),
        0.6 to Rgba(255, 255, 0, 0.65),
        0.7 to Rgba(255, 165, 0, 0.7),
        0.85 to Rgba(255, 69, 0, 0.8),
        1.0 to Rgba(255, 0, 0, 0.85),
    )

    data class Rgba(val r: Int, val g: Int, val b: Int, val a: Double)

    /** A binned cell to draw: position in pixels and its (capped) value. */
    data class Cell(val x: Float, val y: Float, val value: Double)

    /**
     * The web's per-point weight: `log(1 + intensity) / log(1 + maxIntensity)` in 0..1, or 0.5
     * when the maximum is unusable.
     */
    fun normalizedIntensity(intensity: Int, maxIntensity: Int?): Double {
        val logMax = ln(1.0 + (maxIntensity ?: 0))
        if (logMax <= 0.0) return 0.5
        return (ln(1.0 + intensity) / logMax).coerceIn(0.0, 1.0)
    }

    /** `calculateDynamicRadius(zoom)`: bigger blobs zoomed out, smaller ones zoomed in (CSS px). */
    fun radiusFor(zoom: Double): Double = when {
        zoom <= 8 -> 25.0
        zoom <= 12 -> 20 - (zoom - 8)
        zoom <= 15 -> 16 - (zoom - 12) * 2
        else -> max(6.0, 10 - (zoom - 15) * 2)
    }

    /** The web's blur for a radius: `max(4, radius * 0.4)`. */
    fun blurFor(radius: Double): Double = max(4.0, radius * 0.4)

    /** Leaflet.heat's zoom weight `v = 1 / 2^clamp(maxZoom − zoom, 0, 12)`. */
    fun zoomWeight(zoom: Double): Double = 1.0 / 2.0.pow(max(0.0, min(MAX_ZOOM - zoom, 12.0)))

    /**
     * Leaflet.heat `_redraw` binning. Points (screen pixels with weights) inside the view grown
     * by [extent] are grouped into square cells of `extent / 2`; a cell sits at the
     * weight-averaged position of its points and holds their summed `weight × v`, capped at
     * [MAX]. [scrollX]/[scrollY] anchor the grid to the map (not the screen), so the cells do
     * not regroup while panning.
     */
    fun bin(
        xs: FloatArray,
        ys: FloatArray,
        weights: DoubleArray,
        count: Int,
        width: Int,
        height: Int,
        extent: Double,
        v: Double,
        scrollX: Long = 0,
        scrollY: Long = 0,
    ): List<Cell> {
        val cellSize = extent / 2.0
        if (cellSize <= 0.0) return emptyList()
        val offsetX = positiveRemainder(scrollX.toDouble(), cellSize)
        val offsetY = positiveRemainder(scrollY.toDouble(), cellSize)
        val cells = HashMap<Long, DoubleArray>()
        for (i in 0 until count) {
            val x = xs[i].toDouble()
            val y = ys[i].toDouble()
            if (x < -extent || y < -extent || x > width + extent || y > height + extent) continue
            val k = weights[i] * v
            val cx = floor((x + offsetX) / cellSize).toLong()
            val cy = floor((y + offsetY) / cellSize).toLong()
            val key = (cx shl 32) xor (cy and 0xffffffffL)
            val cell = cells[key]
            if (cell == null) {
                cells[key] = doubleArrayOf(x, y, k)
            } else {
                val total = cell[2] + k
                if (total > 0.0) {
                    cell[0] = (cell[0] * cell[2] + x * k) / total
                    cell[1] = (cell[1] * cell[2] + y * k) / total
                }
                cell[2] = total
            }
        }
        return cells.values.map { Cell(it[0].roundToInt().toFloat(), it[1].roundToInt().toFloat(), min(it[2], MAX)) }
    }

    private fun positiveRemainder(value: Double, divisor: Double): Double =
        (value % divisor).let { if (it < 0) it + divisor else it }

    /** simpleheat's `globalAlpha` for a cell: `max(value / MAX, MIN_OPACITY)`, at most 1. */
    fun cellAlpha(value: Double): Double = min(1.0, max(value / MAX, MIN_OPACITY))

    /**
     * The 256-entry colour ramp simpleheat samples from a 1×256 linear gradient (pixel `i` at
     * position `(i + 0.5) / 256`). Canvas gradients interpolate in premultiplied space, so a
     * transparent stop does not darken its neighbour. Entries are `0xRRGGBB`; the alpha comes
     * from the drawn pixel, not from here.
     */
    val PALETTE: IntArray by lazy { buildPalette(GRADIENT) }

    fun buildPalette(stops: List<Pair<Double, Rgba>>): IntArray = IntArray(256) { i ->
        val pos = (i + 0.5) / 256.0
        val upper = stops.indexOfFirst { it.first >= pos }.let { if (it < 0) stops.lastIndex else it }
        val lower = (upper - 1).coerceAtLeast(0)
        val (p0, c0) = stops[lower]
        val (p1, c1) = stops[upper]
        val f = if (p1 > p0) ((pos - p0) / (p1 - p0)).coerceIn(0.0, 1.0) else 0.0
        val a = c0.a + (c1.a - c0.a) * f
        fun channel(v0: Int, v1: Int): Int {
            if (a <= 0.0) return 0
            val premultiplied = v0 * c0.a + (v1 * c1.a - v0 * c0.a) * f
            return (premultiplied / a).roundToInt().coerceIn(0, 255)
        }
        (channel(c0.r, c1.r) shl 16) or (channel(c0.g, c1.g) shl 8) or channel(c0.b, c1.b)
    }

    /**
     * simpleheat `_colorize`: every pixel with alpha `a > 0` takes the palette colour at index
     * `a` and keeps its own alpha. [pixels] are non-premultiplied ARGB, as `Bitmap.getPixels`.
     */
    fun colorize(pixels: IntArray, palette: IntArray = PALETTE) {
        for (i in pixels.indices) {
            val alpha = pixels[i] ushr 24
            if (alpha != 0) pixels[i] = (alpha shl 24) or palette[alpha]
        }
    }
}

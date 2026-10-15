package com.fpclient.android.ui.profile

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.fpclient.android.data.dto.HeatmapPointDto
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * osmdroid overlay drawing the heatmap the way the web page does (Leaflet.heat + simpleheat,
 * rules in [HeatLayer]): a blurred circle per binned cell, alpha accumulated, then recoloured
 * through the web's gradient. Replaces the old `SimpleFastPointOverlay`, which drew every cell
 * as the same orange square and ignored intensity altogether.
 *
 * Radii are the web's CSS pixels taken as dp, which matches the web only because the map
 * scales its tiles to the display density (`isTilesScaledToDpi`), making a zoom level cover
 * the same ground as in a browser.
 *
 * The layer is rendered off-screen at [SCALE] (the blobs are blurred anyway, and recolouring is
 * per pixel) and only when the zoom, the scroll position, the size or the data changed.
 */
internal class HeatmapOverlay(private val density: Float) : Overlay() {

    private var lats = DoubleArray(0)
    private var lons = DoubleArray(0)
    private var weights = DoubleArray(0)
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private var dataSource: Pair<List<HeatmapPointDto>, Int?>? = null
    private var dataVersion = 0

    private var heat: Bitmap? = null
    private var pixels = IntArray(0)
    private var brush: Bitmap? = null
    private var brushKey: Pair<Float, Float>? = null
    private var renderedKey: List<Any>? = null

    private val geo = GeoPoint(0.0, 0.0)
    private val screen = Point()
    private val brushPaint = Paint()
    private val outPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val target = Rect()

    /** Sets the cells to draw; a no-op for the same list, so recompositions do not re-render. */
    fun setPoints(points: List<HeatmapPointDto>, maxIntensity: Int?) {
        if (dataSource?.first === points && dataSource?.second == maxIntensity) return
        dataSource = points to maxIntensity
        val valid = points.filter { it.latitude != null && it.longitude != null }
        lats = DoubleArray(valid.size) { valid[it].latitude!! }
        lons = DoubleArray(valid.size) { valid[it].longitude!! }
        // The web normalises each cell once, before Leaflet.heat sees it.
        weights = DoubleArray(valid.size) { HeatLayer.normalizedIntensity(valid[it].intensity, maxIntensity) }
        xs = FloatArray(valid.size)
        ys = FloatArray(valid.size)
        dataVersion++
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || lats.isEmpty()) return
        val width = mapView.width
        val height = mapView.height
        if (width == 0 || height == 0) return
        val zoom = mapView.zoomLevelDouble
        val key = listOf(zoom, mapView.mapScrollX, mapView.mapScrollY, width, height, dataVersion)
        if (key != renderedKey) {
            render(mapView, zoom, width, height)
            renderedKey = key
        }
        heat?.let {
            target.set(0, 0, width, height)
            canvas.drawBitmap(it, null, target, outPaint)
        }
    }

    private fun render(mapView: MapView, zoom: Double, width: Int, height: Int) {
        val w = max(1, ceil(width * SCALE).toInt())
        val h = max(1, ceil(height * SCALE).toInt())
        val bitmap = heat?.takeIf { it.width == w && it.height == h }
            ?: createBitmap(w, h).also {
                heat?.recycle()
                heat = it
                pixels = IntArray(w * h)
            }
        bitmap.eraseColor(Color.TRANSPARENT)

        val cssRadius = HeatLayer.radiusFor(zoom)
        val radius = (cssRadius * density * SCALE).toFloat()
        val blur = (HeatLayer.blurFor(cssRadius) * density * SCALE).toFloat()
        val extent = radius + blur
        val stamp = brushFor(radius, blur)

        val projection = mapView.projection
        for (i in lats.indices) {
            geo.setCoords(lats[i], lons[i])
            projection.toPixels(geo, screen)
            xs[i] = screen.x * SCALE
            ys[i] = screen.y * SCALE
        }
        val cells = HeatLayer.bin(
            xs, ys, weights, lats.size, w, h,
            extent = extent.toDouble(),
            v = HeatLayer.zoomWeight(zoom),
            scrollX = (mapView.mapScrollX * SCALE).toLong(),
            scrollY = (mapView.mapScrollY * SCALE).toLong(),
        )
        if (cells.isEmpty()) return

        val canvas = Canvas(bitmap)
        for (cell in cells) {
            brushPaint.alpha = (HeatLayer.cellAlpha(cell.value) * 255).roundToInt()
            canvas.drawBitmap(stamp, cell.x - extent, cell.y - extent, brushPaint)
        }
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        HeatLayer.colorize(pixels)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * simpleheat's circle stamp: a disc of [radius] blurred by [blur], in a square of
     * `2 × (radius + blur)`. Canvas `shadowBlur = b` is a Gaussian of sigma `b / 2`; Skia turns
     * a BlurMaskFilter radius into sigma `0.57735 × radius + 0.5`, hence the conversion.
     */
    private fun brushFor(radius: Float, blur: Float): Bitmap {
        val cached = brush
        if (cached != null && brushKey == radius to blur) return cached
        val extent = radius + blur
        val size = max(1, ceil(extent * 2).toInt())
        val stamp = createBitmap(size, size)
        val sigma = blur / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(max(0.5f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(stamp).drawCircle(extent, extent, radius, paint)
        cached?.recycle()
        brush = stamp
        brushKey = radius to blur
        return stamp
    }

    override fun onDetach(mapView: MapView?) {
        heat?.recycle()
        heat = null
        brush?.recycle()
        brush = null
        super.onDetach(mapView)
    }

    private companion object {
        /** Off-screen resolution relative to the map. */
        const val SCALE = 0.5f
    }
}

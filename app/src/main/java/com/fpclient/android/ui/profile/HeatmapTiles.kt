package com.fpclient.android.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Loads one heatmap tile — `(z, x, y)` of the current revision — as PNG bytes, or null when
 * there is none to draw (see `UserRepository.heatmapTile`).
 */
typealias HeatmapTileLoader = suspend (z: Int, x: Int, y: Int) -> ByteArray?

/**
 * The heatmap FitPub 1.4 (#556) draws itself: 256 px PNG tiles of the owner's routes, over
 * the OSM base map, like the web page's second Leaflet tile layer.
 *
 * The tiles need the signed-in session, so they come through the app's own HTTP client
 * ([load]) rather than osmdroid's downloader, whose only way to add a header is a global
 * setting that would send it to the OSM tile servers too. They are kept in memory only: the
 * server marks them `private, no-store`, and osmdroid's provider array has no disk cache
 * unless one is given.
 *
 * A new revision needs a new overlay: osmdroid caches tiles by index alone.
 */
internal object HeatmapTiles {

    /** The zooms the server draws (it answers 404 outside 1–18). */
    private const val MIN_ZOOM = 1
    private const val MAX_ZOOM = 18
    private const val TILE_SIZE = 256

    /** A few tiles at a time, as a browser loads the web page's layer. */
    private const val THREADS = 4
    private const val PENDING = 40

    private const val RETRY_AFTER_FAILURE_MS = 30_000L

    fun overlay(context: Context, load: HeatmapTileLoader): TilesOverlay {
        // Names the tiles in osmdroid's memory cache; no URL is ever built from it.
        val source = XYTileSource("fitpub-heatmap", MIN_ZOOM, MAX_ZOOM, TILE_SIZE, ".png", arrayOf("https://fitpub.invalid/"))
        val module = Module(context, load)
        val provider = MapTileProviderArray(source, null, arrayOf<MapTileModuleProviderBase>(module))
        return TilesOverlay(provider, context, false, false).apply {
            // Untouched areas are plain map, not osmdroid's grey loading grid.
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }

    /**
     * How many tile pixels a route is widened by at [zoom]. The server draws every route one
     * pixel wide at any zoom, which is fine on a desktop zoomed to a city but leaves a whole
     * country's worth of routes as a few faint pixels on a phone; the cell heatmap the app drew
     * before was readable from the country view. Widest when zoomed out, untouched from street
     * level on, where the server's lines are what the web shows.
     */
    internal fun thickenRadius(zoom: Int): Int = when {
        zoom <= 8 -> 3
        zoom <= 11 -> 2
        zoom <= 13 -> 1
        else -> 0
    }

    /** [tile] with its routes widened by [thickenRadius] (a dilation: offset copies in a disk). */
    private fun thicken(tile: Bitmap, zoom: Int): Bitmap {
        val radius = thickenRadius(zoom)
        if (radius == 0) return tile
        val out = createBitmap(tile.width, tile.height)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue
                canvas.drawBitmap(tile, dx.toFloat(), dy.toFloat(), paint)
            }
        }
        tile.recycle()
        return out
    }

    private class Module(
        context: Context,
        private val load: HeatmapTileLoader,
    ) : MapTileModuleProviderBase(THREADS, PENDING) {
        private val resources = context.applicationContext.resources

        /**
         * When each tile last failed. osmdroid asks for a missing tile again on every redraw,
         * which turned one failing tile into ten requests a second against the server's tile
         * rate limit; a failed tile now waits [RETRY_AFTER_FAILURE_MS] before the next try.
         */
        private val failedAt = ConcurrentHashMap<Long, Long>()

        override fun getName() = "FitPub heatmap"
        override fun getThreadGroupName() = "fitpub-heatmap"
        override fun getUsesDataConnection() = true
        override fun getMinimumZoomLevel() = MIN_ZOOM
        override fun getMaximumZoomLevel() = MAX_ZOOM
        // One fixed source: the tiles always come from [load].
        override fun setTileSource(tileSource: ITileSource) = Unit

        override fun getTileLoader(): TileLoader = object : TileLoader() {
            // Runs on the module's own worker threads, so blocking on the request is fine.
            override fun loadTile(pMapTileIndex: Long): Drawable? {
                val now = SystemClock.elapsedRealtime()
                failedAt[pMapTileIndex]?.let { if (now - it < RETRY_AFTER_FAILURE_MS) return null }
                val bytes = runBlocking {
                    load(MapTileIndex.getZoom(pMapTileIndex), MapTileIndex.getX(pMapTileIndex), MapTileIndex.getY(pMapTileIndex))
                }
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.let { thicken(it, zoom) }
                if (bitmap == null) {
                    failedAt[pMapTileIndex] = now
                    return null
                }
                failedAt.remove(pMapTileIndex)
                return BitmapDrawable(resources, bitmap)
            }
        }
    }
}

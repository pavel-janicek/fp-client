package com.fpclient.android.ui.profile

import androidx.compose.foundation.layout.Spacer
import com.fpclient.android.data.dto.HeatmapMapper
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.floor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.doOnLayout
import com.fpclient.android.data.dto.HeatmapBoundsDto
import com.fpclient.android.data.dto.HeatmapPointDto
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import com.fpclient.android.data.dto.HeatmapResponse
import com.fpclient.android.data.dto.HeatmapViewport
import androidx.compose.runtime.rememberUpdatedState
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent

/**
 * Activity-location heatmap over an OSM base map, drawn like the web heatmap page — gradient
 * blobs from [HeatmapOverlay], cells reloaded for the visible area after every pan/zoom.
 *
 * The card can be maximized: the "Enlarge heatmap" button opens the same map full-screen
 * (system Back closes it), just like the activity-detail track map.
 */
@Composable
fun HeatmapCard(
    points: List<HeatmapPointDto>,
    bounds: HeatmapBoundsDto?,
    modifier: Modifier = Modifier,
    generationStatus: String? = null,
    hasHeatmap: Boolean? = null,
    maxIntensity: Int? = null,
    loadViewport: (suspend (HeatmapViewport) -> HeatmapResponse?)? = null,
) {
    // Once per point list: the result keys the map's effects, which compare it by content.
    val valid = remember(points) { points.filter { it.latitude != null && it.longitude != null } }
    // Like the web heatmap page: the generation status is always shown, the map only once a
    // heatmap exists. Without a known status (older server) the card keeps its old rule —
    // shown only when there are points to draw.
    val status = HeatmapMapper.statusMessage(generationStatus)
    if (valid.isEmpty() && status == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "Activity heatmap",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp),
        )
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (generationStatus == "FAILED") {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp),
            )
        }
        if (valid.isEmpty() || hasHeatmap == false) {
            if (generationStatus == "NO_HEATMAP") {
                Text(
                    "No GPS tracks yet. Upload activities with GPS tracks to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
            return@Card
        }
        Box {
            HeatmapCanvas(
                points = valid,
                bounds = bounds,
                maxIntensity = maxIntensity,
                loadViewport = loadViewport,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            FilledTonalIconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Enlarge heatmap")
            }
        }
    }

    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            // Full-width dialog: the map fills the whole window, system Back also closes it.
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    HeatmapCanvas(
                        points = valid,
                        bounds = bounds,
                        maxIntensity = maxIntensity,
                        loadViewport = loadViewport,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Same translucent tonal button, same top-right corner as the
                    // "Enlarge heatmap" button — the familiar affordance closes the view.
                    FilledTonalIconButton(
                        onClick = { expanded = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close enlarged heatmap")
                    }
                }
            }
        }
    }
}

/**
 * The osmdroid heatmap itself: draws the point overlay and frames the bounding box.
 * Used both for the small inline card on the Me tab and for the enlarged full-screen
 * version — only the size differs.
 *
 * Navigation is the point of this composable: one finger held down and moved pans the
 * map, two fingers pinch-zoom and move it. The single-finger case needs help because the
 * card sits inside the Me tab's `LazyColumn`, which would otherwise claim the drag and
 * scroll the list instead — the touch listener disallows the parent's interception on
 * touch-down, the same pattern the privacy-zone editor and the record mini-map use.
 */
@Composable
private fun HeatmapCanvas(
    points: List<HeatmapPointDto>,
    bounds: HeatmapBoundsDto?,
    maxIntensity: Int?,
    loadViewport: (suspend (HeatmapViewport) -> HeatmapResponse?)?,
    modifier: Modifier = Modifier,
) {
    // Frame the data once per point set, not on every recomposition: re-running
    // zoomToBoundingBox from the update block would snap the camera back under the
    // user's finger mid-pan (scrolling the list recomposes the card). Keyed on the initial
    // (full) data only, so the per-viewport reloads below never re-frame the map.
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(points, bounds) { fitted = false }

    // Like the web page: after the map settles (500 ms, its `moveend` debounce) ask for the
    // cells of the visible area at this zoom, which the server aggregates coarser when zoomed
    // out. Each new viewport cancels the previous request, so only the newest one lands; a
    // failure (e.g. the endpoint's 429 rate limit) keeps the cells already shown.
    var viewport by remember { mutableStateOf<HeatmapViewport?>(null) }
    var viewportData by remember(points) { mutableStateOf<HeatmapResponse?>(null) }
    val currentLoad by rememberUpdatedState(loadViewport)
    LaunchedEffect(viewport) {
        val area = viewport ?: return@LaunchedEffect
        currentLoad?.invoke(area)?.let { viewportData = it }
    }
    val shownPoints = viewportData?.points ?: points
    val shownMax = viewportData?.maxIntensity ?: maxIntensity

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                // Tiles at 256 dp, as in a browser: labels stay readable, and a zoom level then
                // covers the same ground as on the web, which the heat radii (CSS px → dp) and
                // the server's zoom-dependent grid both assume.
                isTilesScaledToDpi = true
                setMultiTouchControls(true)
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
                overlays.add(HeatmapOverlay(context.resources.displayMetrics.density))
                addMapListener(
                    DelayedMapListener(
                        object : MapListener {
                            override fun onScroll(event: ScrollEvent?) = report(this@apply)
                            override fun onZoom(event: ZoomEvent?) = report(this@apply)
                            private fun report(map: MapView): Boolean {
                                val box = map.boundingBox
                                // The web skips a degenerate (single-point) box the same way.
                                if (box.lonWest != box.lonEast && box.latSouth != box.latNorth) {
                                    viewport = HeatmapViewport(
                                        minLon = box.lonWest,
                                        minLat = box.latSouth,
                                        maxLon = box.lonEast,
                                        maxLat = box.latNorth,
                                        zoom = map.zoomLevelDouble,
                                    )
                                }
                                return false
                            }
                        },
                        VIEWPORT_RELOAD_DELAY_MS,
                    ),
                )
            }
        },
        update = { map ->
            map.overlays.filterIsInstance<HeatmapOverlay>().forEach { it.setPoints(shownPoints, shownMax) }
            if (!fitted) {
                val box = if (bounds != null &&
                    bounds.minLatitude != null && bounds.minLongitude != null &&
                    bounds.maxLatitude != null && bounds.maxLongitude != null
                ) {
                    BoundingBox(bounds.maxLatitude!!, bounds.maxLongitude!!, bounds.minLatitude!!, bounds.minLongitude!!)
                } else {
                    BoundingBox.fromGeoPoints(points.map { GeoPoint(it.latitude!!, it.longitude!!) })
                }
                // Only once the map has its size: a post can run before the first layout (the
                // list composes the card ahead of placing it, e.g. when scrolling back up), and
                // osmdroid turns the then-negative room (0 − 2 × border) into a NaN zoom on
                // which `Projection.getCloserPixel` loops forever — the Me tab ANR of 3.3.0.
                map.post {
                    map.doOnLayout {
                        // Leaflet's fitBounds (what the web uses) snaps to a whole zoom level
                        // (zoomSnap 1), which is what leaves room around the data; osmdroid fits
                        // a fractional zoom edge to edge. So: fit with a border the size of a
                        // blob (radius + blur at the widest), then round the zoom down like
                        // Leaflet.
                        val border = HeatmapFraming.border(
                            box,
                            map.width,
                            map.height,
                            (FIT_BORDER_DP * map.resources.displayMetrics.density).toInt(),
                        ) ?: return@doOnLayout
                        map.zoomToBoundingBox(box, false, border)
                        map.controller.setZoom(floor(map.zoomLevelDouble))
                    }
                }
                fitted = true
            }
            map.invalidate()
        },
        onRelease = { map -> map.onDetach() },
        // osmdroid draws its tiles past its own bounds when the parent does not clip, which
        // painted the map over the card's title and status line — and the heat layer, drawn
        // within the bounds only, then looked cut off along the map's real edge.
        modifier = modifier.clipToBounds(),
    )
}

/** Guards for `MapView.zoomToBoundingBox`, which hangs the main thread on a NaN zoom. */
internal object HeatmapFraming {

    /**
     * The border to fit [box] into a [width] × [height] map with: [desired], shrunk so that at
     * least one pixel is left for the data on both axes. Null when there is nothing safe to fit —
     * a map without a size yet, or a box with non-finite coordinates — since either makes
     * osmdroid's `getBoundingBoxZoom` return NaN.
     */
    fun border(box: BoundingBox, width: Int, height: Int, desired: Int): Int? {
        if (width <= 0 || height <= 0) return null
        val coordinates = listOf(box.latNorth, box.latSouth, box.lonEast, box.lonWest)
        if (coordinates.any { !it.isFinite() }) return null
        return minOf(desired, (width - 1) / 2, (height - 1) / 2).coerceAtLeast(0)
    }
}

/** Room kept around the data when framing it: the widest blob, radius 25 + blur 10 (dp). */
private const val FIT_BORDER_DP = 35

/** The web page's `moveend` debounce before reloading the visible area. */
private const val VIEWPORT_RELOAD_DELAY_MS = 500L
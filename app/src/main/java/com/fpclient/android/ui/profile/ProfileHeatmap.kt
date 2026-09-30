package com.fpclient.android.ui.profile

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fpclient.android.data.dto.HeatmapBoundsDto
import com.fpclient.android.data.dto.HeatmapPointDto
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlay
import org.osmdroid.views.overlay.simplefastpoint.SimpleFastPointOverlayOptions
import org.osmdroid.views.overlay.simplefastpoint.SimplePointTheme

/**
 * Activity-location heatmap rendered as a fast point overlay over an OSM base map,
 * mirroring the web app's profile heatmap. Shown only when the server returns points.
 *
 * The card can be maximized: the "Enlarge heatmap" button opens the same map full-screen
 * (system Back closes it), just like the activity-detail track map.
 */
@Composable
fun HeatmapCard(
    points: List<HeatmapPointDto>,
    bounds: HeatmapBoundsDto?,
    modifier: Modifier = Modifier,
) {
    val valid = points.filter { it.latitude != null && it.longitude != null }
    if (valid.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "Activity heatmap",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp),
        )
        Box {
            HeatmapCanvas(
                points = valid,
                bounds = bounds,
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
                    HeatmapCanvas(points = valid, bounds = bounds, modifier = Modifier.fillMaxSize())
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
    modifier: Modifier = Modifier,
) {
    // Frame the data once per point set, not on every recomposition: re-running
    // zoomToBoundingBox from the update block would snap the camera back under the
    // user's finger mid-pan (scrolling the list recomposes the card).
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(points, bounds) { fitted = false }

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
            }
        },
        update = { map ->
            map.overlays.removeAll { it is SimpleFastPointOverlay }
            val geoPoints = points.map { GeoPoint(it.latitude!!, it.longitude!!) }
            val theme = SimplePointTheme(geoPoints.toList(), false)
            val options = SimpleFastPointOverlayOptions.getDefaultStyle()
            map.overlays.add(SimpleFastPointOverlay(theme, options))
            if (!fitted) {
                val box = if (bounds != null &&
                    bounds.minLatitude != null && bounds.minLongitude != null &&
                    bounds.maxLatitude != null && bounds.maxLongitude != null
                ) {
                    BoundingBox(bounds.maxLatitude!!, bounds.maxLongitude!!, bounds.minLatitude!!, bounds.minLongitude!!)
                } else {
                    BoundingBox.fromGeoPoints(geoPoints)
                }
                map.post {
                    map.zoomToBoundingBox(box.increaseByScale(1.15f), false)
                }
                fitted = true
            }
            map.invalidate()
        },
        onRelease = { map -> map.onDetach() },
        modifier = modifier,
    )
}
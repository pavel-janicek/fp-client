package com.fpclient.android.ui.record

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fpclient.android.R
import com.fpclient.android.recording.TrackPoint
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** The mini-map of the Record flow (Iteration 8c + 8d): draws the accepted fixes as a
 * polyline with the most recent one highlighted by a position dot, and follows the dot
 * by default. Panning turns following off (it would otherwise fight the user); the
 * floating recenter button turns it back on. Rebuilt per fix emission — at the 2 s fix
 * cadence that is cheap, and it sidesteps incremental overlay bookkeeping.
 *
 * [fitTrack] switches it to the post-workout review use (Iteration 8d): the camera zooms
 * out to the whole track once instead of following the last fix, which is what the summary
 * wants when the recording is already complete.
 */
@Composable
fun LiveTrackMap(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    fitTrack: Boolean = false,
    interactive: Boolean = !fitTrack,
) {
    var follow by remember { mutableStateOf(true) }
    var fitted by remember { mutableStateOf(false) }

    // Reset the fitted flag when the point count changes so the map re-fits on
    // every meaningful change (new points during recording, or re-entering the
    // summary screen after leaving it).
    LaunchedEffect(points.size) {
        fitted = false
    }

    Box(modifier.clipToBounds()) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(interactive)
                    isClickable = interactive
                    isFocusable = interactive
                    if (!interactive) {
                        setOnTouchListener { _, _ -> true }
                    } else {
                        setOnTouchListener { v, event ->
                            if (event.action == MotionEvent.ACTION_DOWN) {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            false
                        }
                    }
                    controller.setZoom(16.0)
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            follow = false
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean = true
                    })
                }
            },
            update = { map ->
                map.overlays.removeAll { it is Polyline || it is Marker }
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                if (geoPoints.isNotEmpty()) {
                    map.overlays.add(Polyline(map).apply { setPoints(geoPoints) })
                    Marker(map).apply {
                        position = geoPoints.last()
                        icon = ContextCompat.getDrawable(map.context, R.drawable.ic_record)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }.also { map.overlays.add(it) }
                    when {
                        // During recording, follow the live position: pan the map to the
                        // latest GPS fix so the user always sees where they are. Panning the
                        // map turns follow off (it would otherwise fight the user).
                        follow -> map.controller.animateTo(geoPoints.last())
                        // Fit the whole track when requested (post-workout summary, or the
                        // pre-start preview with a single point). The fitted flag ensures this
                        // only runs once per point set, so re-entering the summary screen
                        // re-fits instead of showing a stale view.
                        //
                        // A minimum span is enforced so that a lone fix still lands at a usable
                        // zoom instead of jumping to a degenerate location near the default
                        // (0,0) origin.
                        fitTrack && !fitted -> {
                            map.post {
                                val center = geoPoints.last()
                                val span = 0.004 // ~440 m — enough to see context around a point
                                map.controller.setCenter(center)
                                map.controller.zoomToSpan(span, span)
                            }
                            fitted = true
                        }
                        // Already fitted or fitTrack disabled: nothing to do.
                        else -> Unit
                    }
                }
                map.invalidate()
            },
            onRelease = { map -> map.onDetach() },
            modifier = Modifier.fillMaxSize(),
        )
        if (!follow && !fitTrack && interactive) {
            FilledTonalIconButton(
                onClick = { follow = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Recenter on live position")
            }
        }
    }
}

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
                        fitTrack && !fitted -> {
                            map.post {
                                map.zoomToBoundingBox(
                                    BoundingBox.fromGeoPoints(geoPoints).increaseByScale(1.2f),
                                    false,
                                )
                            }
                            fitted = true
                        }
                        fitTrack -> Unit
                        follow -> map.controller.animateTo(geoPoints.last())
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

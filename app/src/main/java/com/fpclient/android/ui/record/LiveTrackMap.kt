package com.fpclient.android.ui.record

import android.view.MotionEvent
import android.view.ViewConfiguration
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
import kotlin.math.abs
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
 * Following is driven by *touches*, not by osmdroid's map listeners: every programmatic
 * camera move goes through `MapView.setExpectedCenter`, which also dispatches the
 * listeners' scroll events — a scroll listener therefore switched following off on the
 * map's own `animateTo`, and the camera stayed wherever the screen was opened (the
 * pre-start mini-map kept showing the previous town even after a fresh fix arrived).
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
    // Following is the default (live recording, pre-start warm-up). The fit-the-whole-track
    // use starts with it off, so the post-workout summary zooms out to the complete track
    // instead of trailing its last point.
    var follow by remember { mutableStateOf(!fitTrack) }
    var fitted by remember { mutableStateOf(false) }
    // The fix the camera was last sent to. Recompositions (the 500 ms ticker, stats) rerun
    // the AndroidView update block; re-aiming the camera at the same fix every time would
    // restart its animation needlessly.
    var lastCentered by remember { mutableStateOf<TrackPoint?>(null) }
    // Touch-down coordinates of the current gesture (plain holder — writing Compose state
    // per touch move would recompose the whole map on every finger movement).
    val downAt = remember { FloatArray(2) }

    // Reset the fitted flag whenever the newest fix changes so a fit runs for every new
    // point set. Keyed on the point itself, not on `points.size`: the pre-start preview
    // carries exactly one point, so a size-based key never changed and the camera was
    // never moved to the new fix.
    LaunchedEffect(points.lastOrNull()) {
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
                        // View-only (summary, pre-start warm-up): swallow touches; the
                        // camera keeps following the newest fix.
                        setOnTouchListener { _, _ -> true }
                    } else {
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    downAt[0] = event.x
                                    downAt[1] = event.y
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val slop = ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()
                                    if (isUserPan(
                                            event.pointerCount,
                                            event.x - downAt[0],
                                            event.y - downAt[1],
                                            slop,
                                        )
                                    ) {
                                        follow = false
                                    }
                                }
                            }
                            false
                        }
                    }
                    controller.setZoom(16.0)
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
                    val newest = points.last()
                    when {
                        // Recording and the pre-start warm-up: keep the newest fix in the
                        // middle of the view so the user always sees where they are. Panning
                        // the map turns following off (it would otherwise fight the user).
                        // Skipped when the camera was already sent to this very fix.
                        follow && newest != lastCentered -> {
                            lastCentered = newest
                            map.controller.animateTo(geoPoints.last())
                        }
                        // Fit the whole track when requested (post-workout summary). The
                        // fitted flag ensures this only runs once per point set, so
                        // re-entering the summary screen re-fits instead of showing a stale
                        // view.
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
                onClick = {
                    // Forget the last camera target so the next update re-centers even when
                    // no new fix arrived while the user was panning around.
                    lastCentered = null
                    follow = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Recenter on live position")
            }
        }
    }
}

/**
 * True when a touch gesture means the user took the map over — a single-finger drag past
 * [touchSlop]. Only then may following be switched off: a tap or an unintended two-finger
 * pinch must not, and neither may the map's own programmatic camera moves (which is why
 * following is wired to touch events instead of osmdroid's scroll events).
 */
internal fun isUserPan(pointerCount: Int, dx: Float, dy: Float, touchSlop: Float): Boolean =
    pointerCount == 1 && (abs(dx) > touchSlop || abs(dy) > touchSlop)

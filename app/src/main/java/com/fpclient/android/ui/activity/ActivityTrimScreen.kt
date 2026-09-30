package com.fpclient.android.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.ActivityTrimPointDto
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format
import com.fpclient.android.util.TrimPreview
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

/**
 * Trim workspace for one activity: pick the range of the **original** GPS track to keep.
 *
 * There is no separate apply endpoint — saving is an ordinary
 * `PUT /api/web/activities/{id}` carrying `trim: {startIndex, endIndex}`, and the server
 * rebuilds the activity from the retained points (this screen's own `GET …/trim` supplies
 * the original track plus the currently stored range). The figures shown before saving come
 * from [TrimPreview], a port of the server's own distance/elevation rules, so the preview
 * matches what the instance will store; speed metrics, timezone and start location are
 * recalculated by the server only, so the applied card shows those from the update response.
 *
 * Server refusals (manual activity, no original file, fewer than three points, `start >= end`)
 * are shown verbatim: the messages are written for a person, and the web editor shows them
 * the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTrimScreen(
    activityId: String,
    container: AppContainer,
    appViewModel: AppViewModel,
    onBack: () -> Unit,
) {
    // Scoped to *this* back-stack entry, so — unlike the detail screen's copy of the same
    // ViewModel — it starts empty: the activity itself (whose metadata travels back in the
    // PUT) and the trim workspace are both loaded here.
    val vm: ActivityDetailViewModel = viewModel(factory = ActivityDetailViewModel.factory(container, appViewModel))
    LaunchedEffect(activityId) {
        vm.load(activityId)
        vm.loadTrimData(activityId)
    }
    val ui by vm.ui.collectAsState()
    val trim by vm.trim.collectAsState()
    val unitSystem by appViewModel.unitSystem.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.activity?.title?.takeIf { it.isNotBlank() } ?: "Trim route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading || trim.loading -> LoadingIndicator(Modifier.padding(padding))
            trim.data == null -> ErrorState(
                // The instance's own explanation of why this track cannot be trimmed, as-is.
                message = trim.error ?: ui.error,
                onRetry = {
                    vm.load(activityId)
                    vm.loadTrimData(activityId)
                },
                modifier = Modifier.padding(padding),
            )
            else -> TrimWorkspace(
                activityId = activityId,
                vm = vm,
                trim = trim,
                unitSystem = unitSystem,
                modifier = Modifier.padding(padding),
            )
        }
    }
}


@Composable
private fun TrimWorkspace(
    activityId: String,
    vm: ActivityDetailViewModel,
    trim: ActivityDetailViewModel.TrimUiState,
    unitSystem: String,
    modifier: Modifier = Modifier,
) {
    val points = trim.points
    // The server refuses to build a workspace with fewer than three points; `data != null`
    // already implies we have some, but an empty list would make the sliders meaningless.
    if (points.isEmpty()) return
    val baseTime = remember(points) {
        points.firstOrNull()?.timestamp?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TrimMap(
            points = points,
            start = trim.previewStartIndex,
            end = trim.previewEndIndex,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // A trim can fail *after* a successful load (stale range, server refusal); the
            // message is shown verbatim rather than wrapped in app wording.
            item {
                trim.error?.let { ServerMessageCard(it, Modifier.padding(horizontal = 16.dp)) }
            }
            item { trim.applied?.let { AppliedCard(it, unitSystem) } }
            item { StoredRangeHint(trim) }
            item {
                // Each handle's range stops one point short of the other, so the selection can
                // never invert — the invariant the server validates (`start < end`) is kept by
                // construction, and the thumb simply hits a wall instead of failing after save.
                TrimHandle(
                    label = "Keep from",
                    value = trim.startIndex,
                    from = 0,
                    until = (trim.endIndex - 1).coerceAtLeast(0),
                    points = points,
                    baseTime = baseTime,
                    unitSystem = unitSystem,
                    onValueChange = vm::setTrimStart,
                )
            }
            item {
                TrimHandle(
                    label = "Keep until",
                    value = trim.endIndex,
                    from = (trim.startIndex + 1).coerceAtMost(points.lastIndex),
                    until = points.lastIndex,
                    points = points,
                    baseTime = baseTime,
                    unitSystem = unitSystem,
                    onValueChange = vm::setTrimEnd,
                )
            }
            item { PreviewCard(trim, unitSystem) }
        }
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = vm::trimToOriginal,
                    enabled = !trim.selectedRangeIsOriginal && !trim.saving,
                ) {
                    Text("Whole track")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { vm.applyTrim(activityId) },
                    enabled = trim.canApply,
                ) {
                    if (trim.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Applying…")
                    } else {
                        Text("Apply trim")
                    }
                }
            }
        }
    }
}

/** One cut end of the selection: label, current position in the original track, slider. */
@Composable
private fun TrimHandle(
    label: String,
    value: Int,
    from: Int,
    until: Int,
    points: List<ActivityTrimPointDto>,
    baseTime: Instant?,
    unitSystem: String,
    onValueChange: (Int) -> Unit,
) {
    // `steps` = selectable positions minus two (the range ends are always selectable);
    // one position (both ends pinned, possible with two points) needs 0, not -1.
    val steps = (until - from - 1).coerceAtLeast(0)
    val current = value.coerceIn(from, until)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                cutLabel(points.getOrNull(current), baseTime, unitSystem),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = current.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(from, until)) },
            valueRange = from.toFloat()..until.toFloat(),
            steps = steps,
        )
    }
}

/**
 * Where a cut falls in the *original* track: elapsed time from the first point plus the
 * parser's cumulative distance. Both are position aids for the cut itself — the totals the
 * trim produces are recomputed by [TrimPreview], never by subtracting these values.
 */
private fun cutLabel(point: ActivityTrimPointDto?, baseTime: Instant?, unitSystem: String): String {
    point ?: return "—"
    val elapsed = baseTime
        ?.takeIf { !point.timestamp.isNullOrBlank() }
        ?.let { origin ->
            runCatching { Instant.parse(point.timestamp) }.getOrNull()
                ?.let { Format.duration(Duration.between(origin, it).seconds) }
        }
    val distance = point.distance?.let { Format.distance(it, unitSystem) }
    return listOfNotNull(elapsed, distance).joinToString(" · ").ifEmpty { "—" }
}

@Composable
private fun PreviewCard(trim: ActivityDetailViewModel.TrimUiState, unitSystem: String) {
    val preview = trim.preview
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                "After trimming",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            when {
                preview == null -> Text(
                    "Calculating…",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preview.retainedPoints == 0 -> Text(
                    "Select at least two points.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    StatRow(
                        listOf(
                            "Points" to preview.retainedPoints.toString(),
                            "Distance" to Format.distance(preview.distanceMeters, unitSystem),
                            "Time" to Format.duration(preview.durationSeconds),
                        ),
                    )
                    StatRow(
                        listOf(
                            "Climb" to Format.elevation(preview.elevationGainMeters, unitSystem),
                            "Descent" to Format.elevation(preview.elevationLossMeters, unitSystem),
                        ),
                    )
                    Text(
                        "Distance, time and elevation use the same rules the server applies on " +
                            "save; speeds, time zone and start point are recalculated there.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}


/**
 * What the instance stored for the last applied trim. Unlike the preview, these figures come
 * from the `PUT` response — they include whatever the server's speed policy and timezone
 * recalculation produced, so they are authoritative.
 */
@Composable
private fun AppliedCard(applied: ActivityDetailViewModel.AppliedTrim, unitSystem: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                "Trim applied — the instance now reports",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            StatRow(
                listOf(
                    "Distance" to Format.distance(applied.totalDistance, unitSystem),
                    "Time" to Format.duration(applied.totalDurationSeconds),
                    "Climb" to Format.elevation(applied.elevationGain, unitSystem),
                ),
            )
            Text(
                "Kept original points ${applied.startIndex}–${applied.endIndex}.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun StoredRangeHint(trim: ActivityDetailViewModel.TrimUiState) {
    val data = trim.data ?: return
    val text = if (trim.selectedRangeIsStored) {
        "This selection is exactly what the activity currently stores."
    } else {
        "Currently kept: original points ${data.currentStartIndex}–${data.currentEndIndex} " +
            "of ${data.points.lastIndex}."
    }
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The server's refusal text, verbatim — same as the web editor shows it. */
@Composable
private fun ServerMessageCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}


/**
 * The original track, dimmed, with the retained range drawn over it in the accent colour.
 *
 * The base polyline is built once per track and the viewport is fitted once; the highlight is
 * only replaced when the *settled* selection changes (the preview debounce upstream), so
 * dragging a slider never redraws tens of thousands of points on the UI thread.
 */
@Composable
private fun TrimMap(
    points: List<ActivityTrimPointDto>,
    start: Int,
    end: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
        }
    }
    // Captured here so the effects can read them as plain values (MaterialTheme is only
    // readable during composition).
    val wholeColor = MaterialTheme.colorScheme.outlineVariant
    val keptColor = MaterialTheme.colorScheme.primary
    val wholeTrack = remember(map) {
        Polyline(map).apply {
            // Same opt-out as MapCanvas: the route is a picture, not a control — a polyline
            // without this listener claims every tap near the line and swallows it.
            setOnClickListener { _, _, _ -> false }
            outlinePaint.strokeWidth = 6f
        }
    }
    val keptRange = remember(map) {
        Polyline(map).apply {
            setOnClickListener { _, _, _ -> false }
            outlinePaint.strokeWidth = 12f
        }
    }
    val geoPoints = remember(points) { points.map { GeoPoint(it.latitude, it.longitude) } }

    // Seed the base track (once per track) and fit the viewport to the whole route.
    LaunchedEffect(map, geoPoints) {
        wholeTrack.outlinePaint.color = wholeColor.toArgb()
        keptRange.outlinePaint.color = keptColor.toArgb()
        if (map.overlays.isEmpty()) {
            map.overlays.add(wholeTrack)
            map.overlays.add(keptRange)
        }
        wholeTrack.setPoints(geoPoints)
        if (geoPoints.isNotEmpty()) {
            val minLat = geoPoints.minOf { it.latitude }
            val maxLat = geoPoints.maxOf { it.latitude }
            val minLon = geoPoints.minOf { it.longitude }
            val maxLon = geoPoints.maxOf { it.longitude }
            map.controller.setCenter(GeoPoint((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0))
            map.controller.zoomToSpan(
                (maxLat - minLat).coerceAtLeast(0.002),
                (maxLon - minLon).coerceAtLeast(0.002),
            )
        }
        map.invalidate()
    }

    // Redraw only the kept-range overlay when the settled selection moves.
    LaunchedEffect(geoPoints, start, end) {
        if (geoPoints.isEmpty()) return@LaunchedEffect
        val from = start.coerceIn(0, geoPoints.lastIndex)
        val to = end.coerceIn(from, geoPoints.lastIndex)
        keptRange.setPoints(geoPoints.subList(from, to + 1))
        map.invalidate()
    }

    AndroidView(
        factory = { map },
        update = { },
        onRelease = { it.onDetach() },
        modifier = modifier,
    )
}


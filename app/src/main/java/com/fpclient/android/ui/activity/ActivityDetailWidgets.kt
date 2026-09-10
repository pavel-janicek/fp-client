package com.fpclient.android.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fpclient.android.data.dto.ReactionPalette
import com.fpclient.android.util.Format
import com.fpclient.android.util.TextLimits

@Composable
fun ReactionRow(activityId: String, viewModel: ActivityDetailViewModel, ui: ActivityDetailViewModel.UiState) {
    val activity = ui.activity ?: return
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Reactions", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            ReactionPalette.ALL.forEach { emoji ->
                val count = activity.reactionCounts?.get(emoji) ?: 0
                FilterChip(
                    selected = activity.currentUserReaction == emoji,
                    onClick = { viewModel.react(activityId, emoji) },
                    label = { Text(if (count > 0) "$emoji $count" else emoji) },
                )
            }
        }
    }
}

@Composable
fun CommentComposer(
    activityId: String,
    viewModel: ActivityDetailViewModel,
    onFocused: () -> Unit = {},
) {
    var commentText by rememberSaveable { mutableStateOf("") }
    val comments = viewModel.ui.collectAsState().value.comments
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Comments (${comments.size})", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it.take(TextLimits.COMMENT) },
            label = { Text("Add a comment") },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            viewModel.addComment(activityId, commentText.trim())
                            commentText = ""
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .onFocusChanged { if (it.isFocused) onFocused() },
        )
        Spacer(Modifier.height(8.dp))
        comments.forEach { c ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.displayName ?: "Athlete", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            Format.relative(c.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (c.canDelete && c.id != null) {
                            IconButton(onClick = { viewModel.deleteComment(activityId, c.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete comment")
                            }
                        }
                    }
                    Text(c.content.orEmpty(), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
fun TrackMap(segments: List<List<org.osmdroid.util.GeoPoint>>, hasTrack: Boolean) {
    if (!hasTrack || segments.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box {
            MapCanvas(
                segments = segments,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
            FilledTonalIconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Enlarge map")
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
                Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    ) {
                        IconButton(onClick = { expanded = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close enlarged map")
                        }
                        Text("Route", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    MapCanvas(segments = segments, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * The osmdroid track map itself: draws the polyline segments and zooms to fit
 * the whole track. Used both for the small inline map on the activity detail
 * screen and for the enlarged full-screen version — only the size differs.
 */
@Composable
private fun MapCanvas(segments: List<List<org.osmdroid.util.GeoPoint>>, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            org.osmdroid.views.MapView(context).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.0)
            }
        },
        update = { map ->
            map.overlays.removeAll { it is org.osmdroid.views.overlay.Polyline }
            val allPoints = mutableListOf<org.osmdroid.util.GeoPoint>()
            segments.forEach { seg ->
                if (seg.isNotEmpty()) {
                    val line = org.osmdroid.views.overlay.Polyline(map).apply { setPoints(seg) }
                    map.overlays.add(line)
                    allPoints.addAll(seg)
                }
            }
            if (allPoints.isNotEmpty()) {
                val lats = allPoints.map { it.latitude }
                val lons = allPoints.map { it.longitude }
                val latSpan = (lats.max() - lats.min()).coerceAtLeast(0.002)
                val lonSpan = (lons.max() - lons.min()).coerceAtLeast(0.002)
                map.controller.setCenter(
                    org.osmdroid.util.GeoPoint((lats.max() + lats.min()) / 2.0, (lons.max() + lons.min()) / 2.0),
                )
                map.controller.zoomToSpan(latSpan, lonSpan)
            }
            map.invalidate()
        },
        onRelease = { map -> map.onDetach() },
        modifier = modifier,
    )
}

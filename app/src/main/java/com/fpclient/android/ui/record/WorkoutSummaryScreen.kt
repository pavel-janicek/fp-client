package com.fpclient.android.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.recording.TrackPoint
import com.fpclient.android.recording.TrackRecordingBus
import com.fpclient.android.recording.TrackSessionSnapshot
import com.fpclient.android.recording.TrackStats
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format
import com.fpclient.android.util.TextLimits
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Post-workout summary (Iteration 8d): the just-recorded workout's stats and mini-map plus
 * the metadata it will be shared with (title, description, visibility, activity type), the
 * Share action against the regular multipart upload endpoint, and — once the server has
 * imported it — a direct way into the created ActivityDetail.
 *
 * The same screen serves a workout recovered from the pending-upload registry (recorded in
 * an earlier process, or not shared yet), which is why everything it shows arrives as a
 * parameter instead of being read straight off the recording bus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    sessionId: Long,
    startedAtEpochMs: Long,
    activityType: String,
    initialTitle: String,
    initialDescription: String,
    initialVisibility: String,
    points: List<TrackPoint>,
    stats: TrackStats,
    movingMs: Long,
    vm: WorkoutSummaryViewModel,
    onOpenActivity: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var title by rememberSaveable(sessionId) { mutableStateOf(initialTitle) }
    var description by rememberSaveable(sessionId) { mutableStateOf(initialDescription) }
    var visibility by rememberSaveable(sessionId) { mutableStateOf(initialVisibility) }
    val type = activityType
    var confirmDiscard by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout summary") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // Fixed-height review map: 220 dp keeps it inside the scroll flow so it can
        // never overlay the form below (title/description/visibility/share stay put).
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${activityLabel(type)} · ${Format.dateTime(Instant.ofEpochMilli(startedAtEpochMs).toString())}",
                style = MaterialTheme.typography.titleMedium,
            )
            StatRow(
                items = listOf(
                    "distance" to Format.distanceShort(stats.distanceM),
                    "moving time" to Format.duration(movingMs / 1000),
                    "↑ gain" to "↑${stats.elevationGainM.toInt()} m",
                    "↓ loss" to "↓${stats.elevationLossM.toInt()} m",
                ),
            )
            if (points.isNotEmpty()) {
                LiveTrackMap(
                    points = points,
                    fitTrack = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            } else {
                Text(
                    "No GPS fixes were recorded, so this workout has no route to share.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (ui.uploadedActivityId != null) {
                UploadedPanel(
                    onOpenActivity = { ui.uploadedActivityId?.let(onOpenActivity) },
                    onClose = onClose,
                )
            } else {
                // No activity-type picker here: the type was chosen before Start and is
                // shown in the header — re-picking post-workout invites accidental swaps.
                Text(
                    "Details for FitPub (optional)",
                    style = MaterialTheme.typography.labelLarge,
                )
                // Same logic as the comment composer: when the keyboard opens, scroll
                // the focused field above it instead of letting it hide underneath.
                val fieldScope = rememberCoroutineScope()
                fun scrollFieldIntoView() {
                    fieldScope.launch {
                        // Let the IME settle before scrolling, or the offset is stale and
                        // the field still ends up under the keyboard.
                        delay(150)
                        runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
                    }
                }
                ShareForm(
                    ui = ui,
                    title = title,
                    onTitleChange = { title = it.take(TextLimits.ACTIVITY_TITLE) },
                    onTitleFocused = { scrollFieldIntoView() },
                    description = description,
                    onDescriptionChange = { description = it.take(TextLimits.ACTIVITY_DESCRIPTION) },
                    onDescriptionFocused = { scrollFieldIntoView() },
                    visibility = visibility,
                    onVisibilityChange = { visibility = it },
                    onShare = {
                        vm.share(
                            sessionId = sessionId,
                            activityType = type,
                            title = title.ifBlank { null },
                            description = description.ifBlank { null },
                            visibility = visibility,
                        )
                    },
                    onDiscard = { confirmDiscard = true },
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("The recording is deleted from this device and never uploaded.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        vm.discard(sessionId)
                        onClose()
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep") }
            },
        )
    }
}

/** Metadata form + the Share/Discard actions — hidden once the upload succeeded. */
@Composable
private fun ShareForm(
    ui: WorkoutSummaryViewModel.UiState,
    title: String,
    onTitleChange: (String) -> Unit,
    onTitleFocused: () -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onDescriptionFocused: () -> Unit,
    visibility: String,
    onVisibilityChange: (String) -> Unit,
    onShare: () -> Unit,
    onDiscard: () -> Unit,
) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Title (optional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { /* Next focuses description via tap; scroll handled on focus */ }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onTitleFocused() },
    )
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Description (optional)") },
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .onFocusChanged { if (it.isFocused) onDescriptionFocused() },
    )
    Text("Visibility", style = MaterialTheme.typography.labelLarge)
    VisibilityPicker(selected = visibility, onSelect = onVisibilityChange)
    Text(
        "Privacy zones are applied by the server when it imports the track — exactly as " +
            "for a GPX file uploaded through the upload form.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ui.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Button(
        onClick = onShare,
        enabled = !ui.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (ui.busy) "Uploading…" else "Share to FitPub") }
    OutlinedButton(
        onClick = onDiscard,
        enabled = !ui.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Discard workout") }
    Text(
        "Not now? The workout stays on this device and can be shared later from the " +
            "Record screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Post-import panel: the server created the activity, so offer it and a way out. */
@Composable
private fun UploadedPanel(onOpenActivity: () -> Unit, onClose: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Uploaded to FitPub ✓", style = MaterialTheme.typography.titleMedium)
            Text(
                "The activity was imported on the server with its track and metrics.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth()) {
                Text("View activity")
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Later")
            }
        }
    }
}

/** Visibility chips — the same values the upload form and the manual entry offer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibilityPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActivityVisibilities.ALL.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(value.lowercase()) },
            )
        }
    }
}

/**
 * Assembles the summary's inputs and hands them to the stateless screen: the finished
 * recording while it is still on the bus (the normal path right after Stop), otherwise the
 * persisted session — points and stats replayed from the track file, moving time rebuilt
 * from the segment spans, metadata pre-filled from the pending-upload entry so a retry
 * replays exactly what the user entered before.
 */
@Composable
fun WorkoutSummaryRoute(
    sessionId: Long,
    container: AppContainer,
    onOpenActivity: (String) -> Unit,
    onClose: () -> Unit,
) {
    val vm: WorkoutSummaryViewModel =
        viewModel(factory = WorkoutSummaryViewModel.factory(container))
    val finished by TrackRecordingBus.finished.collectAsState()
    val share = container.recordingShareManager
    val data by produceState<SummaryData?>(null, sessionId, finished) {
        value = withContext(Dispatchers.IO) {
            val onBus = finished?.takeIf { it.snapshot.startedAtEpochMs == sessionId }
            val pending = share.getPending(sessionId)
            if (onBus != null) {
                SummaryData(
                    startedAtEpochMs = onBus.snapshot.startedAtEpochMs,
                    activityType = pending?.activityType ?: onBus.snapshot.activityType,
                    points = onBus.points,
                    stats = onBus.stats,
                    movingMs = onBus.movingMs,
                    title = pending?.title.orEmpty(),
                    description = pending?.description.orEmpty(),
                    visibility = pending?.visibility ?: ActivityVisibilities.PUBLIC,
                )
            } else {
                val segments = share.readSegments(sessionId)
                SummaryData(
                    startedAtEpochMs = pending?.startedAtEpochMs ?: sessionId,
                    activityType = pending?.activityType ?: TrackSessionSnapshot.DEFAULT_ACTIVITY_TYPE,
                    points = segments.flatten(),
                    stats = share.statsForSegments(segments),
                    movingMs = share.movingMsFor(sessionId),
                    title = pending?.title.orEmpty(),
                    description = pending?.description.orEmpty(),
                    visibility = pending?.visibility ?: ActivityVisibilities.PUBLIC,
                )
            }
        }
    }

    val summary = data
    if (summary == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // The bus copy is consumed when the summary is left: a later visit then reads the
    // persisted session instead of a stale in-memory one.
    val consume: () -> Unit = {
        if (finished?.snapshot?.startedAtEpochMs == sessionId) TrackRecordingBus.clearFinished()
    }

    WorkoutSummaryScreen(
        sessionId = sessionId,
        startedAtEpochMs = summary.startedAtEpochMs,
        activityType = summary.activityType,
        initialTitle = summary.title,
        initialDescription = summary.description,
        initialVisibility = summary.visibility,
        points = summary.points,
        stats = summary.stats,
        movingMs = summary.movingMs,
        vm = vm,
        onOpenActivity = { id ->
            consume()
            onOpenActivity(id)
        },
        onClose = {
            consume()
            onClose()
        },
    )
}

/** Everything the summary screen displays, resolved off the main thread. */
private data class SummaryData(
    val startedAtEpochMs: Long,
    val activityType: String,
    val points: List<TrackPoint>,
    val stats: TrackStats,
    val movingMs: Long,
    val title: String,
    val description: String,
    val visibility: String,
)
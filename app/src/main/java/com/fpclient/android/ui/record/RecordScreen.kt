package com.fpclient.android.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.recording.PendingUpload
import com.fpclient.android.recording.TrackRecordingBus
import com.fpclient.android.recording.TrackRecordingController
import com.fpclient.android.util.Format
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The Record flow (Iteration 8c + 8d). A single route with two modes driven by the shared
 * bus: no session → pre-start screen with the activity type picker and the workouts waiting
 * to be shared; session active (recording or paused, from anywhere in the app) → the live
 * recording screen. This doubles as the "no second session" guard: the pre-start surface
 * simply does not exist while a session runs, so re-entering the route always joins the
 * live session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSummary: (Long) -> Unit = {},
) {
    val session by TrackRecordingBus.session.collectAsState()
    val vm: WorkoutSummaryViewModel = viewModel(factory = WorkoutSummaryViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    // Set when the user confirms Stop; the summary opens once the service has actually
    // ended the session (which also guarantees the GPX export exists by then). Plain
    // remember, not rememberSaveable: coming back from the summary must not re-open it.
    var stoppingSessionId by remember { mutableLongStateOf(0L) }

    LaunchedEffect(session, stoppingSessionId) {
        if (session == null && stoppingSessionId != 0L) {
            val sessionId = stoppingSessionId
            stoppingSessionId = 0L
            onOpenSummary(sessionId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (session == null) "Record" else "Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LocationPermissionGate {
                if (session == null) {
                    PreStartScreen(
                        modifier = Modifier.fillMaxSize(),
                        pending = ui.pending,
                        busy = ui.busy,
                        message = ui.message,
                        onOpenSummary = { sessionId ->
                            vm.clearMessage()
                            onOpenSummary(sessionId)
                        },
                        onRetry = vm::retryPending,
                        onDiscard = vm::discard,
                    )
                } else {
                    LiveRecordingScreen(
                        modifier = Modifier.fillMaxSize(),
                        onStopRequested = { stoppingSessionId = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreStartScreen(
    modifier: Modifier = Modifier,
    pending: List<PendingUpload> = emptyList(),
    busy: Boolean = false,
    message: String? = null,
    onOpenSummary: (Long) -> Unit = {},
    onRetry: () -> Unit = {},
    onDiscard: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf("RUN") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("What are you doing?", style = MaterialTheme.typography.titleMedium)
        ActivityTypePicker(selected = selected, onSelect = { selected = it })
        Button(
            onClick = { TrackRecordingController.start(context, selected) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start recording") }
        Text(
            "Your track is recorded on this device only. After you stop, you can " +
                "review it and share it to FitPub.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PendingUploadsSection(
            pending = pending,
            busy = busy,
            onOpenSummary = onOpenSummary,
            onRetry = onRetry,
            onDiscard = onDiscard,
        )
    }
}

/**
 * Workouts recorded on this device but not yet imported by the server (Iteration 8d):
 * each card opens the summary again (with its metadata pre-filled from the last failed
 * attempt) and one button retries the whole queue, so a workout recorded while offline
 * can still be shared later without re-recording it.
 */
@Composable
private fun PendingUploadsSection(
    pending: List<PendingUpload>,
    busy: Boolean,
    onOpenSummary: (Long) -> Unit,
    onRetry: () -> Unit,
    onDiscard: (Long) -> Unit,
) {
    if (pending.isEmpty()) return
    Text("Waiting to be shared", style = MaterialTheme.typography.titleMedium)
    Text(
        "These recordings are stored on this device and can be uploaded whenever " +
            "the server is reachable.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    pending.forEach { entry ->
        PendingUploadCard(
            entry = entry,
            busy = busy,
            onOpen = { onOpenSummary(entry.sessionId) },
            onDiscard = { onDiscard(entry.sessionId) },
        )
    }
    Button(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Retrying…" else "Retry uploads")
    }
}

/** One not-yet-shared workout: when it was recorded, its retry state and its actions. */
@Composable
private fun PendingUploadCard(
    entry: PendingUpload,
    busy: Boolean,
    onOpen: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(activityLabel(entry.activityType), style = MaterialTheme.typography.titleSmall)
            Text(
                Format.dateTime(Instant.ofEpochMilli(entry.startedAtEpochMs).toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (entry.attempts > 0) {
                Text(
                    "Upload attempts: ${entry.attempts}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen, enabled = !busy) { Text("Review & share") }
                OutlinedButton(onClick = onDiscard, enabled = !busy) { Text("Discard") }
            }
        }
    }
}

/** Activity type picker over [ActivityTypes.ALL] — the same set the manual form uses.
 * Shared with the post-workout summary (Iteration 8d), which lets the type be changed
 * before sharing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityTypePicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityTypes.ALL.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(activityLabel(type)) },
            )
        }
    }
}

/** "🏃 run", "ALPINE_SKI" → "⛷️ alpine ski" — shared by the picker and the banner. */
fun activityLabel(type: String): String =
    "${ActivityTypes.icon(type)} ${type.lowercase().replace('_', ' ')}"

/**
 * Wall-clock "now" that re-ticks every 500 ms while the composition is alive, so elapsed
 * labels advance without the session snapshot itself changing every second. Shared by
 * the live screen and the app-wide recording banner.
 */
@Composable
fun rememberTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    return now
}


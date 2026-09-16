package com.fpclient.android.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.recording.TrackRecordingBus
import com.fpclient.android.recording.TrackRecordingController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The Record flow (Iteration 8c). A single route with two modes driven by the shared
 * bus: no session → pre-start screen with the activity type picker; session active
 * (recording or paused, from anywhere in the app) → the live recording screen. This
 * doubles as the "no second session" guard: the pre-start surface simply does not
 * exist while a session runs, so re-entering the route always joins the live session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(onBack: () -> Unit) {
    val session by TrackRecordingBus.session.collectAsState()
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
                if (session == null) PreStartScreen(Modifier.fillMaxSize())
                else LiveRecordingScreen(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun PreStartScreen(modifier: Modifier = Modifier) {
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
            "Your track is recorded on this device only. " +
                "Exporting and sharing it comes in a later update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Activity type picker over [ActivityTypes.ALL] — the same set the manual form uses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityTypePicker(selected: String, onSelect: (String) -> Unit) {
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


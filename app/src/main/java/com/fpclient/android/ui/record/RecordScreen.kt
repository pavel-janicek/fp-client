package com.fpclient.android.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fpclient.android.recording.RecordingState
import com.fpclient.android.recording.TrackRecordingBus
import com.fpclient.android.recording.TrackRecordingController
import com.fpclient.android.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Groundwork verification surface for Iteration 8a: gates the runtime permission flow,
 * then drives TrackRecordingService start/pause/resume/stop and mirrors the live session
 * state from [TrackRecordingBus]. The full Record flow (activity type picker, stats,
 * mini-map) is Iteration 8c — this screen stays as the fallback until then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LocationPermissionGate {
            RecordControls(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp))
        }
    }
}

@Composable
private fun RecordControls(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session by TrackRecordingBus.session.collectAsState()

    // Wall-clock "now" that ticks while a session exists, so the elapsed label advances
    // without the snapshot itself changing every second.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session?.state, session?.startedAtEpochMs) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val s = session
        if (s == null) {
            Text("No recording in progress", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { TrackRecordingController.start(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start recording") }
        } else {
            Text(
                when (s.state) {
                    RecordingState.RECORDING -> "Recording"
                    RecordingState.PAUSED -> "Paused"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                Format.duration(s.elapsedAt(now) / 1000),
                style = MaterialTheme.typography.displayMedium,
            )
            when (s.state) {
                RecordingState.RECORDING -> Button(
                    onClick = { TrackRecordingController.pause(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pause") }
                RecordingState.PAUSED -> Button(
                    onClick = { TrackRecordingController.resume(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Resume") }
            }
            OutlinedButton(
                onClick = { TrackRecordingController.stop(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop") }
        }
    }
}

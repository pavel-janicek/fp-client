package com.fpclient.android.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fpclient.android.recording.RecordingState
import com.fpclient.android.recording.TrackMath
import com.fpclient.android.recording.TrackRecordingBus
import com.fpclient.android.recording.TrackRecordingController
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format

/**
 * The live recording screen (Iteration 8c + 8d): big elapsed timer, the GPS engine's live
 * totals (distance, pace over moving time, elevation gain), pause/resume/stop with a
 * stop confirmation, keep-screen-on toggle, and the optional live mini-map. Observes
 * [TrackRecordingBus]; pausing freezes GPS but the elapsed clock keeps running —
 * exactly the web app's semantics.
 *
 * Stopping reports the session id through [onStopRequested] *before* the service is told,
 * so the caller can open the post-workout summary once the session is really gone.
 */
@Composable
fun LiveRecordingScreen(
    modifier: Modifier = Modifier,
    onStopRequested: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val session by TrackRecordingBus.session.collectAsState()
    val stats by TrackRecordingBus.stats.collectAsState()
    val points by TrackRecordingBus.points.collectAsState()
    val s = session ?: return

    val now = rememberTicker()
    var showMap by rememberSaveable { mutableStateOf(true) }
    var keepScreenOn by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    // Keep the screen on while toggled (and only for as long as this screen exists —
    // the flag is per-window, so it is cleared again on disposal).
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (s.state == RecordingState.PAUSED) "⏸ paused" else activityLabel(s.activityType),
            style = MaterialTheme.typography.titleMedium,
            color = if (s.state == RecordingState.PAUSED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            Format.duration(s.elapsedAt(now) / 1000),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Monospace,
        )
        // Live totals from the GPS engine; pace derives moving time over distance
        // (metric for now, like the groundwork — the 2.0 polish pass revisits).
        StatRow(
            items = listOf(
                "distance" to Format.distanceShort(stats.distanceM),
                "pace" to Format.pace(TrackMath.paceSecondsPerKm(s.movingMsAt(now), stats.distanceM), null),
                "elevation gain" to "↑${stats.elevationGainM.toInt()} m",
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (s.state) {
                RecordingState.RECORDING -> FilledTonalButton(
                    onClick = { TrackRecordingController.pause(context) },
                    modifier = Modifier.weight(1f),
                ) { Text("Pause") }
                RecordingState.PAUSED -> Button(
                    onClick = { TrackRecordingController.resume(context) },
                    modifier = Modifier.weight(1f),
                ) { Text("Resume") }
            }
            FilledTonalButton(
                onClick = { confirmStop = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Text("Stop")
            }
        }
        SettingRow("Keep screen on", keepScreenOn) { keepScreenOn = it }
        SettingRow("Show mini-map", showMap) { showMap = it }
        RecordingHealthCard()
        if (showMap) {
            LiveTrackMap(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        if (confirmStop) {
            AlertDialog(
                onDismissRequest = { confirmStop = false },
                title = { Text("Stop recording?") },
                text = {
                    Text(
                        "The track is saved on this device and you can review and " +
                            "share it right away.",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmStop = false
                            // Report the session id first: the summary opens as soon as
                            // the service has ended the session and exported the GPX.
                            onStopRequested(s.startedAtEpochMs)
                            TrackRecordingController.stop(context)
                        },
                    ) { Text("Stop") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { confirmStop = false }) { Text("Keep recording") }
                },
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

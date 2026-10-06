package com.fpclient.android.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.WorkoutStatus
import java.util.Locale

@Composable
fun WorkoutControlScreen(
    snapshot: WorkoutRecordingSnapshot,
    permissionError: String?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val status = snapshot.status
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Workout",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = when (status) {
                        WorkoutStatus.IDLE -> "Ready"
                        WorkoutStatus.RECORDING -> "Recording"
                        WorkoutStatus.PAUSED -> "Paused"
                        WorkoutStatus.STOPPED -> "Stopped"
                    },
                    style = MaterialTheme.typography.body1,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { MetricText("${formatDuration(snapshot.elapsedMs)} elapsed") }
            item { MetricText("${snapshot.heartRateBpm?.toString() ?: "--"} bpm") }
            item { MetricText("${"%.2f".format(Locale.ROOT, snapshot.distanceMeters / 1000.0)} km") }
            item { MetricText("${formatPace(snapshot.paceSecondsPerKm)} /km") }
            item { MetricText("${snapshot.steps} steps") }
            item {
                MetricText(
                    listOf(
                        "GPS ${availabilityLabel(snapshot.availability.gps)}",
                        "HR ${availabilityLabel(snapshot.availability.heartRate)}",
                        "Steps ${availabilityLabel(snapshot.availability.steps)}",
                    ).joinToString(" · "),
                )
            }
            if (!snapshot.availability.gps && (status == WorkoutStatus.RECORDING || status == WorkoutStatus.PAUSED)) {
                item { MetricText("No GPS. HR, steps and time continue.") }
            }
            (snapshot.errorMessage ?: permissionError)?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when (status) {
                WorkoutStatus.RECORDING -> {
                    item { Button(onClick = onPause, modifier = Modifier.fillMaxWidth()) { Text("Pause") } }
                    item { Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") } }
                }
                WorkoutStatus.PAUSED -> {
                    item { Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume") } }
                    item { Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") } }
                }
                WorkoutStatus.IDLE, WorkoutStatus.STOPPED ->
                    item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start workout") } }
            }
        }
    }
}

@Composable
private fun MetricText(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.body2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun availabilityLabel(available: Boolean): String = if (available) "on" else "off"

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatPace(secondsPerKm: Long?): String {
    if (secondsPerKm == null) return "--:--"
    return "%d:%02d".format(Locale.ROOT, secondsPerKm / 60L, secondsPerKm % 60L)
}
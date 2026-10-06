package com.fpclient.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.WorkoutHeartRateZone
import com.fpclient.android.wear.recording.WorkoutStatus
import java.util.Locale

@Composable
fun WorkoutControlScreen(
    snapshot: WorkoutRecordingSnapshot,
    permissionError: String?,
    onStart: (WorkoutActivityType) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onAmbientModeChanged: (Boolean) -> Unit,
) {
    val ambientManager = rememberAmbientModeManager()
    CompositionLocalProvider(LocalAmbientModeManager provides ambientManager) {
        val isAmbient = LocalAmbientModeManager.current?.currentAmbientMode is AmbientMode.Ambient
        val pagerState = rememberPagerState(pageCount = { 2 })
        var selectedTypeName by rememberSaveable { mutableStateOf(WorkoutActivityType.RUN.name) }
        val selectedType = remember(selectedTypeName) { WorkoutActivityType.fromStorage(selectedTypeName) }

        LaunchedEffect(isAmbient) { onAmbientModeChanged(isAmbient) }
        LaunchedEffect(isAmbient) {
            if (isAmbient && pagerState.currentPage != 0) pagerState.scrollToPage(0)
        }

        Scaffold(
            modifier = Modifier.background(Color.Black),
            timeText = { TimeText() },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isAmbient,
            ) { page ->
                if (page == 0) {
                    LiveWorkoutPage(snapshot, isAmbient)
                } else {
                    WorkoutControlsPage(
                        snapshot = snapshot,
                        permissionError = permissionError,
                        selectedType = selectedType,
                        onSelectType = { selectedTypeName = it.name },
                        onStart = { onStart(selectedType) },
                        onPause = onPause,
                        onResume = onResume,
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveWorkoutPage(snapshot: WorkoutRecordingSnapshot, isAmbient: Boolean) {
    val zone = WorkoutHeartRateZone.fromBpm(snapshot.heartRateBpm)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = when (snapshot.status) {
                WorkoutStatus.IDLE -> "READY"
                WorkoutStatus.RECORDING -> snapshot.session?.activityType?.label?.uppercase(Locale.ROOT) ?: "WORKOUT"
                WorkoutStatus.PAUSED -> "PAUSED"
                WorkoutStatus.STOPPED -> "FINISHED"
            },
            style = MaterialTheme.typography.caption2,
            color = if (isAmbient) Color.LightGray else MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = snapshot.heartRateBpm?.toString()?.takeUnless { isAmbient } ?: "--",
            fontSize = 58.sp,
            fontWeight = FontWeight.Bold,
            color = if (isAmbient) Color.White else zone?.toColor() ?: Color.LightGray,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = if (isAmbient) "BPM" else "${zone?.label ?: "NO SIGNAL"} · BPM",
            style = MaterialTheme.typography.caption1,
            color = if (isAmbient) Color.LightGray else zone?.toColor() ?: Color.LightGray,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = formatDuration(snapshot.elapsedMs),
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isAmbient) "-- km" else "${"%.2f".format(Locale.ROOT, snapshot.distanceMeters / 1000.0)} km",
            style = MaterialTheme.typography.body2,
            color = if (isAmbient) Color.DarkGray else Color.LightGray,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WorkoutControlsPage(
    snapshot: WorkoutRecordingSnapshot,
    permissionError: String?,
    selectedType: WorkoutActivityType,
    onSelectType: (WorkoutActivityType) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = if (snapshot.status == WorkoutStatus.IDLE || snapshot.status == WorkoutStatus.STOPPED) {
                    "Activity"
                } else {
                    "Workout"
                },
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (snapshot.status == WorkoutStatus.IDLE || snapshot.status == WorkoutStatus.STOPPED) {
            WorkoutActivityType.entries.forEach { type ->
                item {
                    Chip(
                        onClick = { onSelectType(type) },
                        label = {
                            Text(
                                text = if (selectedType == type) "Selected · ${type.label}" else type.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            item { ControlMetric("${snapshot.pendingSyncCount} pending sync") }
            item { ControlMetric("${snapshot.steps} steps") }
            item { ControlMetric("${formatPace(snapshot.paceSecondsPerKm)} /km") }
            item { ControlMetric(sensorSummary(snapshot)) }
            if (!snapshot.availability.gps) item { ControlMetric("GPS unavailable · HR and steps continue") }
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

        when (snapshot.status) {
            WorkoutStatus.IDLE, WorkoutStatus.STOPPED -> {
                item { Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") } }
            }
            WorkoutStatus.RECORDING -> {
                item { Button(onClick = onPause, modifier = Modifier.fillMaxWidth()) { Text("Pause") } }
                item { Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") } }
            }
            WorkoutStatus.PAUSED -> {
                item { Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume") } }
                item { Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") } }
            }
        }
    }
}

@Composable
private fun ControlMetric(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.body2,
        color = Color.LightGray,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun WorkoutHeartRateZone.toColor(): Color = when (this) {
    WorkoutHeartRateZone.EASY -> Color(0xFF57D68D)
    WorkoutHeartRateZone.AEROBIC -> Color(0xFFE5D64A)
    WorkoutHeartRateZone.TEMPO -> Color(0xFFFFA24A)
    WorkoutHeartRateZone.THRESHOLD -> Color(0xFFFF655E)
    WorkoutHeartRateZone.PEAK -> Color(0xFFE77CFF)
}

private fun sensorSummary(snapshot: WorkoutRecordingSnapshot): String = listOf(
    if (snapshot.availability.gps) "GPS" else null,
    if (snapshot.availability.heartRate) "HR" else null,
    if (snapshot.availability.steps) "steps" else null,
).filterNotNull().joinToString(" · ").ifBlank { "Timer only" }

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatPace(secondsPerKm: Long?): String {
    if (secondsPerKm == null) return "--:--"
    return "%d:%02d".format(Locale.ROOT, secondsPerKm / 60L, secondsPerKm % 60L)
}

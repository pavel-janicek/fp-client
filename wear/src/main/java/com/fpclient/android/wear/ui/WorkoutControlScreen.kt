package com.fpclient.android.wear.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutHeartRateZone
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.WorkoutStatus
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun WorkoutControlScreen(
    activityType: WorkoutActivityType,
    snapshot: WorkoutRecordingSnapshot,
    permissionError: String?,
    onStart: (WorkoutActivityType) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSyncPending: () -> Unit = {},
    onDiscardPending: () -> Unit = {},
    onAmbientModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ambientManager = rememberAmbientModeManager()
    CompositionLocalProvider(LocalAmbientModeManager provides ambientManager) {
        val isAmbient = LocalAmbientModeManager.current?.currentAmbientMode is AmbientMode.Ambient
        val currentType = snapshot.session?.activityType ?: activityType
        val zone = WorkoutHeartRateZone.fromBpm(snapshot.heartRateBpm)

        LaunchedEffect(isAmbient) { onAmbientModeChanged(isAmbient) }

        // One-second UI tick: keeps the on-screen timer moving whenever the screen is on,
        // anchored to the session's monotonic start — so it advances even if the recording
        // service's periodic publish is throttled (ambient, background limits, clock sync).
        var monotonicNow by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
        val recording = snapshot.status == WorkoutStatus.RECORDING
        LaunchedEffect(recording) {
            while (recording) {
                delay(1_000L)
                monotonicNow = SystemClock.elapsedRealtime()
            }
        }
        val elapsedMs = if (recording) {
            snapshot.session?.liveElapsedMs(monotonicNow, System.currentTimeMillis()) ?: snapshot.elapsedMs
        } else {
            snapshot.elapsedMs
        }

        Scaffold(
            modifier = modifier.background(Color.Black),
            timeText = { TimeText() },
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Text(
                        text = when (snapshot.status) {
                            WorkoutStatus.IDLE -> currentType.label.uppercase(Locale.ROOT)
                            WorkoutStatus.RECORDING -> currentType.label.uppercase(Locale.ROOT)
                            WorkoutStatus.PAUSED -> "${currentType.label.uppercase(Locale.ROOT)} · PAUSED"
                            WorkoutStatus.STOPPED -> "✓ ${currentType.label.uppercase(Locale.ROOT)} SAVED"
                        },
                        style = MaterialTheme.typography.caption1,
                        color = if (isAmbient) Color.LightGray else MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (snapshot.pendingSyncCount > 0) {
                    item { MetricLabel("${snapshot.pendingSyncCount} pending sync") }
                    item {
                        Button(
                            onClick = onSyncPending,
                            colors = ButtonDefaults.secondaryButtonColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Text("Sync now")
                        }
                    }
                    item {
                        Button(
                            onClick = onDiscardPending,
                            colors = ButtonDefaults.secondaryButtonColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Text("Discard pending")
                        }
                    }
                }

                // Heart rate BPM readout
                item {
                    Text(
                        text = snapshot.heartRateBpm?.toString()?.takeUnless { isAmbient } ?: "--",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAmbient) Color.White else zone?.toColor() ?: Color.LightGray,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        text = if (isAmbient) "BPM" else "${zone?.label ?: if (snapshot.status == WorkoutStatus.IDLE) "READY" else "NO SIGNAL"} · BPM",
                        style = MaterialTheme.typography.caption2,
                        color = if (isAmbient) Color.LightGray else zone?.toColor() ?: Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Elapsed duration timer
                item {
                    Text(
                        text = formatDuration(elapsedMs),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                item {
                    Text(
                        text = if (snapshot.status == WorkoutStatus.RECORDING) "ELAPSED TIME" else "TIME",
                        style = MaterialTheme.typography.caption2,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    MetricLabel(
                        if (isAmbient) "-- km" else "${"%.2f".format(Locale.ROOT, snapshot.distanceMeters / 1000.0)} km",
                    )
                }

                if (snapshot.status != WorkoutStatus.IDLE) {
                    item { MetricLabel("${formatPace(snapshot.paceSecondsPerKm)} /km · ${snapshot.steps} steps") }
                    item { MetricLabel(sensorSummary(snapshot)) }
                }

                (snapshot.errorMessage ?: permissionError)?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }

                // Action buttons based on workout status
                when (snapshot.status) {
                    WorkoutStatus.IDLE, WorkoutStatus.STOPPED -> {
                        item {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStart(currentType)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                            ) {
                                Text("Start ${currentType.label}")
                            }
                        }
                    }
                    WorkoutStatus.RECORDING -> {
                        item {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPause()
                                },
                                colors = ButtonDefaults.secondaryButtonColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Text("Pause")
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStop()
                                },
                                colors = ButtonDefaults.secondaryButtonColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Text("Stop")
                            }
                        }
                    }
                    WorkoutStatus.PAUSED -> {
                        item {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onResume()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Text("Resume")
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStop()
                                },
                                colors = ButtonDefaults.secondaryButtonColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricLabel(value: String) {
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
).filterNotNull().joinToString(" · ").ifBlank { "Timer" }

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    return "%d:%02d".format(Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatPace(secondsPerKm: Long?): String {
    if (secondsPerKm == null) return "--:--"
    return "%d:%02d".format(Locale.ROOT, secondsPerKm / 60L, secondsPerKm % 60L)
}

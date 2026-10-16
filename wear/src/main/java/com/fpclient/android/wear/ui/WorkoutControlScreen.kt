package com.fpclient.android.wear.ui
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutHeartRateZone
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.liveHeartRateBpm
import com.fpclient.android.wear.recording.WorkoutStatus
import kotlinx.coroutines.delay
import java.util.Date
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
    onLowPowerModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ambientManager = rememberAmbientModeManager()
    CompositionLocalProvider(LocalAmbientModeManager provides ambientManager) {
        val isAmbient = LocalAmbientModeManager.current?.currentAmbientMode is AmbientMode.Ambient
        var wallClockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }

        val currentType = snapshot.session?.activityType ?: activityType
        // A reading older than the stale window is not shown as if it were live.
        val heartRateBpm = snapshot.liveHeartRateBpm(wallClockNow)
        val zone = WorkoutHeartRateZone.fromBpm(heartRateBpm)
        val recording = snapshot.status == WorkoutStatus.RECORDING

        // The service only needs live updates while this screen is visible and awake: dimmed,
        // backgrounded (lifecycle below STARTED) or navigated away from, it switches to low power
        // and publishes once a minute. Keyed on `recording` too: the state is only forwarded
        // while a workout runs, so a change that happened just before Start must be re-sent once
        // recording begins. A stale "low power" in the service throttles every live update — BPM
        // included — to once a minute while this screen's own timer keeps ticking.
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        LaunchedEffect(isAmbient, visible, recording) { onLowPowerModeChanged(isAmbient || !visible) }
        val latestOnLowPowerModeChanged by rememberUpdatedState(onLowPowerModeChanged)
        DisposableEffect(Unit) { onDispose { latestOnLowPowerModeChanged(true) } }

        // Ambient shows only the clock and what is recording; waking the screen (wrist raise /
        // tap) brings the full live workout back. Workout data is deliberately not drawn while
        // dimmed: the platform only refreshes the display on ambient ticks (~once a minute), so
        // any number shown there is up to a minute old — and on the Xiaomi Watch 2 the screen
        // froze on "--" and later a stale "91" for twenty minutes (makni.cz 82bb40f7, 3.1.0).
        if (isAmbient) {
            AmbientWorkoutClock(label = ambientWorkoutLabel(snapshot.status, currentType))
            return@CompositionLocalProvider
        }

        // One-second UI tick: keeps the on-screen active duration timer moving live
        // while recording, anchored to the session's moving time (excluding pauses). It leaves
        // composition while dimmed, so it re-syncs the clock first when the screen wakes.
        LaunchedEffect(recording) {
            wallClockNow = System.currentTimeMillis()
            while (recording) {
                delay(1_000L)
                wallClockNow = System.currentTimeMillis()
            }
        }
        val workoutDurationMs = if (recording) {
            snapshot.session?.movingMsAt(wallClockNow) ?: snapshot.movingMs
        } else {
            snapshot.movingMs
        }

        Scaffold(
            modifier = modifier.background(Color.Black),
            timeText = { TimeText() },
        ) {
            // Top-anchored instead of the default auto-centering, which parks item #1 (the HR
            // readout) in the middle of the round screen and pushes distance, pace and steps
            // below the fold (makni.cz e4ac4998). The top padding clears the TimeText.
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(horizontal = 8.dp),
                state = rememberScalingLazyListState(initialCenterItemIndex = 0),
                autoCentering = null,
                contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
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
                        color = MaterialTheme.colors.primary,
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

                // Heart rate BPM readout (interactive only — ambient shows the clock above).
                item {
                    Text(
                        text = heartRateBpm?.toString() ?: "--",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = zone?.toColor() ?: Color.LightGray,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        text = "${heartRateLabel(snapshot, zone)} · BPM",
                        style = MaterialTheme.typography.caption2,
                        color = zone?.toColor() ?: Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Workout active duration timer
                item {
                    Text(
                        text = formatDuration(workoutDurationMs),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (snapshot.status == WorkoutStatus.PAUSED) MaterialTheme.colors.secondary else Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                item {
                    Text(
                        text = when (snapshot.status) {
                            WorkoutStatus.PAUSED -> "PAUSED · WORKOUT TIME"
                            WorkoutStatus.RECORDING -> "WORKOUT TIME"
                            else -> "TIME"
                        },
                        style = MaterialTheme.typography.caption2,
                        color = if (snapshot.status == WorkoutStatus.PAUSED) MaterialTheme.colors.secondary else Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    MetricLabel(
                        "${"%.2f".format(Locale.ROOT, snapshot.distanceMeters / 1000.0)} km",
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

/**
 * Caption under the BPM readout. Distinguishes the states the user needs to tell apart:
 * warming up, genuinely no signal, watch off the wrist, and no heart-rate permission at all.
 */
private fun heartRateLabel(snapshot: WorkoutRecordingSnapshot, zone: WorkoutHeartRateZone?): String = when {
    zone != null -> zone.label
    snapshot.status == WorkoutStatus.IDLE -> "READY"
    // The #1 real cause of a "--" readout: the PPG has no skin contact (watch off wrist / charging
    // on a desk). Say so, instead of a bare dash that looks like a fault.
    snapshot.availability.noContact -> "NO CONTACT"
    !snapshot.availability.heartRate -> "HR OFF"
    else -> "NO SIGNAL"
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

/**
 * Ambient (dimmed) workout screen: the current time, plus a one-line reminder of what is being
 * recorded. Updated on ambient ticks only, as the platform requires; white-on-black and few lit
 * pixels for OLED burn-in safety. Follows the user's 12/24-hour setting.
 */
@Composable
private fun AmbientWorkoutClock(label: String?) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LocalAmbientModeManager.current?.AmbientTickEffect { now = System.currentTimeMillis() }
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeFormat.format(Date(now)),
            fontSize = 48.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.caption1,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** "> WALK" while recording, "WALK · PAUSED" while paused, nothing otherwise. */
internal fun ambientWorkoutLabel(status: WorkoutStatus, type: WorkoutActivityType): String? {
    val name = type.label.uppercase(Locale.ROOT)
    return when (status) {
        WorkoutStatus.RECORDING -> "> $name"
        WorkoutStatus.PAUSED -> "$name · PAUSED"
        else -> null
    }
}

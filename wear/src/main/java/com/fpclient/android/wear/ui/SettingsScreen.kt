package com.fpclient.android.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.BuildConfig
import com.fpclient.android.wear.recording.WatchWorkoutSyncStore
import com.fpclient.android.wear.recording.WorkoutRecordingController

/**
 * Settings screen: app version / About summary, sensor-permission entry point,
 * and pending workout sync/discard management.
 */
@Composable
fun SettingsScreen(
    onRequestSensorPermissions: () -> Unit,
    onSyncPending: () -> Unit = {},
    onDiscardPending: () -> Unit = {},
    onResolvePending: () -> Unit = {},
    permissionRefreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val missing = remember(permissionRefreshKey) { WorkoutRecordingController.missingPermissions(context) }
    val syncStore = remember { WatchWorkoutSyncStore(context) }
    var pendingCount by remember { mutableStateOf(syncStore.all().size) }
    var feedback by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.title2,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Pending Sync",
                    style = MaterialTheme.typography.title3,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            item {
                Text(
                    text = "$pendingCount pending workout(s) waiting to upload",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (pendingCount > 0) {
                item {
                    Button(
                        onClick = {
                            onSyncPending()
                            feedback = "Sync triggered"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text("Sync pending now")
                    }
                }
                item {
                    Button(
                        onClick = {
                            onDiscardPending()
                            pendingCount = 0
                            feedback = "Pending workouts discarded"
                        },
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text("Discard pending")
                    }
                }
                item {
                    Button(
                        onClick = {
                            onResolvePending()
                            feedback = "Full retry queued for pending workouts"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text("Resolve pending")
                    }
                }
            }
            feedback?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
            item {
                Text(
                    text = if (missing.isEmpty()) {
                        "Sensor access granted"
                    } else {
                        "Sensor access is needed for GPS, heart rate and steps."
                    },
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            item {
                Button(
                    onClick = onRequestSensorPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text(if (missing.isEmpty()) "Review sensor access" else "Grant sensor access")
                }
            }
        }
    }
}

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun SettingsScreenPreview() {
    FitPubWearTheme {
        SettingsScreen(onRequestSensorPermissions = {})
    }
}

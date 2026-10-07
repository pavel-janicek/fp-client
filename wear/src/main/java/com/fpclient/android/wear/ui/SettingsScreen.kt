package com.fpclient.android.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.BuildConfig
import com.fpclient.android.wear.recording.WorkoutRecordingController

/**
 * Settings screen: app version / About summary plus the sensor-permission entry point.
 *
 * This replaces the old About destination — the version no longer sits on [HomeScreen], and this
 * is where the watch asks for access to its sensors (GPS, heart rate, steps). The grant status is
 * recomputed whenever [permissionRefreshKey] changes (the activity bumps it after each permission
 * dialog), so it stays accurate without observing a permission flow.
 */
@Composable
fun SettingsScreen(
    onRequestSensorPermissions: () -> Unit,
    permissionRefreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val missing = remember(permissionRefreshKey) { WorkoutRecordingController.missingPermissions(context) }

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
                    text = "Independent watch module — the phone relays your FitPub sign-in over " +
                        "the Wearable Data Layer. GPS, heart rate and steps are recorded only " +
                        "during a workout you start.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = onRequestSensorPermissions,
                    modifier = Modifier.fillMaxWidth(),
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

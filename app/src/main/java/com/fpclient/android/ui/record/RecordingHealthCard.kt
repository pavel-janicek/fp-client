package com.fpclient.android.ui.record

import android.content.Intent
import android.location.LocationManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.fpclient.android.recording.RecordingHealth
import com.fpclient.android.recording.TrackRecordingBus

/** Rechecks device settings while visible, including after returning from Settings. */
@Composable
fun RecordingHealthCard(showBattery: Boolean = false) {
    val context = LocalContext.current
    val now = rememberTicker()
    val session by TrackRecordingBus.session.collectAsState()
    val points by TrackRecordingBus.points.collectAsState()
    val error by TrackRecordingBus.storageError.collectAsState()
    val gps = remember(now) {
        runCatching { context.getSystemService(LocationManager::class.java)
            .isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    }
    val exempt = remember(now) {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }
    val enoughSpace = remember(now) { RecordingHealth.hasStorage(context.filesDir.usableSpace) }
    var settingsError by remember { mutableStateOf(false) }
    fun openSettings(action: String) {
        settingsError = runCatching { context.startActivity(Intent(action)) }.isFailure
    }
    Column {
        if (!gps) {
            Text("GPS is off. Enable location to record your route.")
            TextButton(onClick = { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }) {
                Text("Location settings")
            }
        } else if (RecordingHealth.missingFix(session, points.lastOrNull()?.time, now)) {
            Text("No accurate GPS fix for 2 minutes. Move outdoors with a clear view of the sky.")
        }
        if (!enoughSpace) Text("Storage is low. Free at least 10 MiB before recording or resuming.")
        error?.let { Text(it) }
        if (showBattery) {
            Text(if (exempt) "Battery optimization: unrestricted." else
                "Battery optimization is enabled. For long workouts, select FP Client in battery settings and allow unrestricted use.")
            Text("The location foreground service keeps a recording notification visible, but does not guarantee exemption from Doze or manufacturer battery limits.")
            if (!exempt) TextButton(onClick = {
                openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }) { Text("Battery settings") }
        }
        if (settingsError) Text("Settings could not be opened. Open Android Settings manually.")
    }
}

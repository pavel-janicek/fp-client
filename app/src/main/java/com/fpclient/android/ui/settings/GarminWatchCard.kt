package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fpclient.android.garmin.GarminInboxStore
import com.fpclient.android.garmin.GarminLink

/**
 * "Garmin watch" card (Iteration 10e): whether the link can work at all (Garmin Connect present,
 * SDK up), the paired watches with their connection and whether FP Wear is installed, the
 * workouts still on their way to FitPub with the newest error, and "Sync now". Meant to answer
 * "why did my workout not arrive?" without adb.
 */
@Composable
internal fun GarminWatchCard(inbox: GarminInboxStore) {
    val context = LocalContext.current
    val status by GarminLink.status.collectAsState()
    val pending by inbox.pendingCount.collectAsState()
    val latestError by inbox.latestError.collectAsState()
    LaunchedEffect(Unit) { GarminLink.refreshStatus() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Garmin watch", style = MaterialTheme.typography.titleSmall)
            Text(
                statusText(status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            for (device in status.devices) {
                Text(
                    "${device.name.ifBlank { "Garmin watch" }} · ${if (device.connected) "connected" else "not connected"} · " +
                        when (device.appInstalled) {
                            true -> "FP Wear installed"
                            false -> "FP Wear not installed"
                            null -> "checking FP Wear…"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                if (pending > 0) "$pending Garmin workout(s) not on FitPub yet" else "No Garmin workouts waiting",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            latestError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
            }
            if (status.availability != GarminLink.Availability.NO_GARMIN_CONNECT) {
                OutlinedButton(
                    onClick = {
                        inbox.retryManually()
                        GarminLink.syncNow(context)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Sync now") }
            }
        }
    }
}

private fun statusText(status: GarminLink.Status): String = when (status.availability) {
    GarminLink.Availability.NO_GARMIN_CONNECT ->
        "Workouts from a Garmin watch need the Garmin Connect app on this phone and the FP Wear app on the watch."
    GarminLink.Availability.STARTING -> "Connecting to Garmin Connect…"
    GarminLink.Availability.ERROR -> when (status.error) {
        "GCM_UPGRADE_NEEDED" -> "Garmin Connect needs an update before FP Client can talk to your watch."
        "GCM_NOT_INSTALLED" -> "Garmin Connect is not installed."
        else -> "Garmin Connect is not reachable (${status.error ?: "unknown error"})."
    }
    GarminLink.Availability.READY ->
        if (status.devices.isEmpty()) "No watch is paired in Garmin Connect." else "Workouts saved in FP Wear upload to FitPub on their own."
}

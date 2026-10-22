package com.fpclient.android.ui.settings

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fpclient.android.AppContainer
import com.fpclient.android.R
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.garmin.GarminConnectStatus
import com.fpclient.android.garmin.GarminConnectStore
import com.fpclient.android.garmin.GarminConnectWorker
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val RELAY_PRIVACY_URL = "https://connect.paveljanicek.cz/privacy"

/**
 * "Garmin Connect™" card (PLAN "Garmin Connect API", step 4): links the user's Garmin account
 * through the relay, which delivers activities recorded on any Garmin watch.
 *
 * Garmin's API brand guidelines ask for the full app name and its tile when presenting the
 * connection, and every imported activity carries a "Garmin [device model]" attribution. The
 * user confirms the attribution before linking.
 */
@Composable
internal fun GarminConnectCard(container: AppContainer) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val store = container.garminConnectStore
    val sync = container.garminConnectSync
    val mailbox by store.mailbox.collectAsState()
    val status by store.status.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(mailbox?.id) {
        if (mailbox != null) sync.refreshStatus()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(R.drawable.garmin_connect_tile),
                    contentDescription = "Garmin Connect™",
                    modifier = Modifier.size(40.dp),
                )
                Column {
                    Text("Garmin Connect™ (experimental)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary(mailbox != null, status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val linked = mailbox != null && status.linked
            if (linked) {
                if (!status.activityExport) {
                    Text(
                        "Garmin does not share your activities with FP Client. Allow \"Activities\" " +
                            "for FP Client in Garmin Connect (Settings → Connected Apps), or connect again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                status.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            GarminConnectWorker.enqueue(context)
                            message = "Checking for new Garmin activities…"
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Sync now") }
                    OutlinedButton(
                        onClick = { confirmDisconnect = true },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Disconnect") }
                }
            } else {
                Text(
                    "Activities you record on your Garmin watch reach FitPub automatically, through " +
                        "FP Client's relay. Your FitPub login never leaves this phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { acknowledged = !acknowledged },
                ) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(
                        "I understand that imported activities carry a \"Garmin [device model]\" " +
                            "attribution, also when I share them from FitPub.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "How the relay handles your data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri(RELAY_PRIVACY_URL) },
                )
                Button(
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            when (val result = sync.connect()) {
                                is ApiResult.Success -> {
                                    GarminConnectWorker.schedulePeriodic(context)
                                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(result.data))
                                }
                                is ApiResult.Error -> message = result.message ?: "The Garmin relay could not be reached."
                            }
                            busy = false
                        }
                    },
                    enabled = acknowledged && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect Garmin Connect™") }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Garmin Connect™?") },
            text = {
                Text(
                    "New Garmin activities will stop reaching FitPub. The relay forgets your Garmin " +
                        "access and deletes anything still waiting. Activities already on FitPub stay.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    busy = true
                    scope.launch {
                        val result = sync.disconnect()
                        message = if (result is ApiResult.Error) {
                            result.message ?: "The Garmin relay could not be reached."
                        } else {
                            "Garmin Connect™ disconnected"
                        }
                        GarminConnectWorker.schedulePeriodic(context)
                        busy = false
                    }
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
}

private fun summary(hasMailbox: Boolean, status: GarminConnectStatus): String = when {
    hasMailbox && status.linked -> buildString {
        append("Connected")
        if (status.waitingFiles > 0) append(" · ${status.waitingFiles} waiting")
        if (status.lastSyncEpochMs > 0) {
            append(" · last check ")
            append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.lastSyncEpochMs)))
        }
    }
    status.linkLost == "deregistered" -> "Disconnected in Garmin Connect. Connect again to resume imports."
    status.linkLost == "replaced" -> "Your Garmin account was connected on another device."
    status.linkLost == "relink" -> "Garmin ended the connection. Connect again to resume imports."
    else -> "Import activities from any Garmin watch"
}

/**
 * Who sees activities that come from Garmin, through Garmin Connect or FP Wear. Private by
 * default: federating them is the user's choice.
 */
@Composable
internal fun GarminVisibilityCard(store: GarminConnectStore) {
    val visibility by store.visibility.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text("Visibility of Garmin imports", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Who sees activities that arrive from your Garmin watch. You can change each one later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ActivityVisibilities.PRIVATE to "Only me",
                    ActivityVisibilities.FOLLOWERS to "Followers",
                    ActivityVisibilities.PUBLIC to "Public",
                ).forEach { (value, label) ->
                    FilterChip(selected = visibility == value, onClick = { store.setVisibility(value) }, label = { Text(label) })
                }
            }
        }
    }
}

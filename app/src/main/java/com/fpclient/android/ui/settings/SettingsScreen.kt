package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.UnitSystems
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.wear.PhoneHandshakeDiagnosticsStore
import com.fpclient.android.wear.PhoneWearAuthRelay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTimestamp(epochMs: Long): String = if (epochMs == 0L) "" else SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(epochMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenPrivacyZones: () -> Unit,
    onChangeInstance: () -> Unit,
    onOpenBatchImport: () -> Unit,
    onOpenKomootImport: () -> Unit,
    onOpenDataExport: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenEmailChange: () -> Unit,
) {
    val unitSystem by appViewModel.unitSystem.collectAsState()
    val sessionState by appViewModel.uiState.collectAsState()
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Units", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UnitSystems.ALL.forEach { u ->
                            FilterChip(
                                selected = unitSystem == u,
                                onClick = { appViewModel.setUnitSystem(u) },
                                label = { Text(u.lowercase()) },
                            )
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Instance", style = MaterialTheme.typography.titleSmall)
                    Text(
                        sessionState.serverUrl.ifBlank { "Not configured" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                // Tokens are per-instance, so switching instances signs out.
                                container.authRepository.logout()
                                onChangeInstance()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Change instance") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Privacy", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = onOpenPrivacyZones,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Manage privacy zones") }
                }
            }
            // Push (Iteration 8f): the card owns the notification-permission request and states
            // the "eventual, not instant" delivery model.
            PushNotificationCard(container)
            // Direct mailbox push (Iteration 8h): shown signed-in only, since the subscription
            // is per-account (probe → mint mailbox → POST /subscribe through the session flow).
            if (sessionState.loggedIn) {
                MailboxPushCard(container)
            }
            if (sessionState.loggedIn) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Data", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = onOpenBatchImport,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Batch import activities") }
                        OutlinedButton(
                            onClick = onOpenKomootImport,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Import from Komoot") }
                        OutlinedButton(
                            onClick = onOpenDataExport,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Export my data") }
                    }
                }
            }
            // Recording entry point: the flow itself lives in the main UI since Iteration
            // 8c (Record button on Timeline/Me); this card is the secondary discovery path.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Recording", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = onOpenRecord,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Record a track") }
                }
            }
            UpdateCheckCard()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("About", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("About this app") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Handshake diagnostics", style = MaterialTheme.typography.titleSmall)
                    val context = LocalContext.current
                    val diagnosticsStore = remember { PhoneHandshakeDiagnosticsStore(context) }
                    val diagnostics by diagnosticsStore.diagnostics.collectAsState(initial = com.fpclient.android.wear.PhoneHandshakeDiagnostics())
                    var reachableNodes by remember { mutableStateOf<List<String>>(emptyList()) }
                    var sendReplyFeedback by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        scope.launch {
                            reachableNodes = try {
                                PhoneWearAuthRelay.getReachableWatchNodeIds(context.applicationContext)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text("Last watch request (node/path/time):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("node=${diagnostics.lastRequestNodeId.takeIf { it.isNotBlank() } ?: "?"}, path=${diagnostics.lastRequestPath.takeIf { it.isNotBlank() } ?: "?"}, ${formatTimestamp(diagnostics.lastRequestTimestampMs)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Last reply: ${if (diagnostics.lastReplyFailed) "FAILED" else if (diagnostics.lastReplySent) "sent ${diagnostics.lastReplyType}" else "not sent"} @ ${formatTimestamp(diagnostics.lastReplyTimestampMs)}", style = MaterialTheme.typography.bodySmall, color = if (diagnostics.lastReplyFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Session: ${if (sessionState.loggedIn) "logged in" else "guest"} (server=${sessionState.serverUrl.takeIf { it.isNotBlank() } ?: "?"}, user=${sessionState.username.takeIf { it.isNotBlank() } ?: "?"})", style = MaterialTheme.typography.bodySmall, color = if (sessionState.loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Reachable watch nodes (best-effort): ${reachableNodes.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                val nodeId = diagnostics.lastRequestNodeId.takeIf { it.isNotBlank() }
                                if (nodeId == null) { sendReplyFeedback = "No watch node recorded"; return@launch }
                                val session = container.sessionStore.currentSession()
                                val ok = PhoneWearAuthRelay.sendReplyTo(context, nodeId, session)
                                sendReplyFeedback = if (ok) "Reply sent" else "Reply failed"
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Send handshake reply") }
                        Button(onClick = {
                            scope.launch {
                                reachableNodes = try {
                                    PhoneWearAuthRelay.getReachableWatchNodeIds(context.applicationContext)
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("Refresh") }
                    }
                    if (sendReplyFeedback != null) {
                        Text(sendReplyFeedback!!, style = MaterialTheme.typography.labelMedium, color = if (sendReplyFeedback == "Reply sent") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleSmall)
                    if (sessionState.loggedIn) {
                        OutlinedButton(
                            onClick = { showChangePassword = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Change password") }
                        OutlinedButton(
                            onClick = onOpenEmailChange,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Change email address") }
                        Button(
                            onClick = {
                                scope.launch {
                                    // Session state drives the UI back to the auth flow.
                                    container.authRepository.logout()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Sign out") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    container.sessionStore.clearGuest()
                                    // Guest flag cleared -> MainActivity shows the auth flow.
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Sign in or create account") }
                    }
                    if (sessionState.loggedIn) {
                        var confirmDelete by rememberSaveable { mutableStateOf(false) }
                        Button(
                            onClick = { confirmDelete = true },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) { Text("Delete account") }
                        if (confirmDelete) {
                            androidx.compose.material3.AlertDialog(
                                // Resizable on large screens: not limited to the platform default width.
                                properties = androidx.compose.ui.window.DialogProperties(
                                    usePlatformDefaultWidth = false,
                                ),
                                onDismissRequest = { confirmDelete = false },
                                title = { Text("Delete account?") },
                                text = {
                                    Text(
                                        "This permanently deletes your account and all your " +
                                            "activities on this instance. This cannot be undone.",
                                    )
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            confirmDelete = false
                                            scope.launch {
                                                // Session cleared server-side and locally;
                                                // the app returns to the auth flow automatically.
                                                container.authRepository.deleteAccount()
                                            }
                                        },
                                    ) { Text("Delete forever") }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                                        Text("Cancel")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(container = container, onDismiss = { showChangePassword = false })
    }
}

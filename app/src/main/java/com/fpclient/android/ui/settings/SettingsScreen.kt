package com.fpclient.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fpclient.android.AppContainer
import com.fpclient.android.BuildConfig
import com.fpclient.android.data.dto.UnitSystems
import com.fpclient.android.garmin.GarminLink
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.wear.PhoneHandshakeDiagnostics
import com.fpclient.android.wear.PhoneHandshakeDiagnosticsStore
import com.fpclient.android.wear.PhoneWearAuthRelay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTimestamp(epochMs: Long): String = if (epochMs == 0L) "" else SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(epochMs))

/**
 * The Settings sub-pages. The top level is a short list of these categories, each opening its
 * own page, instead of one long page of cards. Kept inside this screen (not separate nav
 * routes) so the deep link `?scroll=push` and every existing callback stay unchanged.
 */
private enum class SettingsSection(val title: String) {
    ACCOUNT("Account"),
    NOTIFICATIONS("Notifications"),
    WATCHES("Watches"),
    PRIVACY("Privacy"),
    DATA("Data"),
    GENERAL("General"),
}

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
    onOpenAbout: () -> Unit,
    onOpenEmailChange: () -> Unit,
    onOpenWearWorkoutInbox: () -> Unit = {},
    initialSection: String? = null,
) {
    // Deep links open a page directly: "push" (the Activity tab's mailbox-disabled banner) opens
    // Notifications, and Back from it leaves Settings, back to the banner; "watches" (the return
    // from Garmin's sign-in) opens Watches, and Back from it shows the Settings overview.
    val fromBanner = initialSection == "push"
    var section by rememberSaveable {
        mutableStateOf(
            when (initialSection) {
                "push" -> SettingsSection.NOTIFICATIONS
                "watches" -> SettingsSection.WATCHES
                else -> null
            },
        )
    }
    val closeSection = { if (fromBanner && section == SettingsSection.NOTIFICATIONS) onBack() else section = null }
    BackHandler(enabled = section != null) { closeSection() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(section?.title ?: "Settings") },
                navigationIcon = {
                    IconButton(onClick = { if (section != null) closeSection() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // Keyed on the section so every page starts scrolled to the top.
        val scrollState = androidx.compose.runtime.key(section) { rememberScrollState() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState),
        ) {
            when (section) {
                null -> SettingsOverview(container, appViewModel, onOpenAbout) { section = it }
                SettingsSection.ACCOUNT -> SectionPage {
                    AccountSection(container, appViewModel, onChangeInstance, onOpenEmailChange)
                }
                SettingsSection.NOTIFICATIONS -> SectionPage {
                    // Push (Iteration 8f): the card owns the notification-permission request.
                    PushNotificationCard(container)
                    // Direct mailbox push (Iteration 8h): signed-in only, it is per-account.
                    val sessionState by appViewModel.uiState.collectAsState()
                    if (sessionState.loggedIn) MailboxPushCard(container)
                }
                SettingsSection.WATCHES -> SectionPage {
                    WatchesSection(container, appViewModel, onOpenWearWorkoutInbox)
                }
                SettingsSection.PRIVACY -> SectionPage {
                    SettingsCard("Privacy zones", "Hide the start and end of your activities near places you choose.") {
                        OutlinedButton(onClick = onOpenPrivacyZones, modifier = Modifier.fillMaxWidth()) {
                            Text("Manage privacy zones")
                        }
                    }
                }
                SettingsSection.DATA -> SectionPage {
                    SettingsCard("Import", "Bring activities from files or from Komoot.") {
                        OutlinedButton(onClick = onOpenBatchImport, modifier = Modifier.fillMaxWidth()) {
                            Text("Batch import activities")
                        }
                        OutlinedButton(onClick = onOpenKomootImport, modifier = Modifier.fillMaxWidth()) {
                            Text("Import from Komoot")
                        }
                    }
                    SettingsCard("Export", "Download a copy of everything on your account.") {
                        OutlinedButton(onClick = onOpenDataExport, modifier = Modifier.fillMaxWidth()) {
                            Text("Export my data")
                        }
                    }
                }
                SettingsSection.GENERAL -> SectionPage {
                    val unitSystem by appViewModel.unitSystem.collectAsState()
                    SettingsCard("Units") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UnitSystems.ALL.forEach { u ->
                                FilterChip(
                                    selected = unitSystem == u,
                                    onClick = { appViewModel.setUnitSystem(u) },
                                    label = { Text(u.lowercase()) },
                                )
                            }
                        }
                    }
                    CalorieRecordingCard(container.bodyProfileStore)
                }
            }
        }
    }
}

/** The top level: one row per category, with a one-line summary of its current state. */
@Composable
private fun SettingsOverview(
    container: AppContainer,
    appViewModel: AppViewModel,
    onOpenAbout: () -> Unit,
    onOpen: (SettingsSection) -> Unit,
) {
    val sessionState by appViewModel.uiState.collectAsState()
    val unitSystem by appViewModel.unitSystem.collectAsState()
    val watchPending by container.wearWorkoutInboxStore.pendingCount.collectAsState()
    val garminPending by container.garminInboxStore.pendingCount.collectAsState()
    val waiting = watchPending + garminPending
    val host = sessionState.serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')

    SettingsRow(
        Icons.Outlined.Person, "Account",
        when {
            sessionState.loggedIn -> "@${sessionState.username} · $host"
            host.isNotBlank() -> "Guest · $host"
            else -> "Not signed in"
        },
    ) { onOpen(SettingsSection.ACCOUNT) }
    SettingsRow(Icons.Outlined.Notifications, "Notifications", "Push and instant delivery") {
        onOpen(SettingsSection.NOTIFICATIONS)
    }
    SettingsRow(
        Icons.Outlined.Watch, "Watches",
        if (waiting > 0) "$waiting workout(s) waiting to sync" else "Wear OS and Garmin workouts",
    ) { onOpen(SettingsSection.WATCHES) }
    SettingsRow(Icons.Outlined.Lock, "Privacy", "Privacy zones") { onOpen(SettingsSection.PRIVACY) }
    if (sessionState.loggedIn) {
        SettingsRow(Icons.Outlined.Inventory2, "Data", "Import, Komoot, export") { onOpen(SettingsSection.DATA) }
    }
    SettingsRow(Icons.Outlined.Tune, "General", "${unitSystem.lowercase().replaceFirstChar { it.uppercase() }} units · calories") {
        onOpen(SettingsSection.GENERAL)
    }
    SettingsRow(Icons.Outlined.Info, "About", "Version ${BuildConfig.VERSION_NAME} · updates · contact", onClick = onOpenAbout)
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A sub-page body: the cards of one category, with the usual screen padding. */
@Composable
private fun SectionPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** The card shape every sub-page uses: title, optional one-line explanation, then controls. */
@Composable
private fun SettingsCard(title: String, description: String? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (description != null) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun AccountSection(
    container: AppContainer,
    appViewModel: AppViewModel,
    onChangeInstance: () -> Unit,
    onOpenEmailChange: () -> Unit,
) {
    val sessionState by appViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    SettingsCard("Instance", sessionState.serverUrl.ifBlank { "Not configured" }) {
        OutlinedButton(
            onClick = {
                scope.launch {
                    // Tokens are per-instance, so switching instances signs out.
                    container.authRepository.logout()
                    onChangeInstance()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Change instance") }
    }
    if (sessionState.loggedIn) {
        SettingsCard("Sign-in", "@${sessionState.username}") {
            OutlinedButton(onClick = { showChangePassword = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Change password")
            }
            OutlinedButton(onClick = onOpenEmailChange, modifier = Modifier.fillMaxWidth()) {
                Text("Change email address")
            }
            Button(
                onClick = {
                    scope.launch {
                        // Session state drives the UI back to the auth flow.
                        container.authRepository.logout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
        }
        SettingsCard("Danger zone", "Permanently deletes your account and all your activities on this instance.") {
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete account") }
        }
    } else {
        SettingsCard("Sign-in", "You are browsing as a guest.") {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        container.sessionStore.clearGuest()
                        // Guest flag cleared -> MainActivity shows the auth flow.
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign in or create account") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            // Resizable on large screens: not limited to the platform default width.
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This permanently deletes your account and all your " +
                        "activities on this instance. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
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
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
    if (showChangePassword) {
        ChangePasswordDialog(container = container, onDismiss = { showChangePassword = false })
    }
}

@Composable
private fun WatchesSection(
    container: AppContainer,
    appViewModel: AppViewModel,
    onOpenWearWorkoutInbox: () -> Unit,
) {
    val watchPendingCount by container.wearWorkoutInboxStore.pendingCount.collectAsState()
    SettingsCard(
        "Wear OS workouts",
        if (watchPendingCount > 0) "$watchPendingCount watch workout(s) waiting to sync" else "No watch workouts waiting to sync",
    ) {
        OutlinedButton(onClick = onOpenWearWorkoutInbox, modifier = Modifier.fillMaxWidth()) {
            Text("Manage watch workouts ($watchPendingCount)")
        }
    }
    // Garmin (Iteration 10e): only where FP Wear was found or a Garmin workout is still in
    // flight; the dormant card ("Look for FP Wear") only for testers who switched it on in
    // About, on phones with Garmin Connect — everyone else never hears of it.
    val garminActive by GarminLink.active.collectAsState()
    val garminTesting by GarminLink.testing.collectAsState()
    val garminPending by container.garminInboxStore.pendingCount.collectAsState()
    val context = LocalContext.current
    val garminConnect = remember { GarminLink.isAvailable(context) }
    val fpWearCard = garminActive || garminPending > 0 || (garminTesting && garminConnect)
    if (fpWearCard) {
        GarminWatchCard(container.garminInboxStore, dormant = !garminActive && garminPending == 0)
    }
    // Garmin Connect import (PLAN "Garmin Connect API"): for testers until the relay has
    // Garmin's Startup tier; a phone that already has a relay mailbox keeps the card.
    val connectMailbox by container.garminConnectStore.mailbox.collectAsState()
    val connectCard = garminTesting || connectMailbox != null
    if (connectCard) {
        GarminConnectCard(container)
    }
    if (fpWearCard || connectCard) {
        GarminVisibilityCard(container.garminConnectStore)
    }
    HandshakeDiagnosticsCard(container, appViewModel)
}

/**
 * Wear OS pairing diagnostics, collapsed by default: bug reports for watch pairing ask for its
 * state (About → Report a bug), but nobody else needs to see it.
 */
@Composable
private fun HandshakeDiagnosticsCard(container: AppContainer, appViewModel: AppViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Pairing diagnostics") },
            supportingContent = { Text("For Wear OS pairing bug reports") },
            trailingContent = {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            },
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (!expanded) return@Card
        HorizontalDivider()
        Column(modifier = Modifier.padding(14.dp)) {
            val sessionState by appViewModel.uiState.collectAsState()
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val diagnosticsStore = remember { PhoneHandshakeDiagnosticsStore(context) }
            val diagnostics by diagnosticsStore.diagnostics.collectAsState(initial = PhoneHandshakeDiagnostics())
            var reachableNodes by remember { mutableStateOf<List<String>>(emptyList()) }
            var sendReplyFeedback by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                reachableNodes = try {
                    PhoneWearAuthRelay.getReachableWatchNodeIds(context.applicationContext)
                } catch (e: Exception) {
                    emptyList()
                }
            }

            Text("Last watch request (node/path/time):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("node=${diagnostics.lastRequestNodeId.takeIf { it.isNotBlank() } ?: "?"}, path=${diagnostics.lastRequestPath.takeIf { it.isNotBlank() } ?: "?"}, ${formatTimestamp(diagnostics.lastRequestTimestampMs)}", style = MaterialTheme.typography.bodyMedium)
            Text("Last reply: ${if (diagnostics.lastReplyFailed) "FAILED" else if (diagnostics.lastReplySent) "sent ${diagnostics.lastReplyType}" else "not sent"} @ ${formatTimestamp(diagnostics.lastReplyTimestampMs)}", style = MaterialTheme.typography.bodySmall, color = if (diagnostics.lastReplyFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Session: ${if (sessionState.loggedIn) "logged in" else "guest"} (server=${sessionState.serverUrl.takeIf { it.isNotBlank() } ?: "?"}, user=${sessionState.username.takeIf { it.isNotBlank() } ?: "?"})", style = MaterialTheme.typography.bodySmall, color = if (sessionState.loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Reachable watch nodes (best-effort): ${reachableNodes.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
}

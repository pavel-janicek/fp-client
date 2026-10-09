package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fpclient.android.AppContainer
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.wear.PhoneWorkoutSyncScheduler
import com.fpclient.android.wear.WearWorkoutInboxEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDateTime(epochMs: Long): String =
    if (epochMs <= 0L) "" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearWorkoutInboxScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val entries by container.wearWorkoutInboxStore.entries.collectAsState()
    var syncingSessionId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Watch Workouts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = {
                            container.wearWorkoutInboxStore.retryBlocked()
                            PhoneWorkoutSyncScheduler.enqueue(context)
                            scope.launch { snackbarHostState.showSnackbar("Sync triggered for all watch workouts") }
                        }) {
                            Icon(Icons.Filled.Sync, contentDescription = "Sync all")
                        }
                        IconButton(onClick = {
                            container.wearWorkoutInboxStore.clearAll()
                            scope.launch { snackbarHostState.showSnackbar("Cleared all watch workouts") }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Discard all")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (entries.isEmpty()) {
                EmptyState(
                    title = "No watch workouts pending",
                    body = "Workouts recorded on your watch that haven't uploaded to FitPub yet will appear here.",
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${entries.size} workout(s) waiting to sync",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        container.wearWorkoutInboxStore.retryBlocked()
                        PhoneWorkoutSyncScheduler.enqueue(context)
                        scope.launch { snackbarHostState.showSnackbar("Sync triggered") }
                    }) {
                        Text("Sync all")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.sessionId }) { entry ->
                        WorkoutInboxCard(
                            entry = entry,
                            isSyncing = syncingSessionId == entry.sessionId,
                            onSync = {
                                container.wearWorkoutInboxStore.retryBlocked()
                                PhoneWorkoutSyncScheduler.enqueue(context)
                                scope.launch { snackbarHostState.showSnackbar("Sync triggered for '${entry.title}'") }
                            },
                            onDiscard = {
                                container.wearWorkoutInboxStore.remove(entry.sessionId)
                                scope.launch { snackbarHostState.showSnackbar("Discarded '${entry.title}'") }
                            },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutInboxCard(
    entry: WearWorkoutInboxEntry,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title.ifBlank { "${entry.activityType} workout" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = entry.activityType.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (entry.receivedAtEpochMs > 0L) {
                Text(
                    text = "Received: ${formatDateTime(entry.receivedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (entry.ownerUsername.isNotBlank()) {
                Text(
                    text = "Recorded for @${entry.ownerUsername}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            entry.lastError?.let { err ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last error: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSync,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isSyncing) "Syncing…" else "Sync to FitPub")
                }
                Button(
                    onClick = onDiscard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Discard")
                }
            }
        }
    }
}

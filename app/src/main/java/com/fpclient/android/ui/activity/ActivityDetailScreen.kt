package com.fpclient.android.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.viewinterop.AndroidView
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.data.dto.ReactionPalette
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format
import com.fpclient.android.util.TextLimits
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    activityId: String,
    container: AppContainer,
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val vm: ActivityDetailViewModel = viewModel(factory = ActivityDetailViewModel.factory(container, appViewModel))
    val ui by vm.ui.collectAsState()
    val unitSystem by appViewModel.unitSystem.collectAsState()
    LaunchedEffect(activityId) { vm.load(activityId) }

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.activity?.title ?: "Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ui.isOwnActivity) {
                        IconButton(onClick = {
                            editTitle = ui.activity?.title.orEmpty()
                            editDescription = ui.activity?.description.orEmpty()
                            showEditDialog = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit activity")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete activity")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingIndicator(Modifier.padding(padding))
            ui.error != null -> {
                // Federated (remote) activities return 404 — the server only exposes
                // local activities via GET /api/activities/{id}. Show a neutral
                // informational state (not a red error) with a Back button instead of
                // a useless Retry: the limitation is FitPub federation, not the app.
                if (ui.errorStatusCode == 404 && ui.activity == null) {
                    ErrorState(
                        message = "Activity not accessible yet\n\n" +
                            "This activity lives on a different FitPub server.\n" +
                            "FitPub is gradually adding federation features, and viewing activity details across servers isn't supported at the moment.\n\n" +
                            "It's not an app error — the feature will appear once FitPub enables it.",
                        onRetry = onBack,
                        buttonLabel = "Back",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    ErrorState(
                        message = ui.error,
                        onRetry = { vm.load(activityId) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
            else -> DetailBody(
                activityId = activityId,
                viewModel = vm,
                ui = ui,
                unitSystem = unitSystem,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit activity") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it.take(TextLimits.ACTIVITY_TITLE) },
                        label = { Text("Title") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it.take(TextLimits.ACTIVITY_DESCRIPTION) },
                        label = { Text("Description") },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        vm.updateActivity(
                            activityId,
                            ActivityUpdateRequest(
                                title = editTitle,
                                description = editDescription,
                                // PUT replaces all metadata; the server requires visibility, so
                                // preserve the activity's current value (the dialog only edits title/description).
                                visibility = ui.activity?.visibility ?: ActivityVisibilities.PUBLIC,
                            ),
                        )
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete activity") },
            text = { Text("This activity will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteActivity(activityId) { onBack() }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

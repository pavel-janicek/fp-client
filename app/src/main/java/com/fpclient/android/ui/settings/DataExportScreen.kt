package com.fpclient.android.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.DataExportRepository
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Data export (Settings → Data → Export my data).
 *
 * The instance builds the archive in the background and keeps it downloadable for a while;
 * this screen covers both halves — asking for one, and streaming a finished one to storage
 * the user picks. Two server-side facts shape the copy here and are explained in full on
 * [DataExportRepository]: there is no status route (so readiness is discovered by asking for
 * the archive, and a fresh request cannot be told apart from "an export is already being
 * built"), and the archive is served from the web client's own routes, not a JSON API.
 */
class DataExportViewModel(private val repository: DataExportRepository) : ViewModel() {

    data class UiState(
        val requesting: Boolean = false,
        val requested: Boolean = false,
        val downloading: Boolean = false,
        val writtenBytes: Long = 0L,
        val totalBytes: Long? = null,
        val savedBytes: Long? = null,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /**
     * Asks the instance for an archive. The app always confirms the replacement (it cannot
     * see whether a ready archive exists) and reports the generic outcome, because the server
     * answers "accepted" and "already being built" identically — both leave the user with an
     * archive on the way.
     */
    fun requestExport() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(requesting = true, requested = false, error = null)
            when (val result = repository.requestExport()) {
                is ApiResult.Success ->
                    _ui.value = _ui.value.copy(requesting = false, requested = true)
                is ApiResult.Error ->
                    _ui.value = _ui.value.copy(requesting = false, error = result.message)
            }
        }
    }

    /**
     * Streams the archive into the document the user picked in the system file picker. The
     * stream is opened — and always closed — here rather than in the composable, so leaving
     * the screen mid-download cannot leak an open SAF document.
     */
    fun downloadTo(context: Context, target: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                downloading = true,
                error = null,
                savedBytes = null,
                writtenBytes = 0L,
                totalBytes = null,
            )
            val result: ApiResult<Long> = withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(target)
                        ?: return@withContext ApiResult.Error("Couldn't open the chosen file for writing.")
                    stream.use { output ->
                        repository.downloadArchive(output) { written, total ->
                            _ui.value = _ui.value.copy(writtenBytes = written, totalBytes = total)
                        }
                    }
                } catch (e: Exception) {
                    ApiResult.Error("Couldn't save the archive: ${e.message ?: "unknown error"}")
                }
            }
            when (result) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    downloading = false,
                    savedBytes = result.data,
                    writtenBytes = result.data,
                )
                is ApiResult.Error ->
                    _ui.value = _ui.value.copy(downloading = false, error = result.message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DataExportViewModel(container.dataExportRepository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val vm: DataExportViewModel = viewModel(factory = DataExportViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    val sessionState by appViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // The instance names the archive itself (`fitpub-data-export-<user>-<date>.zip`), but that
    // name arrives *with* the response — long after the picker has asked for one. The
    // suggested name is therefore built locally from the signed-in account.
    val suggestedName = sessionState.username.takeIf { it.isNotBlank() }
        ?.let { "fitpub-data-export-$it.zip" }
        ?: "fitpub-data-export.zip"

    // Lets the user pick where the ZIP goes (Storage Access Framework); the archive is
    // streamed from the instance only after a location is chosen.
    val saveArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.downloadTo(context, uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export data") },
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
                    Text("Your archive", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A ZIP with your profile, settings, privacy zones, activities and their " +
                            "complete tracks, the original FIT/GPX/TCX files, avatar, social data, " +
                            "notifications, analytics, weather, peaks and import history. " +
                            "Credentials and regenerable server caches are not included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // Shown above the actions rather than under them: both cards can fail, and a
            // failure is only useful if it is on screen without scrolling.
            ui.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ExportDownloadCard(
                ui = ui,
                onDownload = { saveArchive.launch(suggestedName) },
            )
            ExportRequestCard(
                ui = ui,
                onRequest = vm::requestExport,
            )
        }
    }
}

/**
 * Download half: the instance has no status route, so the app cannot ask whether an archive
 * is ready — requesting the file *is* the check, and a `404` is the honest answer to show.
 */
@Composable
private fun ExportDownloadCard(
    ui: DataExportViewModel.UiState,
    onDownload: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Download archive", style = MaterialTheme.typography.titleSmall)
            Text(
                "Saves the instance's finished archive, streaming it straight to the location " +
                    "you pick. If it answers that nothing is available, request an export below " +
                    "and come back when the notification arrives.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onDownload,
                enabled = !ui.downloading && !ui.requesting,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (ui.downloading) "Downloading…" else "Download archive") }
            if (ui.downloading) {
                val total = ui.totalBytes
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { ui.writtenBytes.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "${Format.bytes(ui.writtenBytes)} of ${Format.bytes(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    // No Content-Length to measure against — keep it moving, not stuck at 0%.
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            ui.savedBytes?.let {
                Text(
                    "Saved ${Format.bytes(it)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Request half. The copy avoids claiming the request was *accepted*: the server answers a
 * fresh request and "an export is already being built" with the same redirect, and the reason
 * lives in a flash message bound to a session this stateless client does not have. Either way
 * the user is waiting for the same notification, so that is what the screen promises.
 */
@Composable
private fun ExportRequestCard(
    ui: DataExportViewModel.UiState,
    onRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Request an export", style = MaterialTheme.typography.titleSmall)
            Text(
                "The instance builds the archive in the background, which takes a while for a " +
                    "large account, and notifies you when it is ready. An archive that already " +
                    "exists stays downloadable until the new one has been built, then it is " +
                    "replaced.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = onRequest,
                enabled = !ui.requesting && !ui.downloading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (ui.requesting) "Requesting…" else "Request data export") }
            if (ui.requested) {
                Text(
                    "Requested — or an export was already being built; the instance does not say " +
                        "which. You'll be notified when the archive is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}


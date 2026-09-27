package com.fpclient.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.BatchImportJobDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.BatchImportRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BatchImportViewModel(private val repository: BatchImportRepository) : ViewModel() {

    data class UiState(
        val uploading: Boolean = false,
        val error: String? = null,
        val activeJob: BatchImportJobDto? = null,
        val history: List<BatchImportJobDto> = emptyList(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        refreshHistory()
    }

    fun upload(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(uploading = true, error = null)
            when (val r = repository.upload(context, uri)) {
                is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(uploading = false)
                    poll(r.data.id ?: return@launch)
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(uploading = false, error = r.message)
            }
        }
    }

    /** Polls the server job every 2s while it is still pending/processing. */
    private fun poll(jobId: String) {
        viewModelScope.launch {
            var done = false
            while (!done) {
                delay(2000)
                when (val s = repository.status(jobId)) {
                    is ApiResult.Success -> {
                        _ui.value = _ui.value.copy(activeJob = s.data)
                        done = s.data.status != "PENDING" && s.data.status != "PROCESSING"
                    }
                    is ApiResult.Error -> {
                        _ui.value = _ui.value.copy(error = s.message)
                        done = true
                    }
                }
            }
            refreshHistory()
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            when (val j = repository.jobs()) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(history = j.data.content)
                else -> Unit
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BatchImportViewModel(container.batchImportRepository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: BatchImportViewModel = viewModel(factory = BatchImportViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.upload(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch import") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Upload a GPX/FIT export archive (e.g., a Strava bulk export). " +
                    "The server processes it in the background; keep this screen open to watch progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { filePicker.launch("*/*") },
                enabled = !ui.uploading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (ui.uploading) "Uploading…" else "Choose file & import")
            }
            if (ui.uploading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            val job = ui.activeJob
            if (job != null && job.totalFiles > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Current import", style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(
                            progress = { job.processedFiles.toFloat() / job.totalFiles.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Text(
                            "${job.status} · ${job.processedFiles}/${job.totalFiles} files · ${job.successCount} ok · ${job.failedCount} failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            Text("Previous imports", style = MaterialTheme.typography.titleSmall)
            if (ui.history.isEmpty() && !ui.uploading) {
                Text(
                    "No imports yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ui.history.forEach { j ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(j.createdAt ?: "Import", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${j.status} · ${j.successCount} ok · ${j.failedCount} failed of ${j.totalFiles}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
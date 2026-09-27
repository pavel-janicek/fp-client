package com.fpclient.android.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.PersonalRecordDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.AnalyticsRepository
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.util.Format
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Personal records — the destination behind the Analytics "Personal records" tile.
 *
 * The tile previously showed a count that led nowhere, which is the worst of both
 * worlds: a number that looks like a link and is not. The full list was already being
 * fetched by `AnalyticsRepository.personalRecords()` and never called, so this screen
 * needs no new endpoint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    container: AppContainer,
    unitSystem: String,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit = {},
) {
    // Loaded here rather than passed in: this is its own route, so it cannot share the
    // Analytics tab's ViewModel, and it must work when opened from anywhere.
    val vm: RecordsViewModel = viewModel(factory = RecordsViewModel.factory(container))
    val state by vm.state.collectAsState()
    when {
        state.loading -> {
            LoadingIndicator()
            return
        }
        state.error != null -> {
            ErrorState(message = state.error, onRetry = vm::load)
            return
        }
    }
    val records = state.records
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal records") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                EmptyState(title = "No personal records yet")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(records.size) { index ->
                val record = records[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            // Tapping a record opens the activity that set it, when the
                            // server gave us one to open.
                            if (record.activityId.isNullOrBlank()) {
                                Modifier
                            } else {
                                Modifier.clickable { onOpenActivity(record.activityId) }
                            },
                        ),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = record.recordType?.replace('_', ' ')?.lowercase()
                                    ?.replaceFirstChar { it.uppercase() } ?: "Record",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            record.achievedAt?.let {
                                Text(
                                    text = Format.relative(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = recordValue(record, unitSystem),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        record.activityType?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a record value with the unit the server sent. Distances are the one metric where
 * the user's unit preference should win, so a metric distance is converted rather than
 * printed in the server's unit.
 */
private fun recordValue(record: PersonalRecordDto, unitSystem: String): String {
    val value = record.value ?: return "—"
    val unit = record.unit.orEmpty()
    val isDistance = unit.equals("m", true) || unit.equals("meter", true) ||
        unit.equals("meters", true) || unit.equals("km", true)
    return if (isDistance) Format.distance(value, unitSystem) else "$value $unit".trim()
}


/**
 * Loads the personal records for [RecordsScreen]. Tiny by design: one request, no caching,
 * because the list is small and a stale count is worse than a spinner.
 */
private class RecordsViewModel(private val repository: AnalyticsRepository) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val records: List<PersonalRecordDto> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            _state.value = when (val result = repository.personalRecords()) {
                is ApiResult.Success -> State(loading = false, records = result.data)
                is ApiResult.Error -> State(loading = false, error = result.message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecordsViewModel(container.analyticsRepository) }
        }
    }
}

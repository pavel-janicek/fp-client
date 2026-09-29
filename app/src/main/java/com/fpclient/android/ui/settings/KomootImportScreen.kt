package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.data.dto.KomootActivitiesResponse
import com.fpclient.android.data.dto.KomootActivityImportRequest
import com.fpclient.android.data.dto.KomootActivitySummaryDto
import com.fpclient.android.data.dto.KomootImportRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.KomootImportRepository
import com.fpclient.android.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Komoot import.
 *
 * The Komoot password lives in this ViewModel's in-memory [UiState] and nowhere else: it is
 * not persisted, not put in a `SavedStateHandle` (which would survive process death and get
 * written into a saved-state bundle), and never logged. The server uses it for one request
 * and stores nothing either.
 */
class KomootImportViewModel(
    private val repository: KomootImportRepository,
    private val onActivitiesAdded: () -> Unit,
) : ViewModel() {

    data class UiState(
        val email: String = "",
        val password: String = "",
        val userId: String = "",
        val useDateRange: Boolean = false,
        val startDate: String? = null,
        val endDate: String? = null,
        val loading: Boolean = false,
        /** Set when the instance has `fitpub.komoot.enabled` off — the form is then pointless. */
        val disabledOnInstance: Boolean = false,
        val error: String? = null,
        val result: KomootActivitiesResponse? = null,
        /** Komoot activity id currently being imported, for the per-row spinner. */
        val importingId: Long? = null,
        /** How many activities this visit imported, for the progress line. */
        val importedCount: Int = 0,
    ) {
        val canLoad: Boolean
            get() = email.isNotBlank() && password.isNotBlank() && userId.isNotBlank() && !loading

        val pendingCount: Int
            get() = result?.activities?.count { !it.imported } ?: 0
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun onEmailChange(value: String) {
        _ui.value = _ui.value.copy(email = value)
    }

    fun onPasswordChange(value: String) {
        _ui.value = _ui.value.copy(password = value)
    }

    fun onUserIdChange(value: String) {
        _ui.value = _ui.value.copy(userId = value)
    }

    fun onUseDateRangeChange(enabled: Boolean) {
        // All-or-nothing: the server rejects a request carrying only one of the two dates.
        _ui.value = if (enabled) {
            _ui.value.copy(useDateRange = true)
        } else {
            _ui.value.copy(useDateRange = false, startDate = null, endDate = null)
        }
    }

    fun onStartDateChange(value: String) {
        _ui.value = _ui.value.copy(startDate = value)
    }

    fun onEndDateChange(value: String) {
        _ui.value = _ui.value.copy(endDate = value)
    }

    /** Loads the preview list. The password is only sent with this request. */
    fun load() {
        val state = _ui.value
        if (!state.canLoad) return
        viewModelScope.launch {
            _ui.value = state.copy(loading = true, error = null)
            when (val r = repository.activities(state.toRequest())) {
                is ApiResult.Success ->
                    _ui.value = _ui.value.copy(loading = false, result = r.data, error = null)
                is ApiResult.Error -> _ui.value = _ui.value.copy(
                    loading = false,
                    error = r.message,
                    disabledOnInstance = r === KomootImportRepository.KomootDisabled,
                )
            }
        }
    }

    /**
     * Imports every activity the server has not already imported, one at a time.
     *
     * Sequential on purpose: the server sleeps `fitpub.komoot.activity-import-delay-ms`
     * (default 3 s) inside each import to respect Komoot's rate limits, so firing them in
     * parallel would only earn a rate-limit response from Komoot.
     */
    fun importAll() {
        val state = _ui.value
        val pending = state.result?.activities?.filterNot { it.imported }.orEmpty()
        if (pending.isEmpty() || state.importingId != null) return
        viewModelScope.launch {
            var imported = 0
            for (activity in pending) {
                _ui.value = _ui.value.copy(importingId = activity.id, error = null)
                when (val r = repository.importActivity(state.toImportRequest(activity))) {
                    is ApiResult.Success -> {
                        // Mark it in place so the row flips to "Imported" without a reload.
                        markImported(activity.id)
                        imported++
                    }
                    is ApiResult.Error -> {
                        _ui.value = _ui.value.copy(
                            importingId = null,
                            error = r.message,
                            disabledOnInstance = r === KomootImportRepository.KomootDisabled,
                        )
                        // Stop on the first failure: retrying blindly would hammer Komoot.
                        return@launch
                    }
                }
            }
            _ui.value = _ui.value.copy(importingId = null, importedCount = imported)
            if (imported > 0) onActivitiesAdded()
        }
    }

    /** Forgets the Komoot password and the loaded list. */
    fun clear() {
        _ui.value = _ui.value.copy(password = "", result = null)
    }

    private fun markImported(komootId: Long) {
        val result = _ui.value.result ?: return
        _ui.value = _ui.value.copy(
            result = result.copy(
                activities = result.activities.map {
                    if (it.id == komootId) it.copy(imported = true) else it
                },
            ),
        )
    }

    private fun UiState.toRequest() = KomootImportRequest(
        email = email.trim(),
        password = password,
        userId = userId.trim(),
        startDate = if (useDateRange) startDate else null,
        endDate = if (useDateRange) endDate else null,
    )

    private fun UiState.toImportRequest(activity: KomootActivitySummaryDto) =
        KomootActivityImportRequest(
            email = email.trim(),
            password = password,
            userId = userId.trim(),
            activityId = activity.id,
        )

    companion object {
        fun factory(container: AppContainer, onActivitiesAdded: () -> Unit): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    KomootImportViewModel(container.komootImportRepository, onActivitiesAdded)
                }
            }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomootImportScreen(
    container: AppContainer,
    unitSystem: String,
    onBack: () -> Unit,
    onActivitiesAdded: () -> Unit,
) {
    val vm: KomootImportViewModel = viewModel(
        factory = KomootImportViewModel.factory(container, onActivitiesAdded),
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Komoot") },
                navigationIcon = {
                    IconButton(onClick = {
                        // Drop the Komoot password before leaving the screen.
                        vm.clear()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui.disabledOnInstance) {
                item { DisabledCard() }
            } else {
                item { CredentialsCard(vm, ui) }
                ui.error?.let { message ->
                    item {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (ui.loading) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
                ui.result?.let { result -> resultItems(result, ui, vm, unitSystem) }
            }
        }
    }
}

@Composable
private fun CredentialsCard(vm: KomootImportViewModel, ui: KomootImportViewModel.UiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Your Komoot account", style = MaterialTheme.typography.titleMedium)
            Text(
                "These are sent to your own FitPub server, which uses them for this one " +
                    "request and stores nothing. Find your Komoot ID in your Komoot account " +
                    "settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = ui.email,
                onValueChange = vm::onEmailChange,
                label = { Text("Komoot email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.password,
                onValueChange = vm::onPasswordChange,
                label = { Text("Komoot password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = ui.userId,
                onValueChange = vm::onUserIdChange,
                label = { Text("Komoot ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            FilterChip(
                selected = ui.useDateRange,
                onClick = { vm.onUseDateRangeChange(!ui.useDateRange) },
                label = { Text("Limit to a date range") },
                modifier = Modifier.padding(top = 8.dp),
            )
            if (ui.useDateRange) {
                Text(
                    "Both dates are required — the server rejects a range with only one end.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = ui.startDate.orEmpty(),
                        onValueChange = vm::onStartDateChange,
                        label = { Text("From (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = ui.endDate.orEmpty(),
                        onValueChange = vm::onEndDateChange,
                        label = { Text("To (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val rangeIncomplete = ui.useDateRange &&
                (ui.startDate.isNullOrBlank() || ui.endDate.isNullOrBlank())
            Button(
                onClick = { vm.load() },
                enabled = ui.canLoad && !rangeIncomplete,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Load activities") }
        }
    }
}


@Composable
private fun DisabledCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Not available on this instance", style = MaterialTheme.typography.titleMedium)
            Text(
                "The server administrator has not enabled Komoot import " +
                    "(fitpub.komoot.enabled). Nothing to do here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(
    result: KomootActivitiesResponse,
    ui: KomootImportViewModel.UiState,
    vm: KomootImportViewModel,
    unitSystem: String,
) {
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${result.totalCount} activities",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (ui.importedCount > 0) {
                Text(
                    "${ui.importedCount} imported",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (ui.pendingCount > 0) {
        item {
            Text(
                "Importing can take a few seconds per activity — the server spaces Komoot " +
                    "requests out to stay inside their rate limits.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = { vm.importAll() },
                enabled = ui.importingId == null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Import ${ui.pendingCount} activities") }
        }
    }
    if (ui.importingId != null) {
        item {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
    if (result.activities.isEmpty()) {
        item {
            Text(
                "No completed activities in that range.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    items(result.activities, key = { it.id }) { activity ->
        KomootActivityRow(
            activity = activity,
            unitSystem = unitSystem,
            importing = ui.importingId == activity.id,
            anyImporting = ui.importingId != null,
        )
    }
}

@Composable
private fun KomootActivityRow(
    activity: KomootActivitySummaryDto,
    unitSystem: String,
    importing: Boolean,
    anyImporting: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // The server already mapped Komoot's sport to a FitPub activity type, so
                    // the same emoji the rest of the app uses comes for free.
                    ActivityTypes.icon(activity.mappedActivityType) + " " +
                        (activity.name?.takeIf { it.isNotBlank() } ?: "Untitled activity"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    buildString {
                        append(Format.dateTime(activity.date))
                        append(" · ")
                        append(Format.distanceShort(activity.distanceMeters))
                        if (activity.durationSeconds != null) {
                            append(" · ")
                            append(Format.duration(activity.durationSeconds.toLong()))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (activity.elevationUp != null && activity.elevationUp > 0.0) {
                    Text(
                        "↑ " + Format.elevation(activity.elevationUp, unitSystem),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                importing -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                activity.imported -> Text(
                    "Imported",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Nothing per-row: the list imports as a batch, so a row-level button would
                // suggest a control that does not exist.
                anyImporting -> Unit
            }
        }
    }
}

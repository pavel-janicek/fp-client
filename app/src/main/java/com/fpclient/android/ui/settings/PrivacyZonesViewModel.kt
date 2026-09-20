package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.PrivacyZoneDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.PrivacyZoneRepository
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrivacyZonesViewModel(private val repository: PrivacyZoneRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        /** Failure of a single action (toggle/delete) — shown above the list instead of
         * replacing it with a full-screen error state. */
        val actionError: String? = null,
        val zones: List<PrivacyZoneDto> = emptyList(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null, actionError = null)
            when (val r = repository.list()) {
                is ApiResult.Success -> _ui.value = UiState(zones = r.data)
                is ApiResult.Error -> _ui.value = UiState(error = r.message)
            }
        }
    }

    fun create(name: String, lat: Double, lon: Double, radiusMeters: Int) {
        viewModelScope.launch {
            repository.create(name, lat, lon, radiusMeters)
            refresh()
        }
    }

    /**
     * Sets the zone's active state. The server's toggle endpoint is a setter — it takes the
     * desired state in the request body — so the switch's new value is forwarded verbatim.
     */
    fun setActive(id: String, isActive: Boolean) {
        viewModelScope.launch {
            when (val r = repository.toggle(id, isActive)) {
                is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(actionError = null)
                    refresh()
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(
                    actionError = r.message ?: "Could not update the privacy zone.",
                )
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            when (val r = repository.delete(id)) {
                is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(actionError = null)
                    refresh()
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(
                    actionError = r.message ?: "Could not delete the privacy zone.",
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PrivacyZonesViewModel(container.privacyZoneRepository) }
        }
    }
}

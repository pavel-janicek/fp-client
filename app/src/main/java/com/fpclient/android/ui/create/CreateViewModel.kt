package com.fpclient.android.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.data.dto.ManualActivityRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.ActivityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateViewModel(
    private val activities: ActivityRepository,
    private val activitiesVersion: MutableStateFlow<Int>,
) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun uploadFile(context: android.content.Context, uri: Uri, title: String?, description: String?, visibility: String?) {
        viewModelScope.launch {
            _ui.value = UiState(busy = true)
            when (val r = activities.uploadFile(context, uri, title, description, visibility)) {
                is ApiResult.Success -> {
                    activitiesVersion.value += 1
                    _ui.value = UiState(done = true)
                }
                is ApiResult.Error -> _ui.value = UiState(error = r.message)
            }
        }
    }

    fun createManual(request: ManualActivityRequest) {
        viewModelScope.launch {
            _ui.value = UiState(busy = true)
            when (val r = activities.createManual(request)) {
                is ApiResult.Success -> {
                    activitiesVersion.value += 1
                    _ui.value = UiState(done = true)
                }
                is ApiResult.Error -> _ui.value = UiState(error = r.message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreateViewModel(container.activityRepository, container.activitiesVersion) }
        }
    }
}

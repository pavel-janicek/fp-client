package com.fpclient.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fpclient.android.data.dto.UserPeakDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.UserRepository
import com.fpclient.android.ui.activity.MapCanvas
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.util.Format
import com.fpclient.android.util.TrackParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/**
 * One summit: its facts, and the activities that reached it drawn on a map.
 *
 * The tracks arrive privacy-filtered from the server — indoor activities, tracks hidden by
 * `showMap`, and points inside privacy zones are already removed — so nothing here needs to
 * second-guess what it was sent. The route is raw GeoJSON (a `Map` in the server's DTO), which
 * [TrackParser] already understands.
 */
class PeakDetailViewModel(
    private val users: UserRepository,
    private val username: String,
    private val peakId: Long,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val peak: UserPeakDto? = null,
        val segments: List<List<GeoPoint>> = emptyList(),
        /** How many activities reached the peak; the map shows the sum of their tracks. */
        val activityCount: Int = 0,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            when (val p = users.peak(username, peakId)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(loading = false, peak = p.data)
                is ApiResult.Error -> {
                    _ui.value = _ui.value.copy(loading = false, error = p.message)
                    return@launch
                }
            }
            when (val t = users.peakTracks(username, peakId)) {
                is ApiResult.Success -> {
                    val segments = t.data.flatMap { track ->
                        val type = track.route?.get("type")?.toString()?.trim('"')
                        val coordinates = track.route?.get("coordinates")
                        TrackParser.fromGeometry(type, coordinates)
                    }
                    _ui.value = _ui.value.copy(
                        segments = segments,
                        activityCount = t.data.size,
                    )
                }
                // The peak itself loaded, so a track failure should not blank the screen.
                is ApiResult.Error -> Unit
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            username: String,
            peakId: Long,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PeakDetailViewModel(container.userRepository, username, peakId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeakDetailScreen(
    container: AppContainer,
    username: String,
    peakId: Long,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
) {
    val vm: PeakDetailViewModel = viewModel(
        factory = PeakDetailViewModel.factory(container, username, peakId),
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.peak?.name?.takeIf { it.isNotBlank() } ?: "Peak") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            ui.loading -> LoadingIndicator(modifier = Modifier.padding(padding))
            ui.error != null && ui.peak == null ->
                ErrorState(message = ui.error, onRetry = { vm.load() }, modifier = Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ui.peak?.let { peak ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(peak.name ?: "Unnamed peak", style = MaterialTheme.typography.titleMedium)
                            val details = buildString {
                                append(peak.kindLabel)
                                peak.elevation?.let {
                                    append(" · ")
                                    append(Format.elevation(it.toDouble(), "METRIC"))
                                }
                                if (peak.visitCount > 0) {
                                    append(" · ")
                                    append(if (peak.visitCount == 1L) "1 visit" else "${peak.visitCount} visits")
                                }
                                peak.latestVisitedAt?.let {
                                    append(" · ")
                                    append(Format.date(it))
                                }
                            }
                            Text(
                                details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            peak.latestActivityId?.let { activityId ->
                                TextButton(
                                    onClick = { onOpenActivity(activityId) },
                                    modifier = Modifier.padding(top = 4.dp),
                                ) { Text("Open the latest activity") }
                            }
                        }
                    }
                }
                if (ui.segments.isNotEmpty()) {
                    MapCanvas(
                        segments = ui.segments,
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                    Text(
                        // Tracks arrive already filtered server-side; say so rather than
                        // implying every visit is drawn.
                        "Tracks of ${ui.activityCount} " +
                            (if (ui.activityCount == 1) "activity" else "activities") +
                            " that reached this peak. Indoor activities and hidden tracks " +
                            "are not shown.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (ui.peak != null) {
                    Text(
                        "No track to draw for this peak — the activities that reached it may " +
                            "be indoor, private, or have map sharing turned off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

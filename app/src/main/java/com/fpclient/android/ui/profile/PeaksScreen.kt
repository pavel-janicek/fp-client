package com.fpclient.android.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
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
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The full list of summits reached, paged 24 at a time like the server's own page.
 *
 * An empty list is deliberately shown as "no peaks to show" rather than an error: the server
 * hides peaks from viewers unless the owner shares them, and it does that by answering 200
 * with an empty list.
 */
class PeaksViewModel(
    private val users: UserRepository,
    private val username: String,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val peaks: List<UserPeakDto> = emptyList(),
        val page: Int = 0,
        val totalPages: Int = 0,
        val total: Long = 0,
    ) {
        val hasMore: Boolean get() = page + 1 < totalPages
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            when (val r = users.peaksPage(username, 0)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    loading = false,
                    peaks = r.data.content,
                    page = r.data.number,
                    totalPages = r.data.totalPages,
                    total = r.data.totalElements,
                )
                is ApiResult.Error -> _ui.value = _ui.value.copy(loading = false, error = r.message)
            }
        }
    }

    /** Appends the next page; the server pages alphabetically, so order is preserved. */
    fun loadMore() {
        val state = _ui.value
        if (state.loadingMore || !state.hasMore) return
        viewModelScope.launch {
            _ui.value = state.copy(loadingMore = true)
            when (val r = users.peaksPage(username, state.page + 1)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    loadingMore = false,
                    peaks = _ui.value.peaks + r.data.content,
                    page = r.data.number,
                    totalPages = r.data.totalPages,
                )
                is ApiResult.Error -> _ui.value = _ui.value.copy(loadingMore = false, error = r.message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, username: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { PeaksViewModel(container.userRepository, username) }
            }
    }
}

/** A card for one summit, shared by the profile's recent list and the full page. */
@Composable
internal fun PeakRow(
    peak: UserPeakDto,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    peak.name?.takeIf { it.isNotBlank() } ?: "Unnamed peak",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val details = buildString {
                    append(peak.kindLabel)
                    peak.elevation?.let {
                        append(" · ")
                        append(Format.elevation(it.toDouble(), "METRIC"))
                    }
                    if (peak.visitCount > 1) {
                        append(" · ")
                        append("${peak.visitCount} visits")
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
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeaksScreen(
    container: AppContainer,
    username: String,
    onBack: () -> Unit,
    onOpenPeak: (Long) -> Unit,
) {
    val vm: PeaksViewModel = viewModel(factory = PeaksViewModel.factory(container, username))
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Peaks") },
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
            ui.error != null && ui.peaks.isEmpty() ->
                ErrorState(message = ui.error, onRetry = { vm.load() }, modifier = Modifier.padding(padding))
            ui.peaks.isEmpty() -> EmptyState(
                title = "No peaks to show",
                body = "Summits appear here once an activity reaches one. If this is " +
                    "someone else's profile, they may keep their peaks private.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "${ui.total} peaks",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                items(ui.peaks, key = { it.id }) { peak ->
                    PeakRow(peak = peak, onClick = { onOpenPeak(peak.id) })
                }
                if (ui.loadingMore || ui.hasMore) {
                    item {
                        TextButton(
                            onClick = { vm.loadMore() },
                            enabled = !ui.loadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (ui.loadingMore) "Loading…" else "Load more") }
                    }
                }
            }
        }
    }
}


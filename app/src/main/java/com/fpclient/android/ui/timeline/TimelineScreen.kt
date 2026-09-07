package com.fpclient.android.ui.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.TimelineActivityDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.TimelineRepository
import com.fpclient.android.data.repository.TimelineTab
import com.fpclient.android.data.session.SessionStore
import com.fpclient.android.ui.components.ActivityCard
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        /** True while a pull-to-refresh / top-bar refresh is re-fetching page 0. */
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val activities: List<TimelineActivityDto> = emptyList(),
        val hasMore: Boolean = true,
        val serverUrl: String = "",
        val unitSystem: String = "METRIC",
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _tab = MutableStateFlow(TimelineTab.FEDERATED)
    val tab = _tab.asStateFlow()

    private val _search = MutableStateFlow("")
    val search = _search.asStateFlow()

    init {
        refresh()
    }

    fun setTab(tab: TimelineTab) {
        if (_tab.value != tab) {
            _tab.value = tab
            _ui.value = _ui.value.copy(activities = emptyList())
            load()
        }
    }

    fun setSearch(query: String) {
        _search.value = query
    }

    fun submitSearch() {
        _search.value = _search.value.trim()
        load()
    }

    fun refresh() {
        viewModelScope.launch {
            val session = sessionStore.session.first()
            if (session.serverUrl.isNotBlank()) {
                _ui.value = _ui.value.copy(serverUrl = session.serverUrl, unitSystem = "METRIC")
            }
            // Guests have no followed athletes, so start them on the public timeline.
            if (session.guest && _tab.value == TimelineTab.FEDERATED) {
                _tab.value = TimelineTab.PUBLIC
            }
            _ui.value = _ui.value.copy(isRefreshing = true)
            load()
        }
    }

    private fun load() {
        val current = _tab.value
        val query = _search.value.ifBlank { null }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, loadingMore = false, error = null)
            when (val result = repository.load(current, page = 0, search = query)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    loading = false,
                    isRefreshing = false,
                    activities = result.data,
                    hasMore = result.data.size >= PAGE_SIZE,
                )
                is ApiResult.Error -> _ui.value = _ui.value.copy(
                    loading = false,
                    isRefreshing = false,
                    error = result.message,
                )
            }
        }
    }

    fun loadMore() {
        val st = _ui.value
        if (st.loading || st.loadingMore || !st.hasMore) return
        val current = _tab.value
        val query = _search.value.ifBlank { null }
        val nextPage = st.activities.size / PAGE_SIZE
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loadingMore = true)
            when (val result = repository.load(current, page = nextPage, search = query)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    loadingMore = false,
                    activities = (st.activities + result.data).distinctBy { it.id },
                    hasMore = result.data.size >= PAGE_SIZE,
                )
                is ApiResult.Error -> _ui.value = _ui.value.copy(loadingMore = false, error = result.message)
            }
        }
    }

    companion object {
        /** Must match the server's fixed page size (see TimelineRepository.load). */
        private const val PAGE_SIZE = 20

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TimelineViewModel(container.timelineRepository, container.sessionStore) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    container: AppContainer,
    unitSystem: String,
    guestMode: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenActivity: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onRequireSignIn: () -> Unit = {},
) {
    val vm: TimelineViewModel = viewModel(factory = TimelineViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    val tab by vm.tab.collectAsState()
    val search by vm.search.collectAsState()
    val serverUrl = ui.serverUrl
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    // Re-fetch when an activity was created locally so the fresh entry shows up right
    // away instead of looking like the save "didn't stick".
    val activitiesVersion by container.activitiesVersion.collectAsState()
    LaunchedEffect(activitiesVersion) {
        if (activitiesVersion > 0) vm.refresh()
    }

    Scaffold(
        modifier = modifier,
        // The parent scaffold already pads this screen above the bottom navigation bar;
        // re-applying the system bar insets here doubled the bottom space ("empty screen").
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("FP Client") },
                actions = {
                    // Toggles the "Search activities" input; sits left of the reload icon.
                    IconButton(
                        onClick = {
                            if (searchVisible) {
                                searchVisible = false
                                // Closing the search clears the filter so the feed is not
                                // left narrowed with no visible reason.
                                if (search.isNotBlank()) {
                                    vm.setSearch("")
                                    vm.submitSearch()
                                }
                            } else {
                                searchVisible = true
                            }
                        },
                    ) {
                        Icon(
                            if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchVisible) "Close search" else "Search activities",
                        )
                    }
                    // Show a spinner while a refresh is in flight so the button gives feedback.
                    IconButton(onClick = { vm.refresh() }) {
                        if (ui.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Guests see the same FAB; tapping it takes them to sign-in since
            // posting requires an account.
            FloatingActionButton(
                onClick = if (guestMode) onRequireSignIn else onOpenCreate,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (guestMode) "Sign in to add an activity" else "New activity",
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Floating feed switches styled like the analytics tab row.
            androidx.compose.material3.ScrollableTabRow(
                selectedTabIndex = TimelineTab.entries.indexOfFirst { it == tab }.coerceAtLeast(0),
                edgePadding = 12.dp,
            ) {
                TimelineTab.entries.forEach { t ->
                    androidx.compose.material3.Tab(
                        selected = tab == t,
                        onClick = { vm.setTab(t) },
                        text = { Text(t.title) },
                    )
                }
            }
            if (searchVisible) {
                OutlinedTextField(
                    value = search,
                    onValueChange = vm::setSearch,
                    label = { Text("Search activities") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submitSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = ui.isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    when {
                        ui.loading && ui.activities.isEmpty() -> {
                            item { LoadingIndicator() }
                        }
                        ui.error != null && ui.activities.isEmpty() -> {
                            item { ErrorState(message = ui.error, onRetry = { vm.refresh() }) }
                        }
                        ui.activities.isEmpty() -> {
                            item {
                                EmptyState(
                                    title = "No activities yet",
                                    body = "When you or the people you follow record workouts they will appear here.",
                                )
                            }
                        }
                        else -> {
                            items(ui.activities, key = { it.id }) { activity ->
                                ActivityCard(
                                    activity = activity,
                                    serverUrl = serverUrl,
                                    unitSystem = unitSystem,
                                    onClick = { onOpenActivity(activity.id) },
                                    onAuthorClick = { handle -> onOpenProfile(handle) },
                                )
                            }
                            if (ui.hasMore) {
                                item(key = "load-more") {
                                    Button(
                                        onClick = { vm.loadMore() },
                                        enabled = !ui.loadingMore,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(if (ui.loadingMore) "Loading…" else "Load more")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.fpclient.android.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.data.dto.UserDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.ui.components.UserAvatar
import com.fpclient.android.util.ActorHandle
import com.fpclient.android.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FollowListViewModel(
    private val users: com.fpclient.android.data.repository.UserRepository,
    private val sessionStore: com.fpclient.android.data.session.SessionStore,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val users: List<UserDto> = emptyList(),
        val serverUrl: String = "",
        /** Per-user in-flight flags (by [UserDto.fullHandle]) so only that row's button disables. */
        val busyUsers: Set<String> = emptySet(),
        /** Local override of follow state (by [UserDto.fullHandle]) applied after toggling. */
        val followOverrides: Map<String, Boolean> = emptyMap(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load(username: String, type: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val session = sessionStore.session.first()
            var error: String? = null
            var list: List<UserDto> = emptyList()
            when (val r = if (type == "following") users.following(username.ifBlank { "me" }) else users.followers(username.ifBlank { "me" })) {
                is ApiResult.Success -> list = r.data
                is ApiResult.Error -> error = r.message
            }
            _ui.value = _ui.value.copy(
                loading = false,
                error = error,
                users = list,
                serverUrl = session.serverUrl,
            )
        }
    }

    fun toggle(user: UserDto) {
        val key = user.fullHandle
        val target = followTarget(user, _ui.value.serverUrl) ?: return
        val currentlyFollowing = _ui.value.followOverrides[key] ?: (user.isFollowing == true)
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busyUsers = _ui.value.busyUsers + key, error = null)
            val result = if (currentlyFollowing) users.unfollow(target) else users.follow(target)
            when (result) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    busyUsers = _ui.value.busyUsers - key,
                    followOverrides = _ui.value.followOverrides + (key to !currentlyFollowing),
                )
                is ApiResult.Error -> _ui.value = _ui.value.copy(
                    busyUsers = _ui.value.busyUsers - key,
                    error = result.message,
                )
            }
        }
    }

    companion object {
        /**
         * Who the follow endpoint should follow for a listed [user]: a plain username for this
         * instance's users, `user@host` for anyone else. A follower list mixes both, and the
         * plain username of a remote follower used to be sent as if they were local — "User
         * not found", or worse, a local namesake. Same form as `ProfileViewModel.toggleFollow`.
         */
        internal fun followTarget(user: UserDto, serverUrl: String): String? =
            ActorHandle.normalizeToUsername(user.fullHandle, serverUrl)?.removePrefix("@")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FollowListViewModel(container.userRepository, container.sessionStore) }
        }
    }
}

/**
 * Followers / Following list. Every listed athlete can be followed/unfollowed right
 * from the list; tapping the row opens their profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    container: AppContainer,
    username: String,
    type: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val vm: FollowListViewModel = viewModel(key = "$username/$type", factory = FollowListViewModel.factory(container))
    val scope = rememberCoroutineScope()

    LaunchedEffect(username, type) { vm.load(username, type) }
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val who = username.ifBlank { "me" }
                    Text(if (type == "following") "Following · @$who" else "Followers · @$who")
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> LoadingIndicator()
                ui.error != null -> ErrorState(message = ui.error, onRetry = { vm.load(username, type) })
                ui.users.isEmpty() -> EmptyState(title = "Nobody here yet")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.users.size) { index ->
                        val u = ui.users[index]
                        // Keep the full @username@host handle so remote actors remain reachable.
                        val fullHandle = u.fullHandle
                        val busy = ui.busyUsers.contains(fullHandle)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = fullHandle.isNotBlank()) { onOpenProfile(fullHandle) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(avatarUrl = u.avatarUrl, displayName = u.displayName, serverUrl = ui.serverUrl, size = 40)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        u.displayName ?: fullHandle,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(fullHandle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (!busy && !u.username.isNullOrBlank()) {
                                            scope.launch { vm.toggle(u) }
                                        }
                                    },
                                    enabled = !busy,
                                ) {
                                    Text(if (ui.followOverrides[fullHandle] ?: u.isFollowing == true) "Unfollow" else "Follow")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

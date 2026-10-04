package com.fpclient.android.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.NotificationRepository
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A follow request this session accepted or rejected, keyed by actor username in
 * [NotificationsViewModel.UiState.followRequestOutcomes]. The server keeps the
 * FOLLOW_REQUEST notification after the decision (it only flips `followRequestPending`),
 * so this local record is what turns the row's buttons into a resolved label.
 */
enum class FollowRequestOutcome { ACCEPTED, REJECTED }

class NotificationsViewModel(
    private val repository: NotificationRepository,
    private val users: com.fpclient.android.data.repository.UserRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val items: List<NotificationDto> = emptyList(),
        /** Username → decision this session completed; survives the post-action refresh. */
        val followRequestOutcomes: Map<String, FollowRequestOutcome> = emptyMap(),
        /** Username whose accept/reject POST is in flight (single-flight guard). */
        val followRequestActingOn: String? = null,
        /** Username the last action failure belongs to; shown inline on that row. */
        val followRequestErrorUsername: String? = null,
        val followRequestError: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount: StateFlow<Long> = _unreadCount.asStateFlow()

    fun pollUnreadCount() {
        refresh()
    }

    /** Called when the user switches to the notifications tab so the badge resets promptly. */
    fun markPollingDirty() {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = _ui.value.items.isEmpty(), error = null)
            when (val result = repository.list(page = 0)) {
                is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(loading = false, items = result.data)
                    updateUnread(result.data.count { !it.read })
                }
                is ApiResult.Error -> _ui.value =
                    _ui.value.copy(loading = false, error = result.message)
            }
        }
    }

    private suspend fun updateUnread(fallback: Int) {
        when (val r = repository.unreadCount()) {
            is ApiResult.Success -> _unreadCount.value = r.data
            else -> _unreadCount.value = fallback.toLong()
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch { repository.markRead(id); refreshSoft() }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead(); refreshSoft() }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id); refreshSoft() }
    }

    fun acceptFollowRequest(username: String) = respondToFollowRequest(username, accept = true)

    fun rejectFollowRequest(username: String) = respondToFollowRequest(username, accept = false)

    /**
     * Posts the decision and records the outcome only when the server confirms it: the
     * FOLLOW_REQUEST notification itself is never deleted, so the row's resolved label is
     * what dismisses the request. Failures stay on the row instead of disappearing with
     * the old fire-and-forget call that ignored the [ApiResult].
     */
    private fun respondToFollowRequest(username: String, accept: Boolean) {
        if (_ui.value.followRequestActingOn != null) return
        _ui.value = _ui.value.copy(
            followRequestActingOn = username,
            followRequestError = null,
            followRequestErrorUsername = null,
        )
        viewModelScope.launch {
            val result =
                if (accept) users.acceptFollowRequest(username) else users.rejectFollowRequest(username)
            when (result) {
                is ApiResult.Success -> {
                    val outcome =
                        if (accept) FollowRequestOutcome.ACCEPTED else FollowRequestOutcome.REJECTED
                    _ui.value = _ui.value.copy(
                        followRequestActingOn = null,
                        followRequestOutcomes = _ui.value.followRequestOutcomes +
                            (username to outcome),
                    )
                    refreshSoft()
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(
                    followRequestActingOn = null,
                    followRequestErrorUsername = username,
                    followRequestError = result.message
                        ?: "Could not ${if (accept) "accept" else "reject"} the follow request",
                )
            }
        }
    }

    private fun refreshSoft() {
        viewModelScope.launch {
            val result = repository.list(page = 0)
            if (result is ApiResult.Success) {
                _ui.value = _ui.value.copy(items = result.data, loading = false)
            }
            delay(50)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotificationsViewModel(container.notificationRepository, container.userRepository) }
        }
    }
}

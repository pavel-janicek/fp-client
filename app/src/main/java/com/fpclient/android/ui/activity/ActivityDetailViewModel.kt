package com.fpclient.android.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.dto.BoostDto
import com.fpclient.android.data.dto.CommentDto
import com.fpclient.android.data.dto.LikeDto
import com.fpclient.android.data.dto.ReactionPalette
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.util.TrackParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActivityDetailViewModel(
    private val activities: com.fpclient.android.data.repository.ActivityRepository,
    private val users: com.fpclient.android.data.repository.UserRepository,
    private val appViewModel: com.fpclient.android.ui.AppViewModel,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        /** HTTP status code of the last error, if any. Used to detect 404 (federated activity not loadable). */
        val errorStatusCode: Int? = null,
        val activity: com.fpclient.android.data.dto.ActivityDto? = null,
        val likes: List<LikeDto> = emptyList(),
        val comments: List<CommentDto> = emptyList(),
        /** Who boosted the activity, newest first. */
        val boosts: List<BoostDto> = emptyList(),
        val boostBusy: Boolean = false,
        /** Follow relationship with the activity's owner; null for own activities or while loading. */
        val followStatus: com.fpclient.android.data.dto.FollowStatusDto? = null,
        val followBusy: Boolean = false,
        /** True when the activity belongs to the signed-in user (follow UI hidden). */
        val isOwnActivity: Boolean = false,
        /** Base URL of the current instance, used to resolve the author's avatar. */
        val serverUrl: String = "",
    )

    private val _ui = MutableStateFlow(UiState(serverUrl = appViewModel.uiState.value.serverUrl))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load(activityId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            when (val r = activities.detail(activityId)) {
            is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(loading = false, activity = r.data)
                    loadComments(activityId)
                    loadLikes(activityId)
                    loadBoosts(activityId)
                    loadFollowStatus()
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(loading = false, error = r.message, errorStatusCode = r.statusCode)
            }
        }
    }

    private suspend fun loadFollowStatus() {
        val owner = _ui.value.activity?.resolvedUsername
        val own = owner.isNullOrBlank() || owner == appViewModel.uiState.value.username
        _ui.value = _ui.value.copy(isOwnActivity = own, serverUrl = appViewModel.uiState.value.serverUrl)
        if (own) {
            _ui.value = _ui.value.copy(followStatus = null)
            return
        }
        when (val f = users.followStatus(owner)) {
            is ApiResult.Success -> _ui.value = _ui.value.copy(followStatus = f.data)
            else -> Unit // stay null: the button still shows and defaults to Follow
        }
    }

    fun toggleFollow() {
        val owner = _ui.value.activity?.resolvedUsername ?: return
        val status = _ui.value.followStatus
        viewModelScope.launch {
            _ui.value = _ui.value.copy(followBusy = true)
            // No cached status (fresh visitor): default to follow; pending request cancels via unfollow.
            val result = if (status != null && (status.isFollowing || status.canUnfollow || status.isFollowRequestPending)) {
                users.unfollow(owner)
            } else {
                users.follow(owner)
            }
            when (result) {
            is ApiResult.Success -> {
                    // Re-fetch authoritative state instead of guessing flags client-side.
                    when (val f = users.followStatus(owner)) {
                        is ApiResult.Success -> _ui.value = _ui.value.copy(followStatus = f.data, followBusy = false)
                        else -> _ui.value = _ui.value.copy(followBusy = false)
                    }
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(followBusy = false, error = result.message)
            }
        }
    }

    private suspend fun loadComments(id: String) {
        when (val c = activities.comments(id, page = 0, size = 50)) {
            is ApiResult.Success -> _ui.value = _ui.value.copy(comments = c.data.content)
            else -> Unit
        }
    }

    private suspend fun loadLikes(id: String) {
        when (val l = activities.likes(id)) {
            is ApiResult.Success -> _ui.value = _ui.value.copy(likes = l.data)
            else -> Unit
        }
    }

    private suspend fun loadBoosts(id: String) {
        when (val b = activities.boosts(id)) {
            is ApiResult.Success -> _ui.value = _ui.value.copy(boosts = b.data)
            else -> Unit
        }
    }

    /** Boosts (unboosts) the activity, then re-fetches authoritative detail + boost list. */
    fun toggleBoost(activityId: String) {
        val activity = _ui.value.activity ?: return
        val boosting = activity.boostedByCurrentUser != true
        viewModelScope.launch {
            _ui.value = _ui.value.copy(boostBusy = true)
            val result = if (boosting) activities.boost(activityId) else activities.unboost(activityId)
            when (result) {
                is ApiResult.Success -> load(activityId)
                is ApiResult.Error -> _ui.value = _ui.value.copy(boostBusy = false, error = result.message)
            }
        }
    }

    fun react(activityId: String, emoji: String?) {
        viewModelScope.launch {
            val current = _ui.value.activity
            val mine = current?.currentUserReaction
            if (mine == emoji) {
                activities.unreact(activityId)
            } else {
                activities.react(activityId, emoji)
            }
            load(activityId)
        }
    }

    fun addComment(activityId: String, text: String) {
        viewModelScope.launch {
            activities.addComment(activityId, text)
            loadComments(activityId)
        }
    }

    fun deleteComment(activityId: String, commentId: String) {
        viewModelScope.launch {
            activities.deleteComment(activityId, commentId)
            loadComments(activityId)
        }
    }

    fun updateActivity(activityId: String, request: ActivityUpdateRequest) {
        viewModelScope.launch {
            when (val r = activities.update(activityId, request)) {
                is ApiResult.Success -> load(activityId)
                is ApiResult.Error -> _ui.value = _ui.value.copy(error = r.message)
            }
        }
    }

    /** Deletes the activity; invokes [onDeleted] (e.g. navigate back) only on success. */
    fun deleteActivity(activityId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (val r = activities.delete(activityId)) {
                is ApiResult.Success -> onDeleted()
                is ApiResult.Error -> _ui.value = _ui.value.copy(error = r.message)
            }
        }
    }

        /** Parsed polyline segments for the map, sourced from the activity's embedded simplified track. */
    fun trackSegments(): List<List<org.osmdroid.util.GeoPoint>> =
        _ui.value.activity?.let {
            TrackParser.fromGeometry(it.simplifiedTrack?.type, it.simplifiedTrack?.coordinates)
        } ?: emptyList()

    companion object {
        fun factory(container: AppContainer, appViewModel: com.fpclient.android.ui.AppViewModel): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ActivityDetailViewModel(
                    container.activityRepository,
                    container.userRepository,
                    appViewModel,
                )
            }
        }
    }
}

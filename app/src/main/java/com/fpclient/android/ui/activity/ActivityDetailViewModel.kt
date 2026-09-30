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
import com.fpclient.android.data.dto.ActivityTrimDataDto
import com.fpclient.android.data.dto.ActivityTrimPointDto
import com.fpclient.android.data.dto.ActivityTrimSelection
import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.dto.toUpdateRequest
import com.fpclient.android.data.dto.BoostDto
import com.fpclient.android.data.dto.CommentDto
import com.fpclient.android.data.dto.LikeDto
import com.fpclient.android.data.dto.ReactionPalette
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.util.ActorHandle
import com.fpclient.android.util.Format
import com.fpclient.android.util.ShareLinks
import com.fpclient.android.util.TrackParser
import com.fpclient.android.util.TrimPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivityDetailViewModel(
    private val activities: com.fpclient.android.data.repository.ActivityRepository,
    private val users: com.fpclient.android.data.repository.UserRepository,
    private val appViewModel: com.fpclient.android.ui.AppViewModel,
    /** Bumped after a trim so list screens (timeline, profile, records) re-fetch instead of
     * showing the activity's old distance. */
    private val activitiesVersion: MutableStateFlow<Int>,
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

    /**
     * The trim workspace (`GET /api/web/activities/{id}/trim` + the `trim` field of the normal
     * activity update). Only loaded when the trim screen is opened.
     */
    data class TrimUiState(
        val loading: Boolean = false,
        /** Server message when the source cannot be trimmed at all (manual activity, no original file, unsupported format, < 3 points). */
        val error: String? = null,
        val data: ActivityTrimDataDto? = null,
        /** Slider positions — indices into [ActivityTrimDataDto.points] (the original track). */
        val startIndex: Int = 0,
        val endIndex: Int = 0,
        /** The selection [preview] was computed for; lags the sliders by the preview debounce. */
        val previewStartIndex: Int = 0,
        val previewEndIndex: Int = 0,
        val preview: TrimPreview.Result? = null,
        val saving: Boolean = false,
        /** Totals the instance reported for the last applied trim — authoritative, unlike [preview]. */
        val applied: AppliedTrim? = null,
    ) {
        val points: List<ActivityTrimPointDto> get() = data?.points ?: emptyList()

        /** Whether the selection still differs from the range the activity currently stores. */
        val selectedRangeIsStored: Boolean
            get() = data != null && startIndex == data.currentStartIndex && endIndex == data.currentEndIndex

        /** The whole original track is selected (a way back from any previous trim). */
        val selectedRangeIsOriginal: Boolean
            get() = points.isNotEmpty() && startIndex == 0 && endIndex == points.lastIndex

        /** The server rejects a range that keeps fewer than two points. */
        val canApply: Boolean get() = data != null && startIndex < endIndex && !saving && !selectedRangeIsStored
    }

    /** What the instance stored for the last applied trim, so the screen never has to guess it. */
    data class AppliedTrim(
        val startIndex: Int,
        val endIndex: Int,
        val totalDistance: Double?,
        val totalDurationSeconds: Long?,
        val elevationGain: Double?,
    )

    private data class TrimSelection(val startIndex: Int, val endIndex: Int)

    private val _trim = MutableStateFlow(TrimUiState())
    val trim: StateFlow<TrimUiState> = _trim.asStateFlow()

    /** Emits on every slider move; [init] debounces it so a drag recomputes the preview a few times, not sixty. */
    private val trimSelection = MutableStateFlow(TrimSelection(0, 0))

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            trimSelection.debounce(PREVIEW_DEBOUNCE_MS).collect { selection -> computePreview(selection) }
        }
    }

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
            // The server classifies any `user@host` path segment as a federated handle
            // and routes it into WebFinger discovery ("user not found"), so collapse
            // same-instance handles to the plain local username first.
            val target = ActorHandle.normalizeToUsername(owner, appViewModel.uiState.value.serverUrl) ?: run {
                _ui.value = _ui.value.copy(followBusy = false)
                return@launch
            }
            // An accepted follow AND a pending request are both removed via the unfollow
            // endpoint; no cached status (fresh visitor) defaults to follow.
            val shouldUnfollow = status != null && (status.isAccepted || status.isPending)
            val result = if (shouldUnfollow) {
                users.unfollow(target)
            } else {
                users.follow(target)
            }
            when (result) {
            is ApiResult.Success -> {
                    // Re-fetch authoritative state instead of guessing flags client-side.
                    when (val f = users.followStatus(target)) {
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

    // ---------------------------------------------------------------------------------------
    // Trim workspace
    // ---------------------------------------------------------------------------------------

    /** Fetches the original track + stored range. Called when the trim screen is opened. */
    fun loadTrimData(activityId: String) {
        viewModelScope.launch {
            _trim.value = TrimUiState(loading = true)
            when (val r = activities.trimData(activityId)) {
                is ApiResult.Success -> {
                    _trim.value = trimStateFor(r.data)
                    val state = _trim.value
                    trimSelection.value = TrimSelection(state.startIndex, state.endIndex)
                    // Compute immediately as well: an unchanged selection would not re-emit on the
                    // debounced flow, and this is the state the dialog opens with.
                    computePreview(TrimSelection(state.startIndex, state.endIndex))
                }
                is ApiResult.Error -> _trim.value = TrimUiState(error = r.message)
            }
        }
    }

    /** Moves the first retained point. Keeps at least one point before the end. */
    fun setTrimStart(index: Int) {
        val state = _trim.value
        if (state.points.size < 2) return
        updateTrimSelection(index.coerceIn(0, state.endIndex - 1), state.endIndex)
    }

    /** Moves the last retained point. Keeps at least one point after the start. */
    fun setTrimEnd(index: Int) {
        val state = _trim.value
        if (state.points.size < 2) return
        updateTrimSelection(state.startIndex, index.coerceIn(state.startIndex + 1, state.points.lastIndex))
    }

    /** Selects the whole original track — how a previous trim is undone. */
    fun trimToOriginal() {
        val state = _trim.value
        if (state.points.size < 2) return
        updateTrimSelection(0, state.points.lastIndex)
    }

    /**
     * Applies the selection. A trim is not its own endpoint: the range rides along with the
     * activity update, which is also why the request re-sends the metadata that update replaces
     * ([toUpdateRequest]). The server then recalculates distance, duration, elevation, speed
     * metrics, timezone and start location, and the response's totals are what the screen shows.
     */
    fun applyTrim(activityId: String) {
        val state = _trim.value
        val activity = _ui.value.activity ?: return
        if (!state.canApply) return
        val selection = ActivityTrimSelection(state.startIndex, state.endIndex)
        viewModelScope.launch {
            _trim.value = _trim.value.copy(saving = true, error = null)
            when (val r = activities.update(activityId, activity.toUpdateRequest(selection))) {
                is ApiResult.Success -> {
                    val updated = r.data
                    _trim.value = _trim.value.copy(
                        saving = false,
                        applied = AppliedTrim(
                            startIndex = selection.startIndex,
                            endIndex = selection.endIndex,
                            totalDistance = updated.totalDistance,
                            totalDurationSeconds = updated.totalDurationSeconds,
                            elevationGain = updated.elevationGain,
                        ),
                    )
                    activitiesVersion.value += 1
                    load(activityId)
                    reloadTrimData(activityId)
                }
                is ApiResult.Error -> _trim.value = _trim.value.copy(saving = false, error = r.message)
            }
        }
    }

    private fun updateTrimSelection(start: Int, end: Int) {
        if (_trim.value.startIndex == start && _trim.value.endIndex == end) return
        _trim.value = _trim.value.copy(startIndex = start, endIndex = end)
        trimSelection.value = TrimSelection(start, end)
    }

    /** The workspace state for freshly loaded trim data, with the stored range selected. */
    private fun trimStateFor(data: ActivityTrimDataDto): TrimUiState {
        val last = maxOf(0, data.points.lastIndex)
        val start = data.currentStartIndex.coerceIn(0, last)
        val end = data.currentEndIndex.coerceIn(0, last)
        return TrimUiState(
            data = data,
            startIndex = start,
            endIndex = end,
            previewStartIndex = start,
            previewEndIndex = end,
        )
    }

    /** Re-reads the workspace after a successful trim, keeping the "applied" report visible. */
    private suspend fun reloadTrimData(activityId: String) {
        val reported = _trim.value.applied
        when (val r = activities.trimData(activityId)) {
            is ApiResult.Success -> {
                val state = trimStateFor(r.data)
                _trim.value = state.copy(applied = reported)
                trimSelection.value = TrimSelection(state.startIndex, state.endIndex)
                computePreview(TrimSelection(state.startIndex, state.endIndex))
            }
            is ApiResult.Error -> _trim.value = _trim.value.copy(applied = reported, error = r.message)
        }
    }

    /**
     * Recomputes the preview for a settled selection off the main thread (the elevation pass is
     * linear in the point count, and a long recording has tens of thousands of them).
     */
    private suspend fun computePreview(selection: TrimSelection) {
        val data = _trim.value.data ?: return
        val preview = withContext(Dispatchers.Default) {
            TrimPreview.preview(data, selection.startIndex, selection.endIndex)
        }
        // The workspace may have been reloaded (or closed) while this ran.
        if (_trim.value.data !== data) return
        _trim.value = _trim.value.copy(
            previewStartIndex = selection.startIndex,
            previewEndIndex = selection.endIndex,
            preview = preview,
        )
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

    /** Web URL of the activity on the instance (`{server}/activities/{id}`), for sharing. */
    fun publicActivityUrl(): String? {
        val activity = _ui.value.activity ?: return null
        val id = activity.id ?: return null
        return ShareLinks.publicActivityUrl(_ui.value.serverUrl, id)
    }

    /**
     * Share text for the activity: "I just finished: {name} check it out at: {public link}".
     * Falls back to the capitalized activity type when no title is set.
     */
    fun shareText(): String? {
        val activity = _ui.value.activity ?: return null
        val id = activity.id ?: return null
        val name = activity.title?.takeIf { it.isNotBlank() }
            ?: Format.uppercaseFirst(activity.activityType)
        return ShareLinks.activityShareText(name, _ui.value.serverUrl, id)
    }

    /**
     * Fetches the activity route file (default GPX, same as the web version's
     * download) and hands the bytes to [onResult] on the main thread.
     */
    fun downloadRoute(format: String = "gpx", onResult: suspend (ApiResult<ByteArray>) -> Unit) {
        val id = _ui.value.activity?.id ?: return
        viewModelScope.launch {
            onResult(activities.downloadRoute(id, format))
        }
    }

    companion object {
        /** How long a slider must sit still before the (off-main) preview is recomputed. */
        private const val PREVIEW_DEBOUNCE_MS = 150L

        fun factory(container: AppContainer, appViewModel: com.fpclient.android.ui.AppViewModel): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ActivityDetailViewModel(
                    container.activityRepository,
                    container.userRepository,
                    appViewModel,
                    container.activitiesVersion,
                )
            }
        }
    }
}

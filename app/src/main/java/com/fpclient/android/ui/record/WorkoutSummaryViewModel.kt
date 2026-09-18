package com.fpclient.android.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.recording.PendingUpload
import com.fpclient.android.recording.RecordingShareManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the post-workout summary (Iteration 8d): shares the recorded session through
 * [RecordingShareManager] and exposes the pending-upload registry so the Record screen can
 * offer a retry later. Both the summary and the pending list live on the same route, so one
 * ViewModel serves them.
 */
class WorkoutSummaryViewModel(private val share: RecordingShareManager) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val error: String? = null,
        /** Non-null once the server imported the workout — offer ActivityDetail. */
        val uploadedActivityId: String? = null,
        /** Outcome of the last "retry all" pass: how many workouts reached the server. */
        val retried: Int? = null,
        val pending: List<PendingUpload> = emptyList(),
    ) {
        /** One-line status for the Record screen: the failure, or the retry's success. */
        val message: String? get() = error
            ?: retried?.takeIf { it > 0 }?.let { "$it workout(s) uploaded." }
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // Single source of truth for both the pending list and the "uploaded but not
            // yet shared" hints, so a retry and a manual share never disagree.
            share.pendingUploads.collect { list ->
                _ui.value = _ui.value.copy(pending = list)
            }
        }
    }

    /**
     * Uploads the recorded session with the metadata the user entered. On success the
     * files are gone (the manager cleans up) and [UiState.uploadedActivityId] is set; on
     * failure the entry stays pending with the same metadata for a later retry.
     */
    fun share(
        sessionId: Long,
        activityType: String,
        title: String?,
        description: String?,
        visibility: String,
    ) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, retried = null)
            when (val result = share.upload(sessionId, activityType, title, description, visibility)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    busy = false,
                    uploadedActivityId = result.data.id,
                )

                is ApiResult.Error -> _ui.value = _ui.value.copy(
                    busy = false,
                    error = result.message ?: "Upload failed",
                )
            }
        }
    }

    /** Retries every pending upload in order (the Record screen's "retry later" action). */
    fun retryPending() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, retried = null)
            val uploaded = share.retryPending()
            // A stopping-at-the-first-failure pass reports that entry's message, so the
            // user sees *why* the retry did not go through.
            val failure = if (uploaded == 0) share.pendingUploads.value.firstOrNull()?.lastError else null
            _ui.value = _ui.value.copy(busy = false, retried = uploaded, error = failure)
        }
    }

    /** Clears the status line (e.g. when the summary is re-opened for another session). */
    fun clearMessage() {
        _ui.value = _ui.value.copy(error = null, retried = null)
    }

    /** Resets the success state so the summary can be reused for another session. */
    fun resetUploadState() {
        _ui.value = _ui.value.copy(uploadedActivityId = null, error = null, retried = null)
    }

    /** Drops a recorded session without sharing it (with its local files). */
    fun discard(sessionId: Long) {
        share.discard(sessionId)
        _ui.value = _ui.value.copy(uploadedActivityId = null, error = null, retried = null)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkoutSummaryViewModel(container.recordingShareManager) }
        }
    }
}

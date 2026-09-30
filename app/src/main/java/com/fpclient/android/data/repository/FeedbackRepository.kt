package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.FeedbackSubmissionRequest
import com.fpclient.android.data.dto.FeedbackSubmissionResponse
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi

/**
 * Sends feedback to the instance administrators.
 *
 * Sign-in only: the server's security config requires an authenticated session for
 * `/api/web/feedback`, so there is nothing to do for a guest. Submission is fire-and-forget
 * — the endpoint returns only the new id, and the only way back to it is the instance's own
 * admin page — so nothing is stored locally.
 */
class FeedbackRepository(private val api: FitPubApi) {

    suspend fun submit(request: FeedbackSubmissionRequest): ApiResult<FeedbackSubmissionResponse> {
        return try {
            val response = api.submitFeedback(request)
            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: FeedbackSubmissionResponse())
            } else {
                // A 400 carries the server's own wording ("Choose a topic and enter a message
                // of 1 to 5,000 characters."), which beats anything invented here.
                ApiResult.Error(ErrorMessages.extract(response.errorBody()?.string()), response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }
}

package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.KomootActivitiesResponse
import com.fpclient.android.data.dto.KomootActivityImportRequest
import com.fpclient.android.data.dto.KomootActivitySummaryDto
import com.fpclient.android.data.dto.KomootImportExecutionResponse
import com.fpclient.android.data.dto.KomootImportRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi
import retrofit2.Response

/**
 * Imports activities from a Komoot account through the user's own FitPub server.
 *
 * The server proxies Komoot's API with the Komoot credentials supplied per request and
 * stores none of them, so nothing here is persisted either — the caller keeps the password
 * in memory for the length of the flow.
 *
 * Komoot support is **opt-in on the server** (`fitpub.komoot.enabled`, default false), where
 * both endpoints answer 404 with `{"error":"Komoot support is disabled."}`. That is modelled
 * as [KomootDisabled] rather than a generic error, because the screen's whole job then
 * becomes "this instance does not offer this" rather than "something went wrong".
 */
class KomootImportRepository(private val api: FitPubApi) {

    /**
     * Lists the user's completed Komoot activities, newest first as the server returns them.
     */
    suspend fun activities(request: KomootImportRequest): ApiResult<KomootActivitiesResponse> =
        execute { api.komootActivities(request) }

    /**
     * Imports one activity. The server paces these deliberately
     * (`fitpub.komoot.activity-import-delay-ms`, default 3000) to stay inside Komoot's rate
     * limits, so callers must import sequentially rather than in parallel.
     */
    suspend fun importActivity(
        request: KomootActivityImportRequest,
    ): ApiResult<KomootImportExecutionResponse> = execute { api.komootImportActivity(request) }

    private suspend fun <T> execute(call: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = call()
            when {
                response.isSuccessful -> ApiResult.Success(response.body() ?: return emptyBody())
                // 404 is how the server says "Komoot support is disabled" — before that it can
                // also mean a bad activity id, but the feature-gate answer is the common case
                // and the one the UI must explain.
                response.code() == 404 -> KomootDisabled
                else -> ApiResult.Error(
                    ErrorMessages.extract(response.errorBody()?.string()),
                    response.code(),
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }

    private fun emptyBody(): ApiResult<Nothing> =
        ApiResult.Error("The server sent an empty response.")

    /** The activities a user may still import, i.e. excluding the server's own duplicates. */
    fun importable(activities: List<KomootActivitySummaryDto>): List<KomootActivitySummaryDto> =
        activities.filterNot { it.imported }

    companion object {
        /**
         * The instance has `fitpub.komoot.enabled` off — not an error the user can act on.
         *
         * A shared value rather than an `object`, because [ApiResult.Error] is a final data
         * class and cannot be subclassed. Callers compare it with `===`, which is reliable
         * because the repository returns this very instance for every disabled answer.
         */
        val KomootDisabled: ApiResult.Error =
            ApiResult.Error("Komoot import is not enabled on this instance.", statusCode = 404)
    }
}

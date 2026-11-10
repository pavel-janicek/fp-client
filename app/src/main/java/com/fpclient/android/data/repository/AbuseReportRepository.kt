package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.AbuseReportRequest
import com.fpclient.android.data.dto.ReportTargetKind
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi

/**
 * Abuse reports (FitPub 1.4, #566). A report reaches only this instance's administrators,
 * never the reported person or their server.
 */
class AbuseReportRepository(private val api: FitPubApi) {

    /**
     * Whether the signed-in user may report this target. Any failure, including the 404 of a
     * server older than abuse reports, counts as "no", so the Report action stays hidden.
     */
    suspend fun canReport(kind: ReportTargetKind, targetId: String): Boolean =
        try {
            val response = api.abuseReportEligibility(kind, targetId)
            response.isSuccessful && response.body()?.eligible == true
        } catch (e: Exception) {
            false
        }

    suspend fun report(request: AbuseReportRequest): ApiResult<Unit> {
        return try {
            val response = api.submitAbuseReport(request)
            if (response.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Error(
                if (response.code() == 404) "This content is no longer available."
                else ErrorMessages.extract(response.errorBody()?.string(), "The report could not be sent. Please try again."),
                response.code(),
            )
        } catch (e: Exception) {
            ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
    }
}

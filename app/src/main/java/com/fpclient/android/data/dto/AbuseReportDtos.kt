package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Abuse reports (FitPub 1.4, #566): a report goes to the reporter's own instance
// administrators only, never to the reported person or their server.
// ---------------------------------------------------------------------------

/** What a report points at; the server's `ReportTargetKind`. */
enum class ReportTargetKind { LOCAL_PROFILE, LOCAL_ACTIVITY, REMOTE_ACTIVITY }

/**
 * `POST /api/web/abuse-reports`. [targetId] is the local activity's id or, for a federated
 * activity, the home instance's mirror id. [reason] is 1 to [MAX_REASON] characters.
 */
@Serializable
data class AbuseReportRequest(
    val kind: ReportTargetKind,
    val targetId: String,
    val reason: String,
) {
    companion object {
        /** The server's `AbuseReportService.MAX_REASON_LENGTH`. */
        const val MAX_REASON = 5000
    }
}

@Serializable
data class AbuseReportCreatedDto(val id: String? = null)

/** `GET /api/web/abuse-reports/eligibility`: false for own content and content the viewer can't see. */
@Serializable
data class AbuseReportEligibilityDto(val eligible: Boolean = false)

package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Feedback
// ---------------------------------------------------------------------------

/**
 * Body of `POST /api/web/feedback`.
 *
 * [topic] is required — the server rejects a null one — so the app always sends a concrete
 * [FeedbackTopics] value. [message] must be 1..[TextLimits.FEEDBACK_MESSAGE] characters.
 *
 * [replyAllowed] is a privacy decision, not a convenience: the server only exposes the
 * account's email address to instance admins when this is true (see `FeedbackDTO`, which
 * nulls the email otherwise). The UI states that before the user chooses.
 */
@Serializable
data class FeedbackSubmissionRequest(
    val topic: String,
    val message: String,
    val replyAllowed: Boolean = false,
)

/** `POST /api/web/feedback` answers **201** with just the new id. */
@Serializable
data class FeedbackSubmissionResponse(
    val id: String? = null,
)

/**
 * The server's `FeedbackTopic` enum, with the labels the web UI uses so the two clients
 * read identically.
 */
object FeedbackTopics {
    const val BUG_REPORT = "BUG_REPORT"
    const val FEATURE_REQUEST = "FEATURE_REQUEST"
    const val SUPPORT_REQUEST = "SUPPORT_REQUEST"
    const val OTHER = "OTHER"

    /** Picker order, matching `feedback.html`. */
    val ALL = listOf(BUG_REPORT, FEATURE_REQUEST, SUPPORT_REQUEST, OTHER)

    fun label(topic: String?): String = when (topic) {
        BUG_REPORT -> "Bug report"
        FEATURE_REQUEST -> "Feature request"
        SUPPORT_REQUEST -> "Support request"
        OTHER -> "Other"
        // A future server topic degrades to readable text rather than shouting the enum.
        else -> topic?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Other"
    }
}

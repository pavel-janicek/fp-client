package com.fpclient.android.util

/**
 * Text-length limits that mirror the FitPub server's configurable validation
 * (`fitpub.text-limits` in application.yml). The Android UI caps inputs to these
 * values so the server never rejects client submissions with a 400 BAD_REQUEST.
 *
 * Defaults match the server's `FITPUB_*` environment variables:
 * - activity title 200 (`FITPUB_ACTIVITY_TITLE_MAX_LENGTH`)
 * - activity description 5000 (`FITPUB_ACTIVITY_DESCRIPTION_MAX_LENGTH`)
 * - user bio 500 (`FITPUB_USER_BIO_MAX_LENGTH`)
 * - display name 100 / timezone 100 (fixed @Size in UserUpdateRequest)
 */
object TextLimits {
    const val ACTIVITY_TITLE = 200
    const val ACTIVITY_DESCRIPTION = 5000
    const val USER_BIO = 500
    const val DISPLAY_NAME = 100
    const val TIMEZONE = 100
    const val COMMENT = 5000
    /** Server's `FeedbackService.MAX_MESSAGE_LENGTH` (a constant, not configurable). */
    const val FEEDBACK_MESSAGE = 5000
}
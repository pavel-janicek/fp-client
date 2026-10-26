package com.fpclient.android.garmin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.fpclient.android.MainActivity

/**
 * Receives `fpclient://garmin-connect?result=linked|denied|expired|error`, where the relay's
 * page sends the browser after Garmin's sign-in. It hands the result to [MainActivity] with
 * CLEAR_TOP, which also closes the Custom Tab sitting above it, and finishes at once (no UI).
 */
class GarminConnectReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = intent?.data?.getQueryParameter("result") ?: RESULT_ERROR
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_RESULT, result)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    companion object {
        const val EXTRA_RESULT = "com.fpclient.android.GARMIN_CONNECT_RESULT"
        const val RESULT_LINKED = "linked"
        const val RESULT_DENIED = "denied"
        const val RESULT_EXPIRED = "expired"
        const val RESULT_ERROR = "error"

        /** What the user is told about a returned [result]. */
        fun message(result: String): String = when (result) {
            RESULT_LINKED -> "Garmin Connect™ linked"
            RESULT_DENIED -> "Garmin Connect™ was not linked: access was not allowed"
            RESULT_EXPIRED -> "The Garmin sign-in expired. Try connecting again."
            else -> "Garmin Connect™ could not be linked. Try again later."
        }
    }
}

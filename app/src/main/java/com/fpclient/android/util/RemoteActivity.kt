package com.fpclient.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Helpers for opening federated (remote) activities, mirroring the web client:
 * `timeline.js` never routes remote items to the local detail page (which 404s,
 * because the mirror lives in `remote_activities`), it opens
 * `activity.activityUri` — the activity's page on the author's home instance —
 * in a new tab instead.
 */
object RemoteActivity {

    /**
     * The origin URL to open for a timeline activity, or `null` when the card
     * should use the regular in-app detail route.
     *
     * Remote items without a usable `activityUri` (older server payloads) fall
     * back to the in-app detail — its "not accessible yet" screen is still the
     * best available answer in that case.
     */
    fun originUrl(isLocal: Boolean, activityUri: String?): String? =
        if (!isLocal && !activityUri.isNullOrBlank()) activityUri else null
}

/**
 * Opens [originUrl] in a Chrome Custom Tab (falls back to the default
 * browser) and reports whether the handoff succeeded. When it fails — no
 * browser installed — the caller should fall back to the in-app detail
 * route rather than leaving the tap dead.
 *
 * Top-level on purpose: object-member extensions need a separate import to be
 * usable with receiver syntax, which is easy to miss at call sites.
 */
fun Context.openOnOrigin(originUrl: String): Boolean = try {
    CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
        .intent
        .apply {
            data = Uri.parse(originUrl)
            // Safe when launched from an Activity context too; required if a
            // non-Activity context ever ends up calling this.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        .let { startActivity(it) }
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: IllegalStateException) {
    false
}

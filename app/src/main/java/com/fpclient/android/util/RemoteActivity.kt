package com.fpclient.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Helpers for opening federated (remote) activities. The home instance's detail
 * endpoint 404s for them (the mirror lives in `remote_activities`), so the web
 * client's `timeline.js` opens `activity.activityUri` — the activity's page on the
 * author's home instance — in a new tab. The app instead reads the detail from the
 * origin's public API ([origin]) and keeps the Custom Tab ([openOnOrigin]) as the
 * fallback for what that API cannot serve anonymously.
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

    /**
     * Where a federated activity's full detail lives. The home instance keeps only a
     * metadata mirror, but the origin serves the same public `GET /api/web/activities/{id}`
     * (plus its likes / comments / boosts) without authentication, so the in-app detail
     * can read it from there. [serverUrl] has no trailing slash.
     */
    data class Origin(val serverUrl: String, val activityId: String, val pageUrl: String) {
        val host: String get() = ActorHandle.hostOf(serverUrl).orEmpty()
    }

    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /**
     * Parses a FitPub activity URI (`https://host[:port]/activities/{uuid}`) into its
     * origin. Anything else — another fediverse server's object URL, plain http, extra
     * path segments — returns `null`, and the caller keeps the Custom Tab handoff.
     */
    fun origin(activityUri: String?): Origin? {
        val url = activityUri?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val segments = url.pathSegments
        if (segments.size != 2 || segments[0] != "activities" || !UUID.matches(segments[1])) return null
        val port = if (url.port == 443) "" else ":${url.port}"
        return Origin(
            serverUrl = "https://${url.host}$port",
            activityId = segments[1],
            pageUrl = activityUri.trim(),
        )
    }

    /** Whether [actorUri] is the signed-in user (`{serverUrl}/users/{username}`). */
    fun isActor(actorUri: String?, serverUrl: String?, username: String?): Boolean {
        if (actorUri.isNullOrBlank() || username.isNullOrBlank()) return false
        val host = ActorHandle.hostOf(actorUri) ?: return false
        return host.equals(ActorHandle.hostOf(serverUrl), ignoreCase = true) &&
            actorUri.trimEnd('/').substringAfterLast('/') == username
    }

    /**
     * Reaction counts after the viewer's reaction moved from [old] to [new] (either may be
     * null). A reaction made through the home instance reaches the origin by federation, so
     * the counts read from the origin lag behind; this keeps the screen consistent with what
     * the user just did. Counts never drop below zero and empty entries are removed.
     */
    fun withReaction(counts: Map<String, Long>?, old: String?, new: String?): Map<String, Long> {
        val result = counts.orEmpty().toMutableMap()
        if (old == new) return result
        if (old != null) {
            val left = (result[old] ?: 0) - 1
            if (left > 0) result[old] = left else result.remove(old)
        }
        if (new != null) result[new] = (result[new] ?: 0) + 1
        return result
    }
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

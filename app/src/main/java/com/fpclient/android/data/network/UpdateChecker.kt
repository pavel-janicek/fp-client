package com.fpclient.android.data.network

import com.fpclient.android.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Result of a manual update check against the project's GitHub releases. */
data class UpdateCheckResult(
    /** Raw `tag_name` of the newest published release, e.g. "Release_1.3.6". */
    val latestVersion: String,
    /** Link to the release page (shown when the user wants to read release notes). */
    val releaseUrl: String? = null,
    /** ISO-8601 publish timestamp of the release. */
    val publishedAt: String? = null,
) {
    /** True when the released version is newer than the running app. */
    val updateAvailable: Boolean get() = UpdateVersions.isNewer(latestVersion, BuildConfig.VERSION_NAME)
}

/**
 * Parsing/ordering helpers for release-tag version strings. Tags carry a release
 * prefix ("Release_1.3.6") and can have arbitrary numeric arity, so a plain
 * string comparison would never match and "1.3.10" must rank above "1.3.6".
 */
object UpdateVersions {

    /** True when [candidate] denotes a newer version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun compare(a: String, b: String): Int {
        val aa = components(a)
        val bb = components(b)
        var i = 0
        while (i < aa.size || i < bb.size) {
            val x = if (i < aa.size) aa[i] else 0
            val y = if (i < bb.size) bb[i] else 0
            if (x != y) return if (x > y) 1 else -1
            i += 1
        }
        return 0
    }

    /** Extracts numeric components from a version-ish string: "Release_1.3.6" -> [1, 3, 6],
     *  "1.3.6-beta2" -> [1, 3, 6, 2]. Falls back to [0] for strings without any digits. */
    private fun components(raw: String): List<Int> {
        val parts = ArrayList<Int>()
        var value = 0
        var inNumber = false
        for (ch in raw) {
            if (ch.isDigit()) {
                value = value * 10 + (ch - '0')
                inNumber = true
            } else if (inNumber) {
                parts.add(value)
                value = 0
                inNumber = false
            }
        }
        if (inNumber) parts.add(value)
        if (parts.isEmpty()) parts.add(0)
        return parts
    }
}

/**
 * Checks the project's GitHub "latest release" for a newer app version.
 *
 * The check is always user-initiated and downloads nothing but a small JSON
 * document (no binaries, no personal data), so it stays compatible with the
 * F-Droid inclusion policy (no background checks, no auto-updates) and with the
 * Google Play distribution agreement (installs and updates stay in the store).
 */
object UpdateChecker {

    /** GitHub "latest release" endpoint; `releases/latest` never returns drafts or prereleases. */
    const val DEFAULT_RELEASES_URL = "https://api.github.com/repos/pavel-janicek/fp-client/releases/latest"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the newest published release and compares it with [BuildConfig.VERSION_NAME].
     * Offloads the HTTP call to OkHttp's worker threads and suspends until it completes,
     * so the compose event loop is never blocked. [releasesUrl] is overridable so tests
     * can point at a local MockWebServer.
     */
    suspend fun check(releasesUrl: String = DEFAULT_RELEASES_URL): ApiResult<UpdateCheckResult> {
        val request = Request.Builder()
            .url(releasesUrl.toHttpUrl())
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FP-Client/${BuildConfig.VERSION_NAME}")
            .get()
            .build()
        val deferred = CompletableDeferred<ApiResult<UpdateCheckResult>>()
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    deferred.complete(ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e))
                }

                override fun onResponse(call: Call, response: Response) {
                    deferred.complete(
                        runCatching {
                            if (!response.isSuccessful) {
                                ApiResult.Error("Couldn't check for updates (HTTP ${response.code}).", response.code)
                            } else {
                                parseRelease(response.body!!.string())
                            }
                        }.getOrNull() ?: ApiResult.Error("Couldn't check for updates."),
                    )
                }
            },
        )
        return deferred.await()
    }

    private fun parseRelease(body: String): ApiResult<UpdateCheckResult> {
        val obj = json.parseToJsonElement(body).jsonObject
        val tag = (obj["tag_name"] as? JsonPrimitive)?.contentOrNull
        if (tag == null || tag.isBlank()) {
            return ApiResult.Error("Couldn't read the latest version from GitHub.")
        }
        return ApiResult.Success(
            UpdateCheckResult(
                latestVersion = tag.trim(),
                releaseUrl = (obj["html_url"] as? JsonPrimitive)?.contentOrNull,
                publishedAt = (obj["published_at"] as? JsonPrimitive)?.contentOrNull,
            ),
        )
    }
}
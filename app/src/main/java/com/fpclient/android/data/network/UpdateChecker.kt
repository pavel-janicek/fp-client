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
 * Pre-release suffixes order semver-style — alpha < beta < rc1..rcN < an explicit
 * "final" label < the unsuffixed release — so an install running "2.0.0-alpha"
 * is prompted to update as soon as the final "2.0.0" is published (GitHub's
 * `releases/latest` never returns prerelease tags, so the suffix must not be
 * folded into extra digits). The unsuffixed form always ranks highest, so
 * "2.0.0" is newer than "2.0.0-final".
 */
object UpdateVersions {

    /** True when [candidate] denotes a newer version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /**
     * Release stage; higher is newer: alpha (0) < beta (1) < rc (2) < an explicit
     * "final"/"release" label (3) < **no suffix at all** (4).
     *
     * The unsuffixed stage is deliberately the maximum, so the canonical release
     * "2.0.0" outranks "2.0.0-final" (and every other suffix) and an install
     * running any kind of pre-release build is always offered the final update.
     */
    private const val STAGE_ALPHA = 0
    private const val STAGE_BETA = 1
    private const val STAGE_RC = 2
    private const val STAGE_FINAL_LABEL = 3
    private const val STAGE_RELEASE = 4

    private class ParsedVersion(val core: List<Int>, val stage: Int, val stageNumber: Int)

    private fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        compareComponents(pa.core, pb.core).let { if (it != 0) return it }
        if (pa.stage != pb.stage) return pa.stage.compareTo(pb.stage)
        return pa.stageNumber.compareTo(pb.stageNumber)
    }

    private fun compareComponents(a: List<Int>, b: List<Int>): Int {
        var i = 0
        while (i < a.size || i < b.size) {
            val x = if (i < a.size) a[i] else 0
            val y = if (i < b.size) b[i] else 0
            if (x != y) return if (x > y) 1 else -1
            i += 1
        }
        return 0
    }

    private fun parse(raw: String): ParsedVersion {
        var i = 0
        val n = raw.length
        // Skip a leading tag prefix such as "Release_".
        while (i < n && !raw[i].isDigit()) i += 1
        // Numeric core: digits and dots up to the first other character (e.g. "-beta").
        val core = ArrayList<Int>()
        var value = 0
        var inNumber = false
        while (i < n && (raw[i].isDigit() || raw[i] == '.')) {
            val ch = raw[i]
            if (ch.isDigit()) {
                value = value * 10 + (ch - '0')
                inNumber = true
            } else if (inNumber) {
                core.add(value)
                value = 0
                inNumber = false
            }
            i += 1
        }
        if (inNumber) core.add(value)
        if (core.isEmpty()) core.add(0)
        val (stage, stageNumber) = parseStage(raw.substring(i))
        return ParsedVersion(core, stage, stageNumber)
    }

    /** Maps a suffix like "-rc2" / "beta.1" / "-alpha" to its stage and iteration number. */
    private fun parseStage(suffix: String): Pair<Int, Int> {
        val stripped = suffix.dropWhile { !it.isLetter() }
        if (stripped.isBlank()) return STAGE_RELEASE to 0
        val letters = stripped.takeWhile { it.isLetter() }
        val digits = stripped.drop(letters.length).filter { it.isDigit() }
        val number = if (digits.isEmpty()) 0 else digits.toIntOrNull() ?: 0
        return when (letters.lowercase()) {
            "rc" -> STAGE_RC to number
            "beta" -> STAGE_BETA to number
            // Labels that merely say "this is the last one": newer than any
            // pre-release stage, but still older than the unsuffixed release.
            "final", "release", "stable", "ga" -> STAGE_FINAL_LABEL to number
            else -> STAGE_ALPHA to number // unknown pre-release stage → most conservative ranking
        }
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
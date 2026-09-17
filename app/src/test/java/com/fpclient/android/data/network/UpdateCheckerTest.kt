package com.fpclient.android.data.network

import com.fpclient.android.BuildConfig
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Verifies the manual update check: GitHub release parsing, version ordering, and error mapping. */
class UpdateCheckerTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun latestUrl(): String = server.url("/").toString().trimEnd('/') + "/releases/latest"

    private fun release(tag: String) = MockResponse().setResponseCode(200)
        .setBody(
            """{"tag_name":"$tag","html_url":"https://github.com/pavel-janicek/fp-client/releases/tag/$tag","published_at":"2026-09-07T19:08:07Z"}""",
        )

    /** The running version as a release tag, e.g. `1.3.7` -> `Release_1.3.7`. */
    private fun currentTag(): String = "Release_${BuildConfig.VERSION_NAME}"

    /** A release tag strictly above the running version, so the test never depends on the bumped version:
     *  strips any pre-release suffix ("2.0.0-beta" -> "2.0.0") and bumps the last numeric component. */
    private fun newerTag(): String {
        val base = BuildConfig.VERSION_NAME.substringBefore('-')
        val parts = base.split('.').toMutableList()
        parts[parts.size - 1] = ((parts.last().toIntOrNull() ?: 0) + 1).toString()
        return "Release_" + parts.joinToString(".")
    }

    @Test
    fun newerRelease_isReportedAsUpdateWithParsedFields() = runTest {
        val tag = newerTag()
        server.enqueue(release(tag))
        val result = UpdateChecker.check(latestUrl())
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success<UpdateCheckResult>).data
        assertTrue(data.updateAvailable)
        assertEquals(tag, data.latestVersion)
        assertEquals("https://github.com/pavel-janicek/fp-client/releases/tag/$tag", data.releaseUrl)
        assertEquals("2026-09-07T19:08:07Z", data.publishedAt)
    }

    @Test
    fun sameVersion_isNotAnUpdate() = runTest {
        server.enqueue(release(currentTag()))
        val result = UpdateChecker.check(latestUrl())
        assertTrue(result is ApiResult.Success)
        assertFalse((result as ApiResult.Success<UpdateCheckResult>).data.updateAvailable)
    }

    @Test
    fun failedHttp_isMappedToApiErrorWithStatusCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        val result = UpdateChecker.check(latestUrl())
        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).statusCode)
    }

    @Test
    fun malformedBody_isMappedToApiError() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        val result = UpdateChecker.check(latestUrl())
        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun missingTag_isMappedToApiError() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"Release 1.3.7"}"""))
        val result = UpdateChecker.check(latestUrl())
        assertTrue(result is ApiResult.Error)
    }

    @Test
    fun request_targetsReleasesLatestWithAppUserAgent() = runTest {
        server.enqueue(release("Release_1.3.7"))
        UpdateChecker.check(latestUrl())
        val recorded = server.takeRequest()
        assertEquals("/releases/latest", recorded.path)
        assertEquals("application/vnd.github+json", recorded.getHeader("Accept"))
        assertTrue(recorded.getHeader("User-Agent")!!.startsWith("FP-Client/"))
    }

    @Test
    fun versionOrdering_handlesPrefixesAndNumericComponents() {
        assertTrue(UpdateVersions.isNewer("Release_1.3.7", "1.3.6"))
        assertTrue(UpdateVersions.isNewer("Release_1.3.10", "1.3.6"))
        assertTrue(UpdateVersions.isNewer("2.0.0", "Release_1.9.9"))
        assertFalse(UpdateVersions.isNewer("Release_1.3.6", "1.3.6"))
        assertFalse(UpdateVersions.isNewer("1.3.5", "Release_1.3.6"))
        assertFalse(UpdateVersions.isNewer("1.3", "Release_1.3.0"))
        assertFalse(UpdateVersions.isNewer("garbage", "1.3.6"))
        // A release candidate is a pre-release of the final version, never newer than it.
        assertFalse(UpdateVersions.isNewer("1.3.6-rc2", "1.3.6"))
        // Conversely, an install running the RC must be told the final release is out.
        assertTrue(UpdateVersions.isNewer("1.3.6", "1.3.6-rc2"))
    }

    /**
     * Pre-release suffixes order semver-style — alpha < beta < rc1..rcN < final —
     * so installs running a 2.0.0-alpha/beta/rc build are prompted to update as
     * soon as the final 2.0.0 is published. The suffix must never be treated as
     * extra numeric components (that previously ranked "1.3.6-rc2" above "1.3.6"
     * and made "2.0.0-beta" equal to "2.0.0").
     */
    @Test
    fun versionOrdering_ranksPreReleaseSuffixes() {
        assertTrue(UpdateVersions.isNewer("2.0.0-beta", "2.0.0-alpha"))
        assertTrue(UpdateVersions.isNewer("2.0.0-rc1", "2.0.0-beta"))
        assertTrue(UpdateVersions.isNewer("Release_2.0.0", "2.0.0-alpha"))
        assertTrue(UpdateVersions.isNewer("Release_2.0.0", "2.0.0-beta"))
        assertTrue(UpdateVersions.isNewer("Release_2.0.0", "2.0.0-rc3"))
        assertTrue(UpdateVersions.isNewer("Release_2.0.0-beta", "2.0.0-alpha"))
        // Iterated pre-releases within one stage.
        assertTrue(UpdateVersions.isNewer("2.0.0-rc2", "2.0.0-rc1"))
        assertTrue(UpdateVersions.isNewer("2.0.0-rc10", "2.0.0-rc2"))
        assertTrue(UpdateVersions.isNewer("2.0.0-beta.2", "2.0.0-beta.1"))
        assertTrue(UpdateVersions.isNewer("2.0.0-beta2", "2.0.0-beta"))
        // And the wrong direction must never report an update.
        assertFalse(UpdateVersions.isNewer("2.0.0-alpha", "2.0.0-beta"))
        assertFalse(UpdateVersions.isNewer("2.0.0-rc1", "Release_2.0.0"))
        assertFalse(UpdateVersions.isNewer("2.0.0-beta", "2.0.0-beta"))
        assertFalse(UpdateVersions.isNewer("1.9.9-rc1", "2.0.0-alpha"))
    }

    /**
     * Regression coverage for the double-digit patch releases: a plain string
     * comparison would rank "1.3.10" below "1.3.9" and stop reporting updates.
     * The numeric-component comparison must keep the whole chain ordered.
     */
    @Test
    fun versionOrdering_handlesDoubleDigitPatchReleases() {
        assertTrue(UpdateVersions.isNewer("Release_1.3.10", "1.3.8"))
        assertTrue(UpdateVersions.isNewer("Release_1.3.10", "1.3.9"))
        assertTrue(UpdateVersions.isNewer("Release_1.3.11", "1.3.10"))
        // Equal or older versions must never be reported as an update.
        assertFalse(UpdateVersions.isNewer("Release_1.3.10", "1.3.10"))
        assertFalse(UpdateVersions.isNewer("Release_1.3.9", "1.3.10"))
        assertFalse(UpdateVersions.isNewer("Release_1.3.8", "1.3.10"))
    }
}
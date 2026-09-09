package com.fpclient.android.data.network

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

    @Test
    fun newerRelease_isReportedAsUpdateWithParsedFields() = runTest {
        server.enqueue(release("Release_1.3.7"))
        val result = UpdateChecker.check(latestUrl())
        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success<UpdateCheckResult>).data
        assertTrue(data.updateAvailable)
        assertEquals("Release_1.3.7", data.latestVersion)
        assertEquals("https://github.com/pavel-janicek/fp-client/releases/tag/Release_1.3.7", data.releaseUrl)
        assertEquals("2026-09-07T19:08:07Z", data.publishedAt)
    }

    @Test
    fun sameVersion_isNotAnUpdate() = runTest {
        server.enqueue(release("Release_1.3.6"))
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
        assertTrue(UpdateVersions.isNewer("1.3.6-rc2", "1.3.6"))
    }
}
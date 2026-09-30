package com.fpclient.android.data.repository

import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.FitPubApi
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Data export is the one feature FitPub serves through its **web client's own routes** rather
 * than the JSON API, and both of its halves have an easy-to-miss outcome:
 *
 *  - the request is the export page's form post, and acceptance is the `302` back to the page
 *    — a `2xx` never arrives, so treating "not successful" as failure would report every
 *    accepted request as broken;
 *  - the download redirects an unauthenticated caller to `/login`, so a client that follows
 *    redirects would happily report success and write the login page out as the user's
 *    archive. These tests build the client the way production does — redirects off.
 */
class DataExportRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var exports: DataExportRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().followRedirects(false).build())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(FitPubApi::class.java)
        exports = DataExportRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requestExport_confirmsReplacementAndReadsTheRedirectAsAcceptance() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .addHeader("Location", "/settings/export"),
        )

        val result = exports.requestExport()

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/settings/export", request.path)
        assertTrue(
            request.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"),
        )
        // The app cannot see whether a ready archive exists, so it always sends the
        // confirmation the server demands before replacing one.
        assertEquals("replaceExisting=true", request.body.readUtf8())
    }

    @Test
    fun requestExport_signedOutCallerGetsALoginPromptNotARawStatusCode() = runTest {
        // Not under /api/, so the server's entry point answers its JSON branch: 403.
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"error\":\"Access Denied\"}"))

        val result = exports.requestExport()

        assertTrue(result is ApiResult.Error)
        assertEquals(403, (result as ApiResult.Error).statusCode)
        assertEquals("Sign in to request a data export.", result.message)
    }

    @Test
    fun downloadArchive_streamsTheZipAndReportsProgressAgainstContentLength() = runTest {
        // Larger than the repository's 1 MiB reporting step, so an intermediate onProgress
        // call is expected as well as the final one.
        val payload = ByteArray(1024 * 1024 + 512) { (it % 251).toByte() }
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/zip")
                .setBody(okio.Buffer().write(payload)),
        )
        val target = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long?>>()

        val result = exports.downloadArchive(target) { written, total -> progress += written to total }

        assertTrue(result is ApiResult.Success)
        assertEquals(payload.size.toLong(), (result as ApiResult.Success).data)
        assertTrue(target.toByteArray().contentEquals(payload))
        assertEquals("/settings/export/download", server.takeRequest().path)
        assertEquals(payload.size.toLong(), progress.last().first)
        assertEquals(payload.size.toLong(), progress.last().second)
        // Throttled: one report per megabyte, not one per buffer.
        assertEquals(2, progress.size)
    }

    @Test
    fun downloadArchive_withNothingReadyExplainsThatNoArchiveExists() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("{\"status\":404,\"error\":\"Not Found\"}"),
        )
        val target = ByteArrayOutputStream()

        val result = exports.downloadArchive(target)

        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).statusCode)
        assertEquals("No data export archive is ready on this instance yet.", result.message)
        assertEquals(0, target.size())
    }

    @Test
    fun downloadArchive_neverSavesARedirectedLoginPageAsTheArchive() = runTest {
        // This is why the repository is built on the redirect-observing client: followed, a
        // 302 to /login would come back as a 200 HTML page and be written out as the ZIP.
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", "/login"),
        )
        val target = ByteArrayOutputStream()

        val result = exports.downloadArchive(target)

        assertTrue(result is ApiResult.Error)
        assertEquals(302, (result as ApiResult.Error).statusCode)
        assertEquals("Downloading the archive failed (HTTP 302).", result.message)
        assertEquals(0, target.size())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun downloadArchive_treatsAnEmptyBodyAsAFailedTransfer() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val target = ByteArrayOutputStream()

        val result = exports.downloadArchive(target)

        assertTrue(result is ApiResult.Error)
        assertEquals("The instance sent an empty archive.", (result as ApiResult.Error).message)
    }
}

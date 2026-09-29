package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.UserPreviewDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.FitPubApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The e-mail change handshake and the restricted-profile preview.
 *
 * Two things are easy to get wrong here:
 *  - `start` answers **202**, not 200, and the account only moves after `verify`;
 *  - the preview's `followStatus` is the server's own `NONE`/`PENDING`/`ACCEPTED`/`REJECTED`
 *    enum, which is *not* the richer `FollowStatusDto` shape — decoding it as one silently
 *    yields "not following" for everyone.
 */
class UserRepositoryEmailAndPreviewTest {

    private lateinit var server: MockWebServer
    private lateinit var users: UserRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(FitPubApi::class.java)
        users = UserRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun startingAnEmailChangeAcceptsTheServers202() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(202)
                .setBody("""{"message":"Email change started"}"""),
        )

        val result = users.startEmailChange("  new@example.test  ")

        assertTrue("202 is success, not an error", result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("/api/web/users/me/email-change", recorded.path)
        assertEquals("POST", recorded.method)
        // Trimmed before sending: the server validates with @Email, so stray spaces are a 400.
        assertTrue(recorded.body.readUtf8().contains("\"newEmail\":\"new@example.test\""))
    }

    @Test
    fun aRejectedEmailChangeKeepsTheServersReason() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"message":"That address is already in use"}"""),
        )

        val result = users.startEmailChange("taken@example.test")

        assertTrue(result is ApiResult.Error)
        assertEquals(400, (result as ApiResult.Error).statusCode)
        assertEquals("That address is already in use", result.message)
    }

    @Test
    fun verifyingSendsOnlyDigits() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"message":"Email address changed successfully"}"""),
        )

        val result = users.verifyEmailChange(" 123456 ")

        assertTrue(result is ApiResult.Success)
        val recorded = server.takeRequest()
        assertEquals("/api/web/users/me/email-change/verify", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"code\":\"123456\""))
    }

    @Test
    fun resendAndCancelHitTheirOwnRoutes() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(202).setBody("""{"message":"Verification code sent"}"""),
        )
        assertTrue(users.resendEmailChange() is ApiResult.Success)
        assertEquals(
            "/api/web/users/me/email-change/resend",
            server.takeRequest().path,
        )

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(users.cancelEmailChange() is ApiResult.Success)
        val cancel = server.takeRequest()
        assertEquals("/api/web/users/me/email-change", cancel.path)
        assertEquals("DELETE", cancel.method)
    }

    @Test
    fun previewDecodesTheServersOwnFollowStatusEnum() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"username":"sam","displayName":"Sam Example","avatarUrl":"/api/users/sam/avatar",
                 "profileVisibility":"FOLLOWERS","followStatus":"ACCEPTED"}
                """.trimIndent(),
            ),
        )

        val result = users.preview("@sam")

        assertTrue(result is ApiResult.Success)
        val preview = (result as ApiResult.Success).data
        assertEquals("Sam Example", preview.displayName)
        assertEquals("FOLLOWERS", preview.profileVisibility)
        assertTrue(preview.followed)
        assertFalse(preview.pending)
        // A leading @ is stripped: the route wants a plain username.
        assertEquals("/api/web/users/sam/preview", server.takeRequest().path)
    }

    @Test
    fun aPendingPreviewIsNotYetFollowed() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"username":"sam","followStatus":"PENDING"}"""),
        )

        val preview = (users.preview("sam") as ApiResult.Success).data

        assertTrue(preview.pending)
        assertFalse(preview.followed)
    }

    /** The Gravatar preview is image bytes; an empty body must not read as a usable image. */
    @Test
    fun gravatarPreviewReturnsRawBytes() = runTest {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(okio.Buffer().write(png)),
        )

        val result = users.gravatarPreview()

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.contentEquals(png))
        assertEquals("/api/web/users/me/avatar/gravatar-preview", server.takeRequest().path)
    }

    @Test
    fun anEmptyImageIsReportedRatherThanShownBlank() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val result = users.gravatarPreview()

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).message.orEmpty().contains("empty"))
    }

    @Test
    fun previewFollowHelpersNeverClaimFollowedForAnUnknownStatus() {
        assertFalse(UserPreviewDto(followStatus = null).followed)
        assertFalse(UserPreviewDto(followStatus = "REJECTED").followed)
        assertFalse(UserPreviewDto(followStatus = "NONE").followed)
    }
}


package com.fpclient.android.data.network

import android.content.Context
import com.fpclient.android.data.dto.ManualActivityRequest
import com.fpclient.android.data.session.Session
import com.fpclient.android.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Verifies the session interceptor's CSRF double-submit handling: tokens are primed from
 * the public `/login` page (JSON API GETs never emit the cookie), and a mutating request
 * rejected with 403 is retried once with a freshly primed token.
 */
class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var apiClient: ApiClient
    private val sessionStore: SessionStore = mock(SessionStore::class.java)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val url = server.url("/").toString().trimEnd('/')
        `when`(sessionStore.session).thenReturn(
            MutableStateFlow(Session(serverUrl = url, token = "jwt-1")),
        )
        apiClient = ApiClient(mock(Context::class.java), sessionStore)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun csrfPrimed() = MockResponse().setResponseCode(200)
        .addHeader("Set-Cookie: XSRF-TOKEN=tok-1; Path=/; SameSite=Lax")

    private fun manualRequest() = ManualActivityRequest(
        activityType = "RUN",
        startedAt = "2026-09-07T06:30:00Z",
        timezone = "Europe/Prague",
        durationSeconds = 3600,
        indoor = false,
    )

    @Test
    fun mutatingRequest_usesTokenPrimedFromLoginPage() = runTest {
        server.enqueue(csrfPrimed())
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val response = apiClient.api.createManualActivity(manualRequest())

        assertTrue(response.isSuccessful)
        val prime = server.takeRequest()
        assertEquals("GET", prime.method)
        assertEquals("/login", prime.path)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/api/web/activities/manual", post.path)
        assertEquals("tok-1", post.getHeader("X-XSRF-TOKEN"))
        assertTrue(post.getHeader("Cookie")!!.contains("JWT_TOKEN=jwt-1"))
        assertTrue(post.getHeader("Cookie")!!.contains("XSRF-TOKEN=tok-1"))
    }

    @Test
    fun mutatingRequest403_retriesOnceWithFreshlyPrimedToken() = runTest {
        server.enqueue(csrfPrimed())
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"error\":\"Forbidden\"}"))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Set-Cookie: XSRF-TOKEN=tok-2; Path=/; SameSite=Lax"),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val response = apiClient.api.createManualActivity(manualRequest())

        assertTrue(response.isSuccessful)
        // Priming GET, rejected POST, re-priming GET, successful retry.
        server.takeRequest()
        val rejected = server.takeRequest()
        assertEquals("tok-1", rejected.getHeader("X-XSRF-TOKEN"))
        server.takeRequest()
        val retried = server.takeRequest()
        assertEquals("POST", retried.method)
        assertEquals("tok-2", retried.getHeader("X-XSRF-TOKEN"))
    }

    @Test
    fun tokenIsCachedPerInstanceAndReusedBetweenCalls() = runTest {
        server.enqueue(csrfPrimed())
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        apiClient.api.createManualActivity(manualRequest())
        apiClient.api.createManualActivity(manualRequest())

        server.takeRequest()
        server.takeRequest()
        // No second priming GET before the second POST — the token is reused.
        val secondPost = server.takeRequest()
        assertEquals("POST", secondPost.method)
        assertEquals("tok-1", secondPost.getHeader("X-XSRF-TOKEN"))
    }
}
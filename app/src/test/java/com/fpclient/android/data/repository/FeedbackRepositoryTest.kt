package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.FeedbackSubmissionRequest
import com.fpclient.android.data.dto.FeedbackTopics
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
 * `POST /api/web/feedback` — sign-in only, answers **201** with `{"id": …}`, and answers 400
 * with its own wording when the topic or message is unusable. The wording is the point: the
 * server's message ("Choose a topic and enter a message of 1 to 5,000 characters.") is far
 * more useful than anything the app could invent, so it must reach the user.
 */
class FeedbackRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: FeedbackRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(productionJson().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FitPubApi::class.java)
        repository = FeedbackRepository(api)
    }

    /**
     * Mirrors `ApiClient`'s `Json`, which the app really uses.
     *
     * `encodeDefaults = true` matters here: with the default (`false`) a `replyAllowed = false`
     * would be *omitted* from the body altogether, so this test would assert something the
     * shipped app never sends.
     */
    private fun productionJson() = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun submitsFeedbackAndReadsBackTheNewId() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7"}"""),
        )

        val result = repository.submit(
            FeedbackSubmissionRequest(
                topic = FeedbackTopics.BUG_REPORT,
                message = "The heatmap does not load.",
                replyAllowed = true,
            ),
        )

        assertTrue(result is ApiResult.Success)
        assertEquals(
            "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            (result as ApiResult.Success).data.id,
        )

        val recorded = server.takeRequest()
        assertEquals("/api/web/feedback", recorded.path)
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"topic\":\"BUG_REPORT\""))
        assertTrue(body.contains("\"message\":\"The heatmap does not load.\""))
        // The privacy decision must reach the server explicitly, both ways round.
        assertTrue(body.contains("\"replyAllowed\":true"))
    }

    @Test
    fun replyAllowedIsSentFalseWhenTheUserDeclinesEmailReplies() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"abc"}"""))

        repository.submit(
            FeedbackSubmissionRequest(
                topic = FeedbackTopics.OTHER,
                message = "Thanks for the app.",
                replyAllowed = false,
            ),
        )

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"replyAllowed\":false"))
    }

    @Test
    fun aRejectedSubmissionSurfacesTheServersOwnWording() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"status":"BAD_REQUEST","error":"Choose a topic and enter a message of 1 to 5,000 characters."}""",
            ),
        )

        val result = repository.submit(
            FeedbackSubmissionRequest(topic = FeedbackTopics.OTHER, message = "   "),
        )

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(400, error.statusCode)
        assertTrue(
            "the server's wording should survive: ${error.message}",
            error.message.orEmpty().contains("Choose a topic"),
        )
    }

    /** A 201 with an empty body must not be read as a failure. */
    @Test
    fun anEmptySuccessBodyIsStillASuccess() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = repository.submit(
            FeedbackSubmissionRequest(topic = FeedbackTopics.OTHER, message = "hi"),
        )

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun topicsCoverTheServersEnumInPickerOrder() {
        assertEquals(
            listOf("BUG_REPORT", "FEATURE_REQUEST", "SUPPORT_REQUEST", "OTHER"),
            FeedbackTopics.ALL,
        )
        assertEquals("Bug report", FeedbackTopics.label(FeedbackTopics.BUG_REPORT))
        assertEquals("Feature request", FeedbackTopics.label(FeedbackTopics.FEATURE_REQUEST))
        assertEquals("Support request", FeedbackTopics.label(FeedbackTopics.SUPPORT_REQUEST))
        assertEquals("Other", FeedbackTopics.label(FeedbackTopics.OTHER))
        // An unknown future topic degrades to readable text, not SHOUTING_ENUM.
        assertEquals("Something new", FeedbackTopics.label("SOMETHING_NEW"))
        assertFalse(FeedbackTopics.label(null).isEmpty())
    }
}

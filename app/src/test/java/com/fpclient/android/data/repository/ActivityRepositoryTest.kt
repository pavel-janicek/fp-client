package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.ActivityDto
import com.fpclient.android.data.network.ApiResult
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
 * Pins the activity-detail call to the **web** route (`GET /api/web/activities/{id}`).
 * The federation route `GET /api/activities/{id}` returns the minimal
 * `PublishedActivityDTO` without author fields, which made the detail screen's
 * author card always fall back to a generic "Athlete" — this test would fail
 * if the endpoint ever regressed to the federation route.
 */
class ActivityRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ActivityRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(com.fpclient.android.data.network.FitPubApi::class.java)
        repository = ActivityRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Mirrors the server's `ActivityDTO.fromEntityWithFiltering` author fields. */
    private fun detailBody() = """
        {
          "id": "d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60",
          "activityType": "Run",
          "title": "Morning 10k",
          "username": "sam",
          "displayName": "Sam Runner",
          "avatarUrl": "/uploads/sam-avatar.png",
          "actorUri": "https://fitpub.example/api/actors/sam",
          "isLocal": true,
          "visibility": "PUBLIC",
          "createdAt": "2026-09-10T06:31:02Z"
        }
    """.trimIndent()

    @Test
    fun detail_usesWebRouteAndParsesAuthorFields() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(detailBody()))

        val result = repository.detail("d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60")

        assertTrue(result is ApiResult.Success)
        val activity = (result as ApiResult.Success<ActivityDto>).data
        assertEquals("sam", activity.resolvedUsername)
        assertEquals("Sam Runner", activity.resolvedDisplayName)
        assertEquals("/uploads/sam-avatar.png", activity.resolvedAvatarUrl)
        assertEquals("/api/web/activities/d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60", server.takeRequest().path)
    }

    @Test
    fun detail_mapsHttpErrorsWithStatusCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val result = repository.detail("d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60")

        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).statusCode)
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}

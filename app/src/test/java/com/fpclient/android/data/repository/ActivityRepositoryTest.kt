package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.ActivityDto
import com.fpclient.android.data.dto.ActivityTrimDataDto
import com.fpclient.android.data.dto.ActivityTrimSelection
import com.fpclient.android.data.dto.ActivityUpdateRequest
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

    /** Mirrors the server's `ActivityTrimDataDTO`: original track + the range now stored. */
    private fun trimBody() = """
        {
          "points": [
            {"index":0,"timestamp":"2026-09-10T06:00:00Z","latitude":48.1,"longitude":16.3,"elevation":180.0,"distance":0.0,"speed":0.0,"elevationSegment":0},
            {"index":1,"timestamp":"2026-09-10T06:00:10Z","latitude":48.1001,"longitude":16.3,"elevation":181.5,"distance":11.1,"speed":1.1,"elevationSegment":0}
          ],
          "currentStartIndex": 2,
          "currentEndIndex": 98,
          "currentElevationGain": 42.0,
          "currentElevationLoss": 40.5,
          "originalElevationGain": 50.0,
          "originalElevationLoss": 48.0
        }
    """.trimIndent()

    @Test
    fun trimData_usesTrimRouteAndParsesWorkspace() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(trimBody()))

        val result = repository.trimData("d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60")

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success<ActivityTrimDataDto>).data
        assertEquals(2, data.currentStartIndex)
        assertEquals(98, data.currentEndIndex)
        assertEquals(2, data.points.size)
        assertEquals(48.1, data.points[0].latitude, 0.0)
        assertEquals(42.0, data.currentElevationGain!!, 0.0)
        assertEquals(50.0, data.originalElevationGain!!, 0.0)
        assertEquals(
            "/api/web/activities/d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60/trim",
            server.takeRequest().path,
        )
    }

    @Test
    fun trimData_surfacesServerRefusalVerbatim() = runTest {
        // What the server answers when the source cannot be trimmed at all
        // (`ApiExceptionHandler` → 400 with its own message); the app shows it unchanged.
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"message":"Manual activities cannot be trimmed"}"""),
        )

        val result = repository.trimData("d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60")

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(400, error.statusCode)
        assertEquals("Manual activities cannot be trimmed", error.message)
    }

    @Test
    fun trimData_mapsUnauthorized() = runTest {
        // Trim data is owner-only; a signed-out or foreign caller must not see a generic
        // "success with no body" path.
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Unauthorized"}"""))

        val result = repository.trimData("d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60")

        assertTrue(result is ApiResult.Error)
        assertEquals(401, (result as ApiResult.Error).statusCode)
    }

    @Test
    fun update_carriesTrimSelection() = runTest {
        // There is no dedicated apply endpoint: the range rides along with the ordinary
        // activity update, so the PUT body must contain it verbatim.
        server.enqueue(MockResponse().setResponseCode(200).setBody(detailBody()))

        repository.update(
            "d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60",
            ActivityUpdateRequest(
                title = "Morning 10k",
                visibility = "PUBLIC",
                trim = ActivityTrimSelection(startIndex = 3, endIndex = 77),
            ),
        )

        val request = server.takeRequest()
        assertEquals("/api/web/activities/d0b17f61-0b2e-4a5d-9a3e-1f2c3d4e5f60", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"trim\":{\"startIndex\":3,\"endIndex\":77}"))
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}

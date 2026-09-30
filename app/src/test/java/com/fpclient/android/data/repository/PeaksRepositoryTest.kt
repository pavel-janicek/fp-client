package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.UserPeakDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.FitPubApi
import com.fpclient.android.util.TrackParser
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The peaks routes — a profile feature with two easy-to-miss shapes.
 *
 *  - the page endpoint is **flat** (`content`/`number`/`totalPages`/`totalElements`), not the
 *    `{content, page}` envelope every other paginated route on the server uses, so decoding it
 *    as the shared envelope yields an always-empty list;
 *  - a hidden peak list is **200 with an empty list**, not a 403, so "not shared" and "none
 *    reached" arrive looking identical and an empty success must not be reported as an error.
 */
class PeaksRepositoryTest {

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
    fun theFlatPageShapeIsReadWithoutAPageEnvelope() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"content":[{"id":7,"name":"Triglav","type":"PEAK","elevation":2864,
                             "visitCount":3,"latestVisitedAt":"2026-07-04T09:12:00Z"}],
                 "number":0,"size":24,"totalPages":2,"totalElements":25}
                """.trimIndent(),
            ),
        )

        val result = users.peaksPage("sam")

        assertTrue(result is ApiResult.Success)
        val page = (result as ApiResult.Success).data
        assertEquals(1, page.content.size)
        assertEquals(0, page.number)
        assertEquals(2, page.totalPages)
        assertEquals(25L, page.totalElements)
        assertEquals("Triglav", page.content.first().name)
        assertEquals("/api/web/users/sam/peaks/page?page=0", server.takeRequest().path)
    }

    @Test
    fun hiddenPeakListsAreAnEmptySuccessNotAnError() = runTest {
        // The server hides peaks by answering 200 with an empty list, so this must stay a
        // success: turning it into an error would tell the user something broke.
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val result = users.recentPeaks("sam")

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.isEmpty())
        assertEquals("/api/web/users/sam/peaks/recent", server.takeRequest().path)
    }

    @Test
    fun federatedAndAtPrefixedHandlesBecomeAPlainLocalUsername() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        users.recentPeaks("  @sam@remote.example  ")

        assertEquals("/api/web/users/sam/peaks/recent", server.takeRequest().path)
    }

    @Test
    fun aHiddenSinglePeakIsA404() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{"message":"Peak not found"}"""),
        )

        val result = users.peak("sam", 42)

        assertTrue(result is ApiResult.Error)
        assertEquals(404, (result as ApiResult.Error).statusCode)
        assertEquals("Peak not found", result.message)
        assertEquals("/api/web/users/sam/peaks/42", server.takeRequest().path)
    }

    @Test
    fun peakTracksKeepRawGeoJsonThatTrackParserAlreadyUnderstands() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{"activityId":"act-1",
                  "route":{"type":"LineString","coordinates":[[14.5,46.0],[14.6,46.1]]}}]
                """.trimIndent(),
            ),
        )

        val result = users.peakTracks("sam", 42)

        assertTrue(result is ApiResult.Success)
        val tracks = (result as ApiResult.Success).data
        assertEquals(1, tracks.size)
        assertEquals("act-1", tracks.first().activityId)
        assertEquals(
            "/api/web/activities/user/sam/peaks/42/tracks",
            server.takeRequest().path,
        )
        // The route is a raw GeoJSON object, so it is handed to the existing parser rather
        // than a peaks-specific one.
        val route = tracks.first().route
        assertNotNull(route)
        val type = route!!["type"]!!.toString().trim('"')
        val segments = TrackParser.fromGeometry(type, route["coordinates"])
        assertEquals(1, segments.size)
        assertEquals(2, segments.first().size)
        assertEquals(46.0, segments.first().first().latitude, 1e-9)
        assertEquals(14.5, segments.first().first().longitude, 1e-9)
    }

    @Test
    fun theKindLabelDegradesForAPeakTypeTheAppDoesNotKnow() {
        assertEquals("Volcano", UserPeakDto(type = "VOLCANO").kindLabel)
        assertEquals("Pass", UserPeakDto(type = "MOUNTAIN_PASS").kindLabel)
        assertEquals("Saddle", UserPeakDto(type = "SADDLE").kindLabel)
        // Most peaks carry no type at all, and the server may add one later.
        assertEquals("Peak", UserPeakDto(type = null).kindLabel)
        assertEquals("Hill", UserPeakDto(type = "HILL").kindLabel)
    }
}

package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.KomootActivityImportRequest
import com.fpclient.android.data.dto.KomootImportRequest
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant

/**
 * Komoot import talks to the `/api/web/komoot-import` endpoints, which are opt-in on the
 * server. Two things here are easy to get wrong and impossible to notice by hand:
 *
 *  - the **404** that means "Komoot support is disabled" must not be shown as a raw
 *    failure — it is a state the screen explains and recovers from;
 *  - `date` is a Jackson `OffsetDateTime`, i.e. an ISO-8601 string *with* an offset. Were it
 *    an epoch array, deserialising the whole response would throw and the list would come
 *    back empty.
 */
class KomootImportRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: KomootImportRepository

    private val request = KomootImportRequest(
        email = "sam@example.test",
        password = "hunter2",
        userId = "123456",
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true; isLenient = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(FitPubApi::class.java)
        repository = KomootImportRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listsActivitiesFromTheServersPayload() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"userId":"123456","totalCount":2,"activities":[
                  {"id":9001,"name":"Morning ride","sport":"cycling","mappedActivityType":"RIDE",
                   "status":"completed","type":"tour","date":"2024-05-01T10:00:00+02:00",
                   "distanceMeters":42100.5,"durationSeconds":5400,"timeInMotionSeconds":5100,
                   "elevationUp":512.0,"imported":false,"fitPubActivityId":null},
                  {"id":9002,"name":"Old hike","sport":"hiking","mappedActivityType":"HIKE",
                   "status":"completed","type":"hike","date":"2024-04-02T08:30:00+02:00",
                   "distanceMeters":8000.0,"durationSeconds":3600,"timeInMotionSeconds":3400,
                   "elevationUp":300.0,"imported":true,
                   "fitPubActivityId":"3f6c1c1e-1111-2222-3333-444455556666"}
                ]}
                """.trimIndent(),
            ),
        )

        val result = repository.activities(request)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(2, data.activities.size)
        val ride = data.activities.first()
        assertEquals(9001L, ride.id)
        assertEquals("RIDE", ride.mappedActivityType)
        assertEquals(false, ride.imported)
        // The offset is honoured, not read as UTC: 10:00+02:00 is 08:00Z.
        assertEquals("2024-05-01T10:00:00+02:00", ride.date)
        assertEquals(
            Instant.parse("2024-05-01T08:00:00Z"),
            Instant.parse(ride.date!!),
        )

        // The server's own duplicate flag decides what may still be imported.
        assertEquals(listOf(9001L), repository.importable(data.activities).map { it.id })

        val recorded = server.takeRequest()
        assertEquals("/api/web/komoot-import/activities", recorded.path)
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"userId\":\"123456\""))
        assertTrue(body.contains("\"email\":\"sam@example.test\""))
        // No date range requested -> both dates absent, never half-set.
        assertTrue(!body.contains("startDate"))
    }


    @Test
    fun aDisabledInstanceIsItsOwnStateNotAGenericError() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"error":"Komoot support is disabled."}"""),
        )

        val result = repository.activities(request)

        assertTrue(
            "expected the shared disabled marker",
            result === KomootImportRepository.KomootDisabled,
        )
        assertEquals(404, KomootImportRepository.KomootDisabled.statusCode)
    }

    @Test
    fun aRejectedRequestSurfacesTheServersMessage() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":"Komoot user ID must be alphanumeric"}"""),
        )

        val result = repository.activities(request)

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(400, error.statusCode)
        assertEquals("Komoot user ID must be alphanumeric", error.message)
    }

    @Test
    fun importsOneActivityAtATime() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"importedActivityId":"3f6c1c1e-1111-2222-3333-444455556666",
                 "importedKomootActivityId":9001,"status":"IMPORTED","message":"Imported."}
                """.trimIndent(),
            ),
        )

        val result = repository.importActivity(
            KomootActivityImportRequest(
                email = "sam@example.test",
                password = "hunter2",
                userId = "123456",
                activityId = 9001L,
            ),
        )

        assertTrue(result is ApiResult.Success)
        assertEquals(
            "3f6c1c1e-1111-2222-3333-444455556666",
            (result as ApiResult.Success).data.importedActivityId,
        )
        val recorded = server.takeRequest()
        assertEquals("/api/web/komoot-import/activities/import", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"activityId\":9001"))
    }
}

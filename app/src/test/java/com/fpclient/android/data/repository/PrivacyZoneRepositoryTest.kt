package com.fpclient.android.data.repository

import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.FitPubApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
 * Pins the privacy-zone request contract.
 *
 * The server's Create/UpdatePrivacyZoneRequest declare name, latitude, longitude and
 * radiusMeters @NotNull (radius 50..10000), so a partial body is rejected with 400 — the
 * update body must carry the complete set. The toggle endpoint
 * (`PATCH /api/web/privacy-zones/{id}/toggle`) is not a server-side flip either: it takes the
 * desired state as `{"isActive": <bool>}` and answers 400 "Required request body is missing"
 * for a bodyless PATCH. Responses carry `isActive` (not `enabled`).
 */
class PrivacyZoneRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: PrivacyZoneRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FitPubApi::class.java)
        repository = PrivacyZoneRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Mirrors the real `PrivacyZoneDTO` payload, including keys the client ignores. */
    private fun zoneJson(isActive: Boolean = true) = """
        {"id":"zone-1","userId":"user-1","name":"Home","description":null,"latitude":48.1,"longitude":11.5,"radiusMeters":750,"isActive":$isActive,"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}
    """.trimIndent()

    @Test
    fun create_postsCompleteContractToTheWebRoute() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(zoneJson()))

        repository.create("Home", 48.1, 11.5, 750)

        val recorded = server.takeRequest()
        assertEquals("/api/web/privacy-zones", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"name\":\"Home\""))
        assertTrue(body.contains("\"latitude\":48.1"))
        assertTrue(body.contains("\"longitude\":11.5"))
        assertTrue(body.contains("\"radiusMeters\":750"))
    }

    @Test
    fun update_sendsNameCenterAndRadiusBecauseTheServerRequiresAllFields() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(zoneJson()))

        repository.update("zone-1", "Home", 48.1, 11.5, 750)

        val recorded = server.takeRequest()
        assertEquals("/api/web/privacy-zones/zone-1", recorded.path)
        assertEquals("PUT", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"latitude\":48.1"))
        assertTrue(body.contains("\"longitude\":11.5"))
        assertTrue(body.contains("\"radiusMeters\":750"))
        assertTrue(body.contains("\"name\":\"Home\""))
    }

    @Test
    fun toggle_patchesTheWebRouteWithTheIsActiveBodyTheServerReads() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(zoneJson(isActive = false)))

        val result = repository.toggle("zone-1", false)

        val recorded = server.takeRequest()
        assertEquals("/api/web/privacy-zones/zone-1/toggle", recorded.path)
        assertEquals("PATCH", recorded.method)
        // Bodyless PATCH → 400 "Required request body is missing"; the server reads "isActive".
        assertEquals("{\"isActive\":false}", recorded.body.readUtf8())

        val zone = (result as ApiResult.Success).data
        assertFalse(zone.isActive)
    }

    @Test
    fun togglingBackOnSendsTrue() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(zoneJson(isActive = true)))

        val result = repository.toggle("zone-1", true)

        assertEquals("{\"isActive\":true}", server.takeRequest().body.readUtf8())
        assertTrue((result as ApiResult.Success).data.isActive)
    }

    @Test
    fun list_readsIsActiveFromTheServerPayload() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[${zoneJson(isActive = false)}]"))

        val result = repository.list()

        assertEquals("/api/web/privacy-zones", server.takeRequest().path)
        assertFalse((result as ApiResult.Success).data.single().isActive)
    }

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}

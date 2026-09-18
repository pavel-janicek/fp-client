package com.fpclient.android.data.repository

import com.fpclient.android.data.network.FitPubApi
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
 * Pins the privacy-zone request contract. The server's Create/UpdatePrivacyZoneRequest
 * declare name, latitude, longitude and radiusMeters @NotNull (radius 50..10000), so a
 * partial body is rejected with 400 — the update body must carry the complete set.
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

    private fun zoneJson() = """
        {"id":"zone-1","name":"Home","latitude":48.1,"longitude":11.5,"radiusMeters":750,"enabled":true}
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

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}

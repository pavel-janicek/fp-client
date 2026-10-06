package com.fpclient.android.wear.recording

import com.fpclient.android.wear.auth.WearAuthState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchActivityUploaderTest {
    @Test
    fun uploadPrimesCsrfAndPostsSessionCookieMultipart() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Set-Cookie", "XSRF-TOKEN=csrf-value; Path=/"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"activity-1","activityType":"RUN"}"""))
        server.start()
        val gpx = File.createTempFile("watch-track", ".gpx").apply { writeText("<gpx/>") }
        val pending = PendingWatchWorkout(10L, gpx.name, "track.json", "RUN", "Run workout", createdAtEpochMs = 1L)
        val auth = WearAuthState(server.url("/").toString().trimEnd('/'), "watch-jwt", "runner", "Runner")

        val uploaded = WatchActivityUploader(
            OkHttpClient.Builder().followRedirects(false).build(),
        ).upload(pending, gpx, auth)

        assertTrue(uploaded)
        val csrfPrime = server.takeRequest()
        assertEquals("/login", csrfPrime.path)
        val uploadRequest = server.takeRequest()
        assertEquals("POST", uploadRequest.method)
        assertEquals("JWT_TOKEN=watch-jwt; XSRF-TOKEN=csrf-value", uploadRequest.getHeader("Cookie"))
        assertEquals("csrf-value", uploadRequest.getHeader("X-XSRF-TOKEN"))
        assertTrue(uploadRequest.body.readUtf8().contains("name=\"file\""))
        gpx.delete()
        server.shutdown()
    }

    @Test
    fun missingSessionDoesNotCallNetwork() = runBlocking {
        val server = MockWebServer()
        server.start()
        val gpx = File.createTempFile("watch-track", ".gpx").apply { writeText("<gpx/>") }
        val pending = PendingWatchWorkout(11L, gpx.name, "track.json", "RUN", "Run workout", createdAtEpochMs = 1L)

        val uploaded = WatchActivityUploader().upload(pending, gpx, WearAuthState())

        assertFalse(uploaded)
        assertEquals(0, server.requestCount)
        gpx.delete()
        server.shutdown()
    }
}
package com.fpclient.android.wear.recording

import com.fpclient.android.wear.auth.WearAuthState
import com.fpclient.android.wear.recording.PendingWatchWorkout
import com.fpclient.android.wear.recording.WatchActivityUploader
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchActivityUploaderVisibilityFallbackTest {

    @Test
    fun missingVisibilityFallsBackToPublicOnUploadAndUpdate() {
        runBlocking {
            val server = MockWebServer()
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Set-Cookie", "XSRF-TOKEN=csrf-value; Path=/"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setBody("""{"id":"activity-1","activityType":null,"visibility":null}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Set-Cookie", "XSRF-TOKEN=csrf-value; Path=/"),
            )
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()

            val gpx = File.createTempFile("watch-track", ".gpx").apply { writeText("<gpx/>") }
            val pending =
                PendingWatchWorkout(
                    sessionId = 1L,
                    gpxFileName = "t.gpx",
                    sidecarFileName = "t.json",
                    activityType = "RUN",
                    title = "Run",
                    visibility = "PUBLIC",
                    createdAtEpochMs = 1L,
                )
            val auth =
                WearAuthState(server.url("/").toString(), "watch-jwt", "runner", "Runner")

            val uploaded = WatchActivityUploader().upload(pending, gpx, auth)

            assertTrue(uploaded)
            assertEquals(4, server.requestCount)
            val loginFirst = server.takeRequest()
            assertEquals("GET", loginFirst.method)
            assertEquals("/login", loginFirst.path)
            val uploadRequest = server.takeRequest()
            assertEquals("POST", uploadRequest.method)
            assertTrue(uploadRequest.body.readUtf8().contains("name="))
            val loginSecond = server.takeRequest()
            assertEquals("GET", loginSecond.method)
            assertEquals("/login", loginSecond.path)
            val updateRequest = server.takeRequest()
            assertEquals("PUT", updateRequest.method)
            assertEquals("/api/web/activities/activity-1", updateRequest.path)
            val updateBody = updateRequest.body.readUtf8()
            assertTrue(updateBody.contains("\"visibility\":\"PUBLIC\""))
            assertTrue(updateBody.contains("\"title\":\"Run\""))
            server.shutdown()
            gpx.delete()
        }
    }
}

package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchWorkoutSyncStoreCodecTest {
    @Test
    fun pendingWorkoutQueueCodecPreservesRetryAndOwnerMetadata() {
        val pending = PendingWatchWorkout(
            sessionId = 123L,
            gpxFileName = "workout-123.gpx",
            sidecarFileName = "workout-123.json",
            activityType = "RUN",
            title = "Run workout",
            ownerServerUrl = "https://fitpub.example",
            ownerUsername = "runner",
            createdAtEpochMs = 456L,
            directUploadAttempted = true,
            relayRequested = true,
            lastError = "network unavailable",
        )

        assertEquals(listOf(pending), WatchWorkoutSyncStore.decode(WatchWorkoutSyncStore.encode(listOf(pending))))
        assertTrue(WatchWorkoutSyncStore.decode("broken").isEmpty())
    }
}
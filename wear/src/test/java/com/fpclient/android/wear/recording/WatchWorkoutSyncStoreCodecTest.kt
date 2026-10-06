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

    @Test
    fun watchProtocolConstantsMatchDataLayerContract() {
        assertEquals("/fitpub/workout/", WorkoutSyncProtocol.DATA_PATH_PREFIX)
        assertEquals("/fitpub/workout/synced", WorkoutSyncProtocol.ACK_PATH)
        assertEquals("session_id", WorkoutSyncProtocol.KEY_ID)
        assertEquals("activity_type", WorkoutSyncProtocol.KEY_ACTIVITY_TYPE)
        assertEquals("title", WorkoutSyncProtocol.KEY_TITLE)
        assertEquals("description", WorkoutSyncProtocol.KEY_DESCRIPTION)
        assertEquals("visibility", WorkoutSyncProtocol.KEY_VISIBILITY)
        assertEquals("owner_server", WorkoutSyncProtocol.KEY_OWNER_SERVER)
        assertEquals("owner_username", WorkoutSyncProtocol.KEY_OWNER_USERNAME)
        assertEquals("workout_gpx", WorkoutSyncProtocol.ASSET_GPX)
        assertEquals("workout_sidecar", WorkoutSyncProtocol.ASSET_SIDECAR)
    }
}
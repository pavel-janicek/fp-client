package com.fpclient.android.wear

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WearWorkoutInboxEntryTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val serializer = ListSerializer(WearWorkoutInboxEntry.serializer())

    @Test
    fun `wear workout inbox entry serialization preserves all sync fields`() {
        val entry = WearWorkoutInboxEntry(
            sessionId = 1700000000000L,
            gpxFileName = "workout-1700000000000.gpx",
            sidecarFileName = "workout-1700000000000.json",
            activityType = "HIKE",
            title = "Mountain Summit",
            description = "Great climb",
            visibility = "PUBLIC",
            ownerServerUrl = "https://fitpub.example.org",
            ownerUsername = "climber",
            sourceNodeId = "watch-node-123",
            dataItemUri = "wear://watch-node-123/fitpub/workout/1700000000000",
            receivedAtEpochMs = 1700000001000L,
            attempts = 1,
            lastError = "HTTP 503",
            blocked = false,
            uploadedActivityId = null,
        )

        val encoded = json.encodeToString(serializer, listOf(entry))
        val decoded = json.decodeFromString(serializer, encoded).single()

        assertEquals(1700000000000L, decoded.sessionId)
        assertEquals("workout-1700000000000.gpx", decoded.gpxFileName)
        assertEquals("workout-1700000000000.json", decoded.sidecarFileName)
        assertEquals("HIKE", decoded.activityType)
        assertEquals("Mountain Summit", decoded.title)
        assertEquals("Great climb", decoded.description)
        assertEquals("PUBLIC", decoded.visibility)
        assertEquals("https://fitpub.example.org", decoded.ownerServerUrl)
        assertEquals("climber", decoded.ownerUsername)
        assertEquals("watch-node-123", decoded.sourceNodeId)
        assertEquals("wear://watch-node-123/fitpub/workout/1700000000000", decoded.dataItemUri)
        assertEquals(1, decoded.attempts)
        assertEquals("HTTP 503", decoded.lastError)
        assertFalse(decoded.blocked)
        assertNull(decoded.uploadedActivityId)
    }

    @Test
    fun `protocol path constants match between phone and watch`() {
        assertEquals("/fitpub/workout/", PhoneWorkoutSyncProtocol.DATA_PATH_PREFIX)
        assertEquals("/fitpub/workout/synced", PhoneWorkoutSyncProtocol.ACK_PATH)
        assertEquals("session_id", PhoneWorkoutSyncProtocol.KEY_ID)
        assertEquals("workout_gpx", PhoneWorkoutSyncProtocol.ASSET_GPX)
        assertEquals("workout_sidecar", PhoneWorkoutSyncProtocol.ASSET_SIDECAR)
    }
}

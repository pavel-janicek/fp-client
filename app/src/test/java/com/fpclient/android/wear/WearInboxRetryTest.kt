package com.fpclient.android.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Watch workouts the server refused are not resent by every 15-minute sync run. */
class WearInboxRetryTest {

    private fun entry(id: Long, blocked: Boolean, rejected: Boolean, error: String?) = WearWorkoutInboxEntry(
        sessionId = id,
        gpxFileName = "workout-$id.gpx",
        sidecarFileName = "workout-$id.json",
        activityType = "WALK",
        title = "Walk workout",
        ownerServerUrl = "https://example.org",
        ownerUsername = "me",
        sourceNodeId = "node",
        dataItemUri = "wear://node/fitpub/workout/$id",
        receivedAtEpochMs = id,
        lastError = error,
        blocked = blocked,
        rejectedByServer = rejected,
    )

    private val rejected = entry(1, blocked = true, rejected = true, error = "Failed to parse FIT file")
    private val transient = entry(2, blocked = true, rejected = false, error = "Workout file is missing")

    @Test
    fun theAutomaticPassKeepsServerRejectedEntriesBlockedWithTheirMessage() {
        val (stillRejected, retried) = listOf(rejected, transient).afterAutomaticRetry()

        assertTrue(stillRejected.blocked)
        assertEquals("Failed to parse FIT file", stillRejected.lastError)
        assertFalse(retried.blocked)
        assertNull(retried.lastError)
    }

    @Test
    fun syncingOneEntryByHandGivesOnlyThatOneAnotherTry() {
        val other = entry(3, blocked = true, rejected = true, error = "x")

        val (retried, untouched) = listOf(rejected, other).afterManualRetry(sessionId = 1)

        assertFalse(retried.blocked)
        assertFalse(retried.rejectedByServer)
        assertNull(retried.lastError)
        assertEquals(other, untouched)
    }

    @Test
    fun syncAllByHandRetriesEveryEntry() {
        val after = listOf(rejected, transient).afterManualRetry(sessionId = null)

        assertTrue(after.none { it.blocked || it.rejectedByServer || it.lastError != null })
    }
}

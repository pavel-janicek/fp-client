package com.fpclient.android.wear.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchWorkoutSyncRouteTest {

    private val queued = PendingWatchWorkout(
        sessionId = 1L,
        gpxFileName = "workout-1.gpx",
        sidecarFileName = "workout-1.json",
        activityType = "WALK",
        createdAtEpochMs = 1L,
    )

    @Test
    fun freshWorkoutUploadsDirectlyWithInternetAndMatchingAccount() {
        assertTrue(shouldUploadDirectly(queued, matchesCurrentAccount = true, hasInternet = true))
        assertFalse(shouldUploadDirectly(queued, matchesCurrentAccount = true, hasInternet = false))
        assertFalse(shouldUploadDirectly(queued, matchesCurrentAccount = false, hasInternet = true))
    }

    @Test
    fun relayedWorkoutIsNeverUploadedDirectlyToo() {
        // Recorded offline, relayed, then the watch got internet before the phone uploaded it:
        // a direct upload now would be the second copy.
        val relayed = queued.copy(relayRequested = true)
        assertFalse(shouldUploadDirectly(relayed, matchesCurrentAccount = true, hasInternet = true))
    }

    @Test
    fun directUploadIsTriedOnlyOnce() {
        val attempted = queued.copy(directUploadAttempted = true)
        assertFalse(shouldUploadDirectly(attempted, matchesCurrentAccount = true, hasInternet = true))
    }
}

package com.fpclient.android.recording

import com.fpclient.android.data.network.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pending-workout retry pass: a file the server refuses must not block newer workouts. */
class RetryPendingUploadsTest {

    private fun entry(id: Long, requested: Boolean = true, rejected: Boolean = false) = PendingUpload(
        sessionId = id,
        startedAtEpochMs = id,
        activityType = "WALK",
        createdAtEpochMs = id,
        requestedByUser = requested,
        rejectedByServer = rejected,
    )

    private val ok = ApiResult.Success(Unit)
    private val invalidFile = ApiResult.Error("No GPS track points found in GPX file", statusCode = 400)
    private val offline = ApiResult.Error("Network error", statusCode = null)

    @Test
    fun aServerRejectedWorkoutIsPassedOverSoNewerOnesStillUpload() = runTest {
        val tried = mutableListOf<Long>()
        val results = mapOf(1L to invalidFile, 2L to ok, 3L to ok)

        val uploaded = retryPendingUploads(listOf(entry(3), entry(1), entry(2)), onlyUserRequested = false) {
            tried += it.sessionId
            results.getValue(it.sessionId)
        }

        assertEquals(listOf(1L, 2L, 3L), tried)
        assertEquals(2, uploaded)
    }

    @Test
    fun aTransientFailureStillStopsThePass() = runTest {
        val tried = mutableListOf<Long>()

        val uploaded = retryPendingUploads(listOf(entry(1), entry(2)), onlyUserRequested = false) {
            tried += it.sessionId
            offline
        }

        assertEquals(listOf(1L), tried)
        assertEquals(0, uploaded)
    }

    @Test
    fun theAppStartPassDoesNotResendWhatTheServerRefused() = runTest {
        val tried = mutableListOf<Long>()

        retryPendingUploads(
            listOf(entry(1, rejected = true), entry(2), entry(3, requested = false)),
            onlyUserRequested = true,
        ) {
            tried += it.sessionId
            ok
        }

        assertEquals(listOf(2L), tried)
    }

    @Test
    fun aUserDrivenRetryTriesTheRejectedOneAgain() = runTest {
        val tried = mutableListOf<Long>()

        retryPendingUploads(listOf(entry(1, rejected = true)), onlyUserRequested = false) {
            tried += it.sessionId
            ok
        }

        assertEquals(listOf(1L), tried)
    }
}

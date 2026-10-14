package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the heart-rate recovery policy that keeps the BPM readout off "--": the real
 * [WorkoutHeartRateWatchdog] the service's watchdog delegates to.
 */
class WorkoutHeartRateRecoveryTest {

    private val stall = WorkoutHeartRateWatchdog.STALL_MS
    private val maxAttempts = WorkoutHeartRateWatchdog.MAX_ATTEMPTS
    private val armedAt = 500_000L

    private fun rearm(
        lastEventAtMs: Long = 0L,
        attempts: Int = 0,
        now: Long,
        isRecording: Boolean = true,
        hasPermission: Boolean = true,
        armedAtMs: Long = armedAt,
    ) = WorkoutHeartRateWatchdog.shouldRearm(isRecording, hasPermission, armedAtMs, lastEventAtMs, attempts, now)

    @Test
    fun theTenSecondPpgWarmUpIsNeverInterrupted() {
        // Xiaomi Watch 2: first reading ~10 s after registering. The old 10 s watchdog re-armed
        // exactly then, restarting the warm-up; nothing may fire anywhere near that window.
        assertFalse(rearm(now = armedAt + 10_000L))
        assertFalse(rearm(now = armedAt + 20_000L))
        assertFalse(rearm(now = armedAt + stall - 1))
    }

    @Test
    fun aCompletelySilentListenerIsReArmedOnceTheStallWindowElapses() {
        assertTrue(rearm(now = armedAt + stall))
    }

    @Test
    fun noContactOrZeroCallbacksProveTheListenerIsAlive() {
        // Callbacks keep arriving (e.g. 0 BPM, off the wrist): re-registering cannot help.
        assertFalse(rearm(lastEventAtMs = armedAt + 50_000L, now = armedAt + 60_000L))
    }

    @Test
    fun aMidWorkoutStallIsRecoveredToo() {
        // Readings flowed, then stopped for the full window.
        assertTrue(rearm(lastEventAtMs = armedAt + 120_000L, now = armedAt + 120_000L + stall))
    }

    @Test
    fun recoveryStopsAfterTheAttemptBudgetSoAWatchWithoutSignalCannotLoop() {
        assertTrue(rearm(attempts = maxAttempts - 1, now = armedAt + stall))
        assertFalse(rearm(attempts = maxAttempts, now = armedAt + stall))
    }

    @Test
    fun watchdogNeverFiresWhenRecordingIsNotActiveOrPermissionIsMissing() {
        assertFalse(rearm(isRecording = false, now = armedAt + stall))
        assertFalse(rearm(hasPermission = false, now = armedAt + stall))
        assertFalse(rearm(armedAtMs = 0L, now = armedAt + stall))
    }

    @Test
    fun outOfRangeReadingsAreDiscarded() {
        // Mirrors publishHeartRate()'s range gate: a 0 BPM report (watch not on skin) is not a BPM.
        assertNull(publishableBpm(0))
        assertNull(publishableBpm(19))
        assertNull(publishableBpm(251))
        assertNull(publishableBpm(null))
        assertEquals(72, publishableBpm(72))
        assertEquals(20, publishableBpm(20))
        assertEquals(250, publishableBpm(250))
    }

    private fun publishableBpm(value: Int?): Int? = value?.takeIf { it in 20..250 }
}

package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the heart-rate recovery policy that keeps the BPM readout off "--".
 *
 * The service-side plumbing ([WorkoutRecordingService]) is Android-bound, so these tests pin the
 * pure decision rules the service delegates to: when a starved listener may be re-armed, and when
 * the UI is allowed to advertise heart rate at all.
 */
class WorkoutHeartRateRecoveryTest {

    // Mirrors WorkoutRecordingService.HEART_RATE_WATCHDOG_MS / MAX_HEART_RATE_RECOVERY_ATTEMPTS and
    // the guards in watchdogHeartRate().
    private val watchdogMs = 10_000L
    private val maxAttempts = 3

    private fun mayRearm(
        isRecording: Boolean,
        heartRateEverReceived: Boolean,
        hasHeartRatePermission: Boolean,
        armedAtMs: Long,
        recoveryAttempts: Int,
        nowElapsedRealtimeMs: Long,
    ): Boolean = isRecording &&
        !heartRateEverReceived &&
        hasHeartRatePermission &&
        armedAtMs != 0L &&
        recoveryAttempts < maxAttempts &&
        nowElapsedRealtimeMs - armedAtMs >= watchdogMs

    private fun claimsHeartRate(
        heartRateEverReceived: Boolean,
        healthHeartRateAvailable: Boolean,
        healthServicesAvailable: Boolean,
        hrSensorPresent: Boolean,
        recoveryAttempts: Int,
    ): Boolean = heartRateEverReceived ||
        (healthHeartRateAvailable && healthServicesAvailable) ||
        (hrSensorPresent && recoveryAttempts < maxAttempts)

    @Test
    fun starvedListenerIsReArmedOnceTheWatchdogWindowElapses() {
        // Armed at t=500_000, nothing delivered yet: silent inside the window, re-armed after it.
        val armedAtMs = 500_000L
        assertFalse(mayRearm(true, false, true, armedAtMs, recoveryAttempts = 0, nowElapsedRealtimeMs = armedAtMs + 9_999L))
        assertTrue(mayRearm(true, false, true, armedAtMs, recoveryAttempts = 0, nowElapsedRealtimeMs = armedAtMs + 10_000L))
    }

    @Test
    fun listenerThatDeliveredIsNeverReArmed() {
        assertFalse(
            mayRearm(true, heartRateEverReceived = true, hasHeartRatePermission = true, armedAtMs = 500_000L, recoveryAttempts = 0, nowElapsedRealtimeMs = 560_000L),
        )
    }

    @Test
    fun recoveryStopsAfterTheAttemptBudgetSoAWatchOffTheWristCannotLoop() {
        assertTrue(mayRearm(true, false, true, armedAtMs = 500_000L, recoveryAttempts = maxAttempts - 1, nowElapsedRealtimeMs = 590_000L))
        assertFalse(mayRearm(true, false, true, armedAtMs = 500_000L, recoveryAttempts = maxAttempts, nowElapsedRealtimeMs = 590_000L))
    }

    @Test
    fun watchdogNeverFiresWhenRecordingIsNotActiveOrPermissionIsMissing() {
        // Paused/stopped workouts must not churn the sensor.
        assertFalse(mayRearm(false, false, true, armedAtMs = 500_000L, recoveryAttempts = 0, nowElapsedRealtimeMs = 560_000L))
        // Without the heart-rate permission there is nothing to re-arm.
        assertFalse(mayRearm(true, false, hasHeartRatePermission = false, armedAtMs = 500_000L, recoveryAttempts = 0, nowElapsedRealtimeMs = 560_000L))
        // Never armed at all (armedAtMs == 0) is not a starved listener.
        assertFalse(mayRearm(true, false, true, armedAtMs = 0L, recoveryAttempts = 0, nowElapsedRealtimeMs = 560_000L))
    }

    @Test
    fun availabilityStopsClaimingHeartRateOnceRecoveryIsExhausted() {
        // While recovery is still possible the UI may say "HR" — the listener is warming up.
        assertTrue(claimsHeartRate(false, false, false, hrSensorPresent = true, recoveryAttempts = 0))
        // After the budget is spent, claiming availability would be a lie above a permanent "--".
        assertFalse(claimsHeartRate(false, false, false, hrSensorPresent = true, recoveryAttempts = maxAttempts))
        // A delivered BPM, or a healthy Health Services source, keeps it honest.
        assertTrue(claimsHeartRate(true, false, false, hrSensorPresent = false, recoveryAttempts = maxAttempts))
        assertTrue(claimsHeartRate(false, true, true, hrSensorPresent = false, recoveryAttempts = maxAttempts))
        // Health Services "available" without its service being up does not count.
        assertFalse(claimsHeartRate(false, true, healthServicesAvailable = false, hrSensorPresent = false, recoveryAttempts = 0))
    }

    @Test
    fun outOfRangeReadingsAreDiscardedSoTheyCannotMarkTheListenerAsDelivered() {
        // Mirrors publishHeartRate()'s range gate: a 0 BPM report (watch not on skin) must not
        // satisfy the watchdog, otherwise recovery would be skipped and the readout stuck.
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

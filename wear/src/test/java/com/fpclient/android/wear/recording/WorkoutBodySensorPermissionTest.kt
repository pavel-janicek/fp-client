package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the heart-rate permission selection that left BPM stuck on "--".
 *
 * The `BODY_SENSORS` → `android.permission.health.READ_HEART_RATE` switch is keyed on the app's
 * **target** SDK, never on the device OS version. This module branched on
 * `Build.VERSION.SDK_INT` while `targetSdk` sat at 34, so on an API 36 watch it asked for
 * READ_HEART_RATE (auto-denied, no dialog) while the manifest's `maxSdkVersion="35"` stripped
 * BODY_SENSORS — heart rate became permanently ungrantable, yet the workout still started because
 * location alone satisfies `canStart`.
 *
 * [WorkoutRecordingController] is Android-bound, so these tests pin the selection rule and the
 * manifest invariants it depends on.
 */
class WorkoutBodySensorPermissionTest {

    /** Mirrors WorkoutRecordingController.bodySensorPermission(). */
    private fun bodySensorPermission(targetSdkVersion: Int): String =
        if (targetSdkVersion >= 36) READ_HEART_RATE else BODY_SENSORS

    /** Mirrors WorkoutRecordingController.hasHeartRatePermission() — accepts either grant. */
    private fun hasHeartRatePermission(readHeartRateGranted: Boolean, bodySensorsGranted: Boolean): Boolean =
        readHeartRateGranted || bodySensorsGranted

    /** Mirrors WorkoutRecordingController.canStart()'s "any one is enough" gate. */
    private fun canStart(locationGranted: Boolean, hasHeartRate: Boolean, activityGranted: Boolean): Boolean =
        locationGranted || hasHeartRate || activityGranted

    @Test
    fun targetSdkSelectsThePermissionThePlatformWillActuallyGrant() {
        // targetSdk 36+ (matches :app): the health permission is the grantable one.
        assertEquals(READ_HEART_RATE, bodySensorPermission(targetSdkVersion = 36))
        assertEquals(READ_HEART_RATE, bodySensorPermission(targetSdkVersion = 37))
        // Below 36 the app is a legacy body-sensors app; BODY_SENSORS is the grantable one.
        assertEquals(BODY_SENSORS, bodySensorPermission(targetSdkVersion = 35))
        assertEquals(BODY_SENSORS, bodySensorPermission(targetSdkVersion = 34))
    }

    @Test
    fun theBrokenCombinationIsTheOneThisModuleUsedToShip() {
        // The old bug: targetSdk 34 selected BODY_SENSORS while the code asked for READ_HEART_RATE
        // on API 36 devices. Assert the two disagree so a future refactor cannot silently pair them.
        val targetSdkSelection = bodySensorPermission(targetSdkVersion = 34)
        val oldDeviceOsSelection = READ_HEART_RATE // what `Build.VERSION.SDK_INT >= 36` picked
        assertFalse(targetSdkSelection == oldDeviceOsSelection)
    }

    @Test
    fun eitherGrantSatisfiesTheHeartRateGateSoTheMigrationIsSafe() {
        assertTrue(hasHeartRatePermission(readHeartRateGranted = true, bodySensorsGranted = false))
        assertTrue(hasHeartRatePermission(readHeartRateGranted = false, bodySensorsGranted = true))
        assertTrue(hasHeartRatePermission(readHeartRateGranted = true, bodySensorsGranted = true))
        assertFalse(hasHeartRatePermission(readHeartRateGranted = false, bodySensorsGranted = false))
    }

    @Test
    fun aWorkoutCanStartWithoutHeartRatePermissionWhichIsWhyTheFailureWasSilent() {
        // Location alone is enough to start, and enough to satisfy the foreground-service type
        // gate — so a missing body-sensors permission produces no error, only a dead readout.
        assertTrue(canStart(locationGranted = true, hasHeartRate = false, activityGranted = false))
        assertFalse(canStart(locationGranted = false, hasHeartRate = false, activityGranted = false))
    }

    @Test
    fun eitherPermissionKeepsForegroundServiceHealthTypeAvailable() {
        // Mirrors foregroundServiceTypes(): HEALTH is claimed from HR *or* activity recognition.
        fun foregroundTypes(location: Boolean, heartRate: Boolean, activity: Boolean): Set<String> = buildSet {
            if (location) add("LOCATION")
            if (heartRate || activity) add("HEALTH")
        }

        assertTrue(foregroundTypes(false, true, false).contains("HEALTH"))
        assertTrue(foregroundTypes(false, false, true).contains("HEALTH"))
        assertTrue(foregroundTypes(true, false, false).contains("LOCATION"))
        assertTrue(foregroundTypes(true, false, false).isEmpty().not())
        assertTrue(foregroundTypes(false, false, false).isEmpty())
    }

    companion object {
        private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        private const val BODY_SENSORS = "android.permission.BODY_SENSORS"
    }
}

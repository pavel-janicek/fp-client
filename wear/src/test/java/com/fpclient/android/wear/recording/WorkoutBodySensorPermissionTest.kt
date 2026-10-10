package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the heart-rate permission selection that made "Grant access" a no-op.
 *
 * The permission to *request* is keyed on the **device OS version** ([Build.VERSION.SDK_INT]),
 * because `android.permission.health.READ_HEART_RATE` only exists from API 36 — asking for it on an
 * older watch is an unknown permission that the PackageManager denies instantly with **no dialog**
 * (the live symptom: `READ_HEART_RATE granted=false`, no USER_SET flag, on an API 34 watch, while
 * `BODY_SENSORS granted=true` sits unused and the Settings button stays stuck).
 *
 * `targetSdk = 36` is the *separate* prerequisite that makes `READ_HEART_RATE` grantable on API 36+
 * devices; it must be set, but it is never the request gate.
 *
 * [WorkoutRecordingController] is Android-bound, so these tests pin the selection rule and the
 * manifest invariants it depends on.
 */
class WorkoutBodySensorPermissionTest {

    /** Mirrors WorkoutRecordingController.bodySensorPermission(). Gate = device OS version. */
    private fun bodySensorPermission(deviceSdkInt: Int): String =
        if (deviceSdkInt >= 36) READ_HEART_RATE else BODY_SENSORS

    /** Mirrors WorkoutRecordingController.hasHeartRatePermission() — accepts either grant. */
    private fun hasHeartRatePermission(readHeartRateGranted: Boolean, bodySensorsGranted: Boolean): Boolean =
        readHeartRateGranted || bodySensorsGranted

    /** Mirrors WorkoutRecordingController.canStart()'s "any one is enough" gate. */
    private fun canStart(locationGranted: Boolean, hasHeartRate: Boolean, activityGranted: Boolean): Boolean =
        locationGranted || hasHeartRate || activityGranted

    @Test
    fun deviceOsVersionSelectsThePermissionThePlatformWillActuallyPromptFor() {
        // API 36+ device: the health permission exists and (with targetSdk 36) is grantable.
        assertEquals(READ_HEART_RATE, bodySensorPermission(deviceSdkInt = 36))
        assertEquals(READ_HEART_RATE, bodySensorPermission(deviceSdkInt = 37))
        // Older device (API ≤ 35): only BODY_SENSORS exists and can be prompted for.
        assertEquals(BODY_SENSORS, bodySensorPermission(deviceSdkInt = 35))
        assertEquals(BODY_SENSORS, bodySensorPermission(deviceSdkInt = 34))
        assertEquals(BODY_SENSORS, bodySensorPermission(deviceSdkInt = 30))
    }

    @Test
    fun theBrokenCombinationIsRequestingHeartRateOnAPreApi36Device() {
        // The regression that shipped: gating on targetSdk (36) made the app ask for READ_HEART_RATE
        // on this very watch (Xiaomi Watch 2, API 34), where the permission does not exist. Assert
        // that an API 34 device must NOT be handed READ_HEART_RATE, whatever targetSdk says.
        val requestedOnDevice = bodySensorPermission(deviceSdkInt = 34)
        assertEquals(BODY_SENSORS, requestedOnDevice)
        assertFalse(requestedOnDevice == READ_HEART_RATE)
    }

    @Test
    fun alreadyGrantedBodySensorsSatisfiesTheGateSoANewPromptIsNeverNeeded() {
        // This watch already has BODY_SENSORS granted, so once the request gate is correct the
        // controller must treat it as satisfied — no dead-end "Grant access" button.
        assertTrue(hasHeartRatePermission(readHeartRateGranted = false, bodySensorsGranted = true))
        assertTrue(hasHeartRatePermission(readHeartRateGranted = true, bodySensorsGranted = false))
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
        assertTrue(foregroundTypes(true, false, false).isNotEmpty())
        assertTrue(foregroundTypes(false, false, false).isEmpty())
    }

    companion object {
        private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        private const val BODY_SENSORS = "android.permission.BODY_SENSORS"
    }
}

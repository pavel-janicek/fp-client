package com.fpclient.android.wear.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

object WorkoutRecordingController {
    internal const val ACTION_START = "com.fpclient.android.wear.recording.START"
    internal const val ACTION_PAUSE = "com.fpclient.android.wear.recording.PAUSE"
    internal const val ACTION_RESUME = "com.fpclient.android.wear.recording.RESUME"
    internal const val ACTION_STOP = "com.fpclient.android.wear.recording.STOP"
    internal const val ACTION_LOW_POWER = "com.fpclient.android.wear.recording.LOW_POWER"
    internal const val EXTRA_ACTIVITY_TYPE = "workout_activity_type"
    internal const val EXTRA_LOW_POWER = "workout_low_power"

    /**
     * The body-sensors permission this build must ask for, keyed on the **device OS version** —
     * that is what decides which permission the framework will actually prompt for:
     *
     *  - `android.permission.health.READ_HEART_RATE` only exists from API 36. Requesting it on an
     *    older watch is an unknown permission: the PackageManager denies it instantly with **no
     *    dialog**. That is the exact "Grant access does nothing" symptom — the button launches a
     *    request the OS ignores, while an already-granted `BODY_SENSORS` sits unused.
     *  - `BODY_SENSORS` is declared with `maxSdkVersion="35"`, so on API 36+ it is stripped from the
     *    merged manifest and `READ_HEART_RATE` is the only grantable option there.
     *
     * `targetSdk = 36` (see wear/build.gradle.kts) is the separate prerequisite that makes
     * `READ_HEART_RATE` *grantable* on API 36+ devices; it must stay, but it is not the request gate.
     */
    fun bodySensorPermission(context: Context): String = Manifest.permission.BODY_SENSORS

    /**
     * True when the body-sensors permission is granted.
     */
    fun hasHeartRatePermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.BODY_SENSORS)

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun runtimePermissions(context: Context): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        add(bodySensorPermission(context))
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun missingPermissions(context: Context): List<String> = runtimePermissions(context).filter {
        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun canStart(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val healthPermission = hasHeartRatePermission(context)
        val activityGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return locationGranted || healthPermission || activityGranted
    }

    fun start(
        context: Context,
        activityType: WorkoutActivityType = WorkoutActivityType.RUN,
    ) = send(
        context,
        ACTION_START,
        foreground = true,
        activityType = activityType.name,
    )
    fun pause(context: Context) = send(context, ACTION_PAUSE)
    fun resume(context: Context) = send(context, ACTION_RESUME)
    fun stop(context: Context) = send(context, ACTION_STOP)

    /**
     * Tells the service whether anyone is watching the live workout. Low power (screen dimmed,
     * workout screen left, app in the background) throttles the live readout and lets the PPG
     * and GPS batch samples in hardware.
     */
    fun setLowPowerMode(context: Context, lowPower: Boolean) {
        context.startService(
            Intent(context, WorkoutRecordingService::class.java)
                .setAction(ACTION_LOW_POWER)
                .putExtra(EXTRA_LOW_POWER, lowPower),
        )
    }

    private fun send(
        context: Context,
        action: String,
        foreground: Boolean = false,
        activityType: String? = null,
    ) {
        val intent = Intent(context, WorkoutRecordingService::class.java)
            .setAction(action)
            .putExtra(EXTRA_ACTIVITY_TYPE, activityType)
        if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }
}
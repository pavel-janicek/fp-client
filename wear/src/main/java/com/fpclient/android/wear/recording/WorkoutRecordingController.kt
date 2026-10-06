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

    fun runtimePermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 36) {
            add(READ_HEART_RATE_PERMISSION)
        } else {
            add(Manifest.permission.BODY_SENSORS)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun missingPermissions(context: Context): List<String> = runtimePermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun canStart(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val healthPermission = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= 36) READ_HEART_RATE_PERMISSION else Manifest.permission.BODY_SENSORS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val activityGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return locationGranted || healthPermission || activityGranted
    }

    fun start(context: Context) = send(context, ACTION_START, foreground = true)
    fun pause(context: Context) = send(context, ACTION_PAUSE)
    fun resume(context: Context) = send(context, ACTION_RESUME)
    fun stop(context: Context) = send(context, ACTION_STOP)

    private fun send(context: Context, action: String, foreground: Boolean = false) {
        val intent = Intent(context, WorkoutRecordingService::class.java).setAction(action)
        if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }

    internal const val READ_HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"
}
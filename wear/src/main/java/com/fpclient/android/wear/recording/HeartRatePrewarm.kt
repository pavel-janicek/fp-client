package com.fpclient.android.wear.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Switches the PPG on while the app is open, so it is already past its warm-up when a workout
 * starts. On the Xiaomi Watch 2 the first reading takes 9–15 s after the sensor is enabled; arming
 * it only from [WorkoutRecordingService] put that whole wait after the Start tap.
 *
 * Holds the same wake-up sensor the service uses and lingers for [LINGER_MS] after the app leaves
 * the foreground, so the service registers while the PPG is still on and it never restarts the
 * warm-up. The readings themselves are ignored here; the service records its own.
 */
class HeartRatePrewarm(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable(::stopNow)
    private var active = false

    fun start() {
        handler.removeCallbacks(stopRunnable)
        if (active || !WorkoutRecordingController.hasHeartRatePermission(appContext)) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            ?: return
        active = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopLater() {
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, LINGER_MS)
    }

    private fun stopNow() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
    }

    override fun onSensorChanged(event: SensorEvent) = Unit

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val LINGER_MS = 10_000L
    }
}

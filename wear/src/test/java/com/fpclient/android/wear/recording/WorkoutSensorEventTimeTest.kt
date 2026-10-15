package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSensorEventTimeTest {
    private val nowEpochMs = 1_760_000_000_000L
    private val nowElapsedNanos = 50_000_000_000_000L

    @Test
    fun `a batched sample keeps its measurement time, not its delivery time`() {
        val measured12sAgo = nowElapsedNanos - 12_000_000_000L
        assertEquals(nowEpochMs - 12_000L, WorkoutMath.sensorEventEpochMs(measured12sAgo, nowEpochMs, nowElapsedNanos))
    }

    @Test
    fun `a live sample is stamped now`() {
        assertEquals(nowEpochMs, WorkoutMath.sensorEventEpochMs(nowElapsedNanos, nowEpochMs, nowElapsedNanos))
    }

    @Test
    fun `a sample stamped after now is clamped to now`() {
        assertEquals(nowEpochMs, WorkoutMath.sensorEventEpochMs(nowElapsedNanos + 5_000_000L, nowEpochMs, nowElapsedNanos))
    }
}

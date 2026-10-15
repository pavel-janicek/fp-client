package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The workout screen never shows a heart rate that stopped updating as if it were live. */
class WorkoutHeartRateStalenessTest {

    private val measuredAt = 1_792_043_721_000L

    private fun snapshot(bpm: Int?, at: Long?) =
        WorkoutRecordingSnapshot(heartRateBpm = bpm, heartRateAtEpochMs = at)

    @Test
    fun aFreshReadingIsShown() {
        assertEquals(91, snapshot(91, measuredAt).liveHeartRateBpm(measuredAt + 1_000L))
        assertEquals(91, snapshot(91, measuredAt).liveHeartRateBpm(measuredAt + HEART_RATE_STALE_MS))
    }

    @Test
    fun aReadingThatStoppedUpdatingIsBlanked() {
        // makni.cz 82bb40f7: "91" stayed on screen for ~20 minutes.
        assertNull(snapshot(91, measuredAt).liveHeartRateBpm(measuredAt + HEART_RATE_STALE_MS + 1))
        assertNull(snapshot(91, measuredAt).liveHeartRateBpm(measuredAt + 20 * 60_000L))
    }

    @Test
    fun aValueWithoutMeasurementTimeIsNotShownAsLive() {
        // Replayed from the track file after a service restart.
        assertNull(snapshot(91, null).liveHeartRateBpm(measuredAt))
    }

    @Test
    fun noReadingStaysBlank() {
        assertNull(snapshot(null, measuredAt).liveHeartRateBpm(measuredAt))
    }
}

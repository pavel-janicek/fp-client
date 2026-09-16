package com.fpclient.android.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the shared bus between the foreground service and the Compose UI,
 * in particular the live track points that feed the Record screen's mini-map (8c).
 */
class TrackRecordingBusTest {

    private fun point(lat: Double, lon: Double, time: Long) =
        TrackPoint(lat = lat, lon = lon, ele = 100.0, time = time, accuracy = 5.0)

    @Test
    fun `publishPoint accumulates accepted fixes in order`() {
        TrackRecordingBus.publish(null) // clean slate
        val p1 = point(10.0, 20.0, 1L)
        val p2 = point(10.001, 20.001, 2L)
        TrackRecordingBus.publishPoint(p1)
        TrackRecordingBus.publishPoint(p2)
        assertEquals(listOf(p1, p2), TrackRecordingBus.points.value)
        // Publishing points never touches the session state.
        assertNull(TrackRecordingBus.session.value)
    }

    @Test
    fun `publishPoints replaces the live track wholesale`() {
        TrackRecordingBus.publish(null)
        TrackRecordingBus.publishPoint(point(10.0, 20.0, 1L))
        val replayed = listOf(point(11.0, 21.0, 2L), point(12.0, 22.0, 3L))
        TrackRecordingBus.publishPoints(replayed)
        assertEquals(replayed, TrackRecordingBus.points.value)
    }

    @Test
    fun `ending the session clears points and stats with it`() {
        TrackRecordingBus.publish(
            TrackSessionSnapshot(RecordingState.RECORDING, 1L, 0L, 1L),
        )
        TrackRecordingBus.publishPoint(point(10.0, 20.0, 1L))
        TrackRecordingBus.publishPoint(point(10.001, 20.001, 2L))

        TrackRecordingBus.publish(null)

        assertNull(TrackRecordingBus.session.value)
        assertEquals(TrackStats(), TrackRecordingBus.stats.value)
        assertTrue(TrackRecordingBus.points.value.isEmpty())
    }
}
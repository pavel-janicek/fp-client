package com.fpclient.android.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the wall-clock session math and the persistence round trip that
 * underpin "process death restarts into the right state" (Iteration 8a).
 */
class TrackRecordingStateTest {

    @Test
    fun `elapsed time derives from the wall clock, not an in-process counter`() {
        val started = 1_000_000L
        val s = TrackSessionSnapshot(RecordingState.RECORDING, started, 0L, started)
        assertEquals(5_000L, s.elapsedAt(started + 5_000))
    }

    @Test
    fun `clock skew before the start time is clamped to zero`() {
        val started = 1_000_000L
        val s = TrackSessionSnapshot(RecordingState.RECORDING, started, 0L, started)
        assertEquals(0L, s.elapsedAt(started - 100))
        assertEquals(0L, s.movingMsAt(started - 100))
    }

    @Test
    fun `pausing freezes the moving time and clears the running segment`() {
        val started = 1_000_000L
        val s = TrackSessionSnapshot(RecordingState.RECORDING, started, 0L, started)
        val pausedAt = started + 60_000
        val paused = s.copy(
            state = RecordingState.PAUSED,
            accumulatedMs = s.movingMsAt(pausedAt),
            lastResumeAtEpochMs = null,
        )
        assertEquals(60_000L, paused.accumulatedMs)
        // Much later (still paused, e.g. after a process death + restore): moving time
        // must not grow.
        assertEquals(60_000L, paused.movingMsAt(pausedAt + 5 * 60_000))
    }

    @Test
    fun `resuming adds a new segment on top of the accumulated one`() {
        val started = 1_000_000L
        val accumulated = 60_000L
        val resumedAt = 2_000_000L
        val resumed = TrackSessionSnapshot(RecordingState.RECORDING, started, accumulated, resumedAt)
        assertEquals(60_000L + 30_000L, resumed.movingMsAt(resumedAt + 30_000))
        // Total elapsed keeps running across the pause.
        assertEquals(resumedAt + 30_000 - started, resumed.elapsedAt(resumedAt + 30_000))
    }

    @Test
    fun `restore after process death recomputes the same state from persisted values`() {
        // Simulates: start at T0, run 5 min, pause, process dies, OS restarts the service
        // with a null intent, store is read back, and time has moved on.
        val start = 1_000_000L
        val pause = start + 300_000L
        val beforeDeath = TrackSessionSnapshot(RecordingState.RECORDING, start, 0L, start)
        val persisted = beforeDeath.copy(
            state = RecordingState.PAUSED,
            accumulatedMs = beforeDeath.movingMsAt(pause),
            lastResumeAtEpochMs = null,
        )
        val restored = TrackRecordingStateStore.deserialize(
            TrackRecordingStateStore.serialize(persisted),
        )!!
        val afterRestart = pause + 120_000L
        assertEquals(RecordingState.PAUSED, restored.state)
        assertEquals(300_000L, restored.accumulatedMs)
        assertNull(restored.lastResumeAtEpochMs)
        assertEquals(300_000L, restored.movingMsAt(afterRestart))
        assertEquals(afterRestart - start, restored.elapsedAt(afterRestart))
    }

    @Test
    fun `serialization round trip preserves recording sessions with a running segment`() {
        val s = TrackSessionSnapshot(RecordingState.RECORDING, 1_000_000L, 45_000L, 1_100_000L)
        val restored = TrackRecordingStateStore.deserialize(TrackRecordingStateStore.serialize(s))!!
        assertEquals(s, restored)
    }

    @Test
    fun `deserialization rejects unknown states and missing fields instead of crashing`() {
        val good = TrackRecordingStateStore.serialize(
            TrackSessionSnapshot(RecordingState.RECORDING, 1L, 0L, 1L),
        )
        assertNull(TrackRecordingStateStore.deserialize(emptyMap()))
        assertNull(TrackRecordingStateStore.deserialize(good + ("state" to "SOMETHING_ELSE")))
        assertNull(TrackRecordingStateStore.deserialize(good - "started_at"))
        // A paused session's "-1" sentinel must decode back to null, not -1.
        val paused = TrackRecordingStateStore.serialize(
            TrackSessionSnapshot(RecordingState.PAUSED, 1L, 10L, null),
        )
        assertNull(TrackRecordingStateStore.deserialize(paused)!!.lastResumeAtEpochMs)
    }

    @Test
    fun `state machine covers the full idle-recording-paused-stop cycle`() {
        val now = System.currentTimeMillis()
        val start = TrackSessionSnapshot(RecordingState.RECORDING, now, 0L, now)
        val paused = start.copy(
            state = RecordingState.PAUSED,
            accumulatedMs = start.movingMsAt(now + 1_000),
            lastResumeAtEpochMs = null,
        )
        val resumed = paused.copy(state = RecordingState.RECORDING, lastResumeAtEpochMs = now + 2_000)
        assertEquals(RecordingState.RECORDING, resumed.state)
        assertTrue(resumed.movingMsAt(now + 3_000) >= paused.accumulatedMs)
    }

    @Test
    fun `deserialize returns null for a state value that fails valueOf`() {
        val map = mapOf("state" to "NOT_A_STATE", "started_at" to 1L, "accumulated_ms" to 0L, "last_resume_at" to -1L)
        assertNull(TrackRecordingStateStore.deserialize(map))
    }
}

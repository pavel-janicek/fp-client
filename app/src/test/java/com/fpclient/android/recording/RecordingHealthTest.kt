package com.fpclient.android.recording

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class RecordingHealthTest {
    private val paused = TrackSessionSnapshot(RecordingState.PAUSED, 1_000L, 5_000L, null, "RUN")
    private val running = paused.copy(state = RecordingState.RECORDING, lastResumeAtEpochMs = 10_000L)

    @Test
    fun `storage reserve includes boundary`() {
        assertFalse(RecordingHealth.hasStorage(-1))
        assertFalse(RecordingHealth.hasStorage(RecordingHealth.MIN_FREE_BYTES - 1))
        assertTrue(RecordingHealth.hasStorage(RecordingHealth.MIN_FREE_BYTES))
    }

    @Test
    fun `low storage refuses resume without writing marker`() {
        val result = RecordingHealth.resumeIfWritable(paused, 10_000L, RecordingHealth.MIN_FREE_BYTES - 1) {
            fail("Must not write a segment below the storage reserve")
        }
        assertNull(result)
        assertEquals(RecordingState.PAUSED, paused.state)
        assertEquals(5_000L, paused.movingMsAt(20_000L))
    }

    @Test
    fun `failed marker refuses resume`() {
        for (failure in listOf(IOException("Disk full"), SecurityException("Access denied"))) {
            assertNull(RecordingHealth.resumeIfWritable(paused, 10_000L, RecordingHealth.MIN_FREE_BYTES) {
                throw failure
            })
        }
    }

    @Test
    fun `successful marker precedes resume and preserves accumulated time`() {
        var writes = 0
        val result = RecordingHealth.resumeIfWritable(paused, 10_000L, RecordingHealth.MIN_FREE_BYTES) {
            writes++
            assertEquals(RecordingState.PAUSED, paused.state)
        }
        assertEquals(1, writes)
        assertEquals(running, result)
        assertEquals(6_000L, result!!.movingMsAt(11_000L))
    }

    @Test
    fun `already running cannot open a second segment`() {
        assertNull(RecordingHealth.resumeIfWritable(running, 11_000L, Long.MAX_VALUE) {
            fail("Already running")
        })
    }

    @Test
    fun `no fix warning starts exactly after two minutes`() {
        assertFalse(RecordingHealth.missingFix(running, null, 129_999L))
        assertTrue(RecordingHealth.missingFix(running, null, 130_000L))
        assertFalse(RecordingHealth.missingFix(running, 129_000L, 130_000L))
        assertTrue(RecordingHealth.missingFix(running, 129_000L, 249_000L))
    }

    @Test
    fun `idle and paused never warn and resume resets grace period`() {
        assertFalse(RecordingHealth.missingFix(null, null, Long.MAX_VALUE))
        assertFalse(RecordingHealth.missingFix(paused, 2_000L, 500_000L))
        assertFalse(RecordingHealth.missingFix(running, 2_000L, 129_999L))
        assertFalse(RecordingHealth.missingFix(running, null, 0L))
    }
}

package com.fpclient.android.wear.ui

import com.fpclient.android.wear.recording.WorkoutStatus
import com.fpclient.android.wear.ui.shouldAutoReturnOnStop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearAppNavGraphShouldAutoReturnTest {

    @Test
    fun `auto-return triggers after a fresh stop from recording`() {
        assertTrue(shouldAutoReturnOnStop(WorkoutStatus.RECORDING, true))
    }

    @Test
    fun `auto-return triggers after a fresh stop from paused`() {
        assertTrue(shouldAutoReturnOnStop(WorkoutStatus.PAUSED, true))
    }

    @Test
    fun `does not auto-return when still recording or paused`() {
        assertFalse(shouldAutoReturnOnStop(WorkoutStatus.RECORDING, false))
        assertFalse(shouldAutoReturnOnStop(WorkoutStatus.PAUSED, false))
    }

    @Test
    fun `does not auto-return for a persisted stopped snapshot after process death`() {
        // A STOPPED snapshot restored from storage has no prior process state, so it must never
        // bounce back to Home on re-entry.
        assertFalse(shouldAutoReturnOnStop(null, true))
        assertFalse(shouldAutoReturnOnStop(WorkoutStatus.STOPPED, true))
    }
}
package com.fpclient.android.wear.ui

import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The dimmed workout screen shows the time plus a one-line reminder of what is recording. */
class AmbientWorkoutLabelTest {

    @Test
    fun recordingShowsThePlayMarkerAndTheActivity() {
        assertEquals("> WALK", ambientWorkoutLabel(WorkoutStatus.RECORDING, WorkoutActivityType.WALK))
        assertEquals("> RUN", ambientWorkoutLabel(WorkoutStatus.RECORDING, WorkoutActivityType.RUN))
    }

    @Test
    fun pausedSaysSoInsteadOfThePlayMarker() {
        assertEquals("WALK · PAUSED", ambientWorkoutLabel(WorkoutStatus.PAUSED, WorkoutActivityType.WALK))
    }

    @Test
    fun noWorkoutMeansJustTheClock() {
        assertNull(ambientWorkoutLabel(WorkoutStatus.IDLE, WorkoutActivityType.WALK))
        assertNull(ambientWorkoutLabel(WorkoutStatus.STOPPED, WorkoutActivityType.WALK))
    }
}

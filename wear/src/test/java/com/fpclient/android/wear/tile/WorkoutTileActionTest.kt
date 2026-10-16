package com.fpclient.android.wear.tile

import com.fpclient.android.wear.recording.WorkoutActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTileActionTest {
    @Test
    fun `every action survives the intent extra round trip`() {
        val actions = WorkoutActivityType.entries.map(WorkoutTileAction::Start) +
            WorkoutTileAction.ChooseActivity + WorkoutTileAction.OpenWorkout
        actions.forEach { assertEquals(it, WorkoutTileAction.decode(it.encode())) }
    }

    @Test
    fun `unknown or missing values are ignored rather than starting a default workout`() {
        assertNull(WorkoutTileAction.decode(null))
        assertNull(WorkoutTileAction.decode("start:SWIM"))
        assertNull(WorkoutTileAction.decode("garbage"))
    }
}

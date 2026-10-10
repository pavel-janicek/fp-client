package com.fpclient.android.wear.recording

import androidx.health.services.client.data.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers [pickHeartRateCapableExerciseType] — the policy that makes heart rate stream for every
 * workout type, not just Run.
 *
 * The symptom this fixes: Run showed BPM but Walk stuck on "--". The cause was that a Health Services
 * exercise only drives the platform PPG, and the code only started one for types the watch reported
 * as HEART_RATE_BPM-capable — otherwise it dropped to the bare platform sensor, which the system
 * starves in the background. This policy always picks an HR-capable exercise type to keep the sensor
 * alive, while the saved activity stays the user's real selection.
 */
class WorkoutHeartRateExerciseTypeTest {

    private val allTypes = setOf(
        ExerciseType.RUNNING,
        ExerciseType.WALKING,
        ExerciseType.HIKING,
        ExerciseType.BIKING,
        ExerciseType.WORKOUT,
    )

    private fun pick(
        hrCapable: Set<ExerciseType>,
        supported: Set<ExerciseType> = allTypes,
        preferred: ExerciseType,
    ): ExerciseType? = pickHeartRateCapableExerciseType(
        supportedTypes = supported,
        isHeartRateCapable = { it in hrCapable },
        preferred = preferred,
    )

    @Test
    fun keepsTheSelectedTypeWhenItCanStreamHeartRate() {
        // The healthy case: every type reports HR, so the user's choice is preserved verbatim.
        assertEquals(ExerciseType.WALKING, pick(hrCapable = allTypes, preferred = ExerciseType.WALKING))
        assertEquals(ExerciseType.BIKING, pick(hrCapable = allTypes, preferred = ExerciseType.BIKING))
        assertEquals(ExerciseType.HIKING, pick(hrCapable = allTypes, preferred = ExerciseType.HIKING))
    }

    @Test
    fun walkFallsBackToAnHrCapableTypeSoBpmStillStreams() {
        // The exact bug: the watch does NOT report HEART_RATE_BPM for WALKING, but RUNNING does.
        // Old behaviour dropped to the starved platform sensor ("--"); now we drive RUNNING instead.
        assertEquals(
            ExerciseType.RUNNING,
            pick(hrCapable = setOf(ExerciseType.RUNNING), preferred = ExerciseType.WALKING),
        )
    }

    @Test
    fun fallbackPrefersRunningThenTheClosestSupportedType() {
        // No HR on WALKING or RUNNING, but HIKING has it → the closest sensible fallback wins over
        // jumping straight to the generic WORKOUT.
        assertEquals(
            ExerciseType.HIKING,
            pick(hrCapable = setOf(ExerciseType.HIKING), preferred = ExerciseType.WALKING),
        )
        // Only the generic type can stream HR → fall back to it rather than giving up.
        assertEquals(
            ExerciseType.WORKOUT,
            pick(hrCapable = setOf(ExerciseType.WORKOUT), preferred = ExerciseType.BIKING),
        )
    }

    @Test
    fun returnsNullOnlyWhenNoTypeCanStreamHeartRate() {
        // Watch exposes HR for nothing: signal the caller to fall back to the platform sensor.
        assertNull(pick(hrCapable = emptySet(), preferred = ExerciseType.WALKING))
    }

    @Test
    fun unsupportedTypesAreNeverSelectedEvenIfListedAsHrCapable() {
        // A type must be BOTH supported AND HR-capable. An unsupported type must not be chosen, and
        // the last-resort scan must not resurrect it.
        val supported = setOf(ExerciseType.WALKING, ExerciseType.RUNNING)
        val hrCapable = setOf(ExerciseType.WALKING, ExerciseType.RUNNING)
        // Preferred BIKING is unsupported → ignored; RUNNING is the supported HR-capable fallback.
        assertEquals(
            ExerciseType.RUNNING,
            pick(hrCapable = hrCapable, supported = supported, preferred = ExerciseType.BIKING),
        )
    }
}

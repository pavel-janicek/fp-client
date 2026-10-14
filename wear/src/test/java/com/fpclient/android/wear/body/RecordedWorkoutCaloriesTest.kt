package com.fpclient.android.wear.body

import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutFitWriter
import com.fpclient.android.wear.recording.WorkoutSessionTransitions
import com.fpclient.android.wear.recording.WorkoutTrackEvent
import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import java.io.FileInputStream
import java.nio.file.Files
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end: a FIT exactly as the watch records it ([WorkoutFitWriter]) must come out of
 * [CalorieFitAmender] with a SESSION `totalCalories` the server's `FitParser` can read.
 */
class RecordedWorkoutCaloriesTest {

    @Test
    fun recordedWorkoutFitIsAmendedWithDecodableCalories() {
        val directory = Files.createTempDirectory("recorded-calories-test").toFile()
        val start = 1_700_000_000_000L
        val session = WorkoutSessionTransitions.start(start, WorkoutActivityType.WALK)
        val events = (0 until 600).map { i ->
            WorkoutTrackEvent(start + i * 1_000L, 50.0 + i * 0.00001, 14.0, heartRateBpm = 110, steps = i)
        }
        val fit = WorkoutFitWriter.write(directory, session, events, start + 600_000L)
        val amended = java.io.File(directory, "amended-${fit.name}")

        val ok = CalorieFitAmender.amendFitFileWithCalories(
            fit,
            amended,
            BodyProfile(optIn = true, sex = Sex.MALE, heightCm = 180.0, weightKg = 80.0, ageYears = 40),
        )
        assertTrue(ok)

        var decoded: SessionMesg? = null
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(SessionMesgListener { decoded = it })
        FileInputStream(amended).use { decode.read(it, broadcaster) }

        assertNotNull(decoded?.totalCalories)
        assertTrue(decoded!!.totalCalories!! > 0)
        directory.deleteRecursively()
    }
}

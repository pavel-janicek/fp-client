package com.fpclient.android.wear.recording

import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.Sport
import java.io.FileInputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutFitWriterTest {

    @Test
    fun writeEncodesEventsIntoValidFitActivity() {
        val directory = Files.createTempDirectory("fit-writer-test").toFile()
        val session = WorkoutSessionTransitions.start(1_000L, WorkoutActivityType.RUN)
        val events = listOf(
            WorkoutTrackEvent(1_000L, 50.0, 14.0, altitudeMeters = 220.0, accuracyMeters = 4.0, heartRateBpm = 100, steps = 0),
            WorkoutTrackEvent(2_000L, heartRateBpm = 120, steps = 2),
            WorkoutTrackEvent(3_000L, boundary = "PAUSE"),
            WorkoutTrackEvent(4_000L, 50.1, 14.1, altitudeMeters = 230.0, accuracyMeters = 3.0, heartRateBpm = 140, steps = 3),
            WorkoutTrackEvent(5_000L, boundary = "STOP"),
        )

        val fitFile = WorkoutFitWriter.write(directory, session, events, 5_000L)
        assertTrue(fitFile.exists())
        assertTrue(fitFile.length() > 0)

        val records = mutableListOf<RecordMesg>()
        var decodedSession: SessionMesg? = null

        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(RecordMesgListener { mesg -> records.add(mesg) })
        broadcaster.addListener(SessionMesgListener { mesg -> decodedSession = mesg })

        FileInputStream(fitFile).use { input ->
            decode.read(input, broadcaster)
        }

        assertEquals(3, records.size)
        assertEquals(100.toShort(), records[0].heartRate)
        assertEquals(120.toShort(), records[1].heartRate)
        assertEquals(140.toShort(), records[2].heartRate)

        // Lat 50.0 degrees -> semicircles: (50.0 / 180.0 * 2^31) = 596523235
        assertEquals(596523235, records[0].positionLat)
        assertEquals(167026505, records[0].positionLong)

        assertNotNull(decodedSession)
        assertEquals(Sport.RUNNING, decodedSession?.sport)
        assertEquals(120.toShort(), decodedSession?.avgHeartRate)
        assertEquals(140.toShort(), decodedSession?.maxHeartRate)

        directory.deleteRecursively()
    }

    @Test
    fun writeEncodesIndoorWorkoutWithoutGpsFixes() {
        val directory = Files.createTempDirectory("fit-indoor-test").toFile()
        val session = WorkoutSessionTransitions.start(1_000L, WorkoutActivityType.WALK)
        val events = listOf(
            WorkoutTrackEvent(1_000L, heartRateBpm = 110, steps = 10),
            WorkoutTrackEvent(2_000L, heartRateBpm = 115, steps = 20),
            WorkoutTrackEvent(3_000L, heartRateBpm = 125, steps = 30),
        )

        val fitFile = WorkoutFitWriter.write(directory, session, events, 3_000L)
        assertTrue(fitFile.exists())

        val records = mutableListOf<RecordMesg>()
        var decodedSession: SessionMesg? = null

        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(RecordMesgListener { mesg -> records.add(mesg) })
        broadcaster.addListener(SessionMesgListener { mesg -> decodedSession = mesg })

        FileInputStream(fitFile).use { input ->
            decode.read(input, broadcaster)
        }

        assertEquals(3, records.size)
        assertEquals(110.toShort(), records[0].heartRate)
        assertEquals(115.toShort(), records[1].heartRate)
        assertEquals(125.toShort(), records[2].heartRate)
        // Position should be null for indoor/GPS-less workout
        assertEquals(null, records[0].positionLat)
        assertEquals(null, records[0].positionLong)

        assertNotNull(decodedSession)
        assertEquals(Sport.WALKING, decodedSession?.sport)
        // 30 steps -> 15 strides; FitPub's FitParser stores totalSteps = strides * 2 = 30.
        assertEquals(15L, decodedSession?.totalStrides)

        directory.deleteRecursively()
    }

    @Test
    fun stridesRoundToTheNearestStrideAndAreOmittedWithoutSteps() {
        assertEquals(null, WorkoutFitWriter.stridesFor(0))
        assertEquals(1L, WorkoutFitWriter.stridesFor(1))
        assertEquals(4L, WorkoutFitWriter.stridesFor(7))
        assertEquals(500L, WorkoutFitWriter.stridesFor(1_000))
    }
}

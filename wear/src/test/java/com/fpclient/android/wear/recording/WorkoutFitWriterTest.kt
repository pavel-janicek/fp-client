package com.fpclient.android.wear.recording

import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
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
        var fileId: FileIdMesg? = null
        broadcaster.addListener(FileIdMesgListener { mesg -> fileId = mesg })

        FileInputStream(fitFile).use { input ->
            decode.read(input, broadcaster)
        }

        assertEquals("FP Wear", fileId?.productName)

        // One record per GPS fix; the HR-only event at 2_000 is not a record of its own (a
        // position-less record would split FitPub's elevation segments) but still counts toward
        // the session average below.
        assertEquals(2, records.size)
        assertEquals(100.toShort(), records[0].heartRate)
        assertEquals(140.toShort(), records[1].heartRate)

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

    @Test
    fun gpsWorkoutWritesOnlyPositionedRecordsCarryingTheNearestHeartRate() {
        // Regression for makni.cz activity 82bb40f7 (3.1.0): elevationGain was null because HR-
        // and steps-only records sat between GPS records, and FitParser opens a new elevation
        // segment at every record without a position — leaving each GPS point alone.
        val directory = Files.createTempDirectory("fit-gps-merge-test").toFile()
        val session = WorkoutSessionTransitions.start(1_000L, WorkoutActivityType.WALK)
        val events = (0 until 10).flatMap { i ->
            val t = 1_000L + i * 1_000L
            listOf(
                WorkoutTrackEvent(t, 50.0 + i * 0.0001, 14.0, altitudeMeters = 500.0 + i, accuracyMeters = 5.0),
                WorkoutTrackEvent(t + 300L, heartRateBpm = 100 + i),
                WorkoutTrackEvent(t + 600L, steps = (i + 1) * 2),
            )
        }

        val fitFile = WorkoutFitWriter.write(directory, session, events, 11_000L)

        val records = mutableListOf<RecordMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(RecordMesgListener { mesg -> records.add(mesg) })
        FileInputStream(fitFile).use { decode.read(it, broadcaster) }

        assertEquals(10, records.size)
        assertTrue(records.all { it.positionLat != null && it.positionLong != null })
        assertTrue(records.all { it.heartRate != null })
        // Nearest HR to the fix at t is the reading 300 ms later.
        assertEquals(100.toShort(), records.first().heartRate)
        assertEquals(109.toShort(), records.last().heartRate)

        directory.deleteRecursively()
    }

    @Test
    fun footWorkoutWritesRecordAndSessionCadence() {
        // makni.cz e4ac4998: the watch recorded steps but sent no cadence at all.
        val directory = Files.createTempDirectory("fit-cadence-test").toFile()
        val session = WorkoutSessionTransitions.start(0L, WorkoutActivityType.RUN)
        // 3 steps per second = 180 spm = 90 strides/min, one fix + one step total per second.
        val events = (0..60).flatMap { i ->
            val t = i * 1_000L
            listOf(
                WorkoutTrackEvent(t, 50.0 + i * 0.00005, 14.0, accuracyMeters = 5.0),
                WorkoutTrackEvent(t + 500L, steps = (i + 1) * 3),
            )
        }

        val fitFile = WorkoutFitWriter.write(directory, session, events, 61_000L)

        val records = mutableListOf<RecordMesg>()
        var decodedSession: SessionMesg? = null
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(RecordMesgListener { mesg -> records.add(mesg) })
        broadcaster.addListener(SessionMesgListener { mesg -> decodedSession = mesg })
        FileInputStream(fitFile).use { decode.read(it, broadcaster) }

        assertEquals(90.toShort(), records.last().cadence)
        assertEquals(90.toShort(), decodedSession?.maxCadence)
        // 183 steps -> 92 strides over 61 s of moving time.
        assertEquals(90.toShort(), decodedSession?.avgCadence)

        directory.deleteRecursively()
    }

    @Test
    fun cadenceWindowDoesNotReachBackAcrossAPause() {
        val events = listOf(
            WorkoutTrackEvent(0L, steps = 0),
            WorkoutTrackEvent(10_000L, steps = 30),
            WorkoutTrackEvent(10_000L, boundary = "PAUSE"),
            WorkoutTrackEvent(60_000L, boundary = "RESUME"),
            WorkoutTrackEvent(70_000L, steps = 60),
        )
        val timeline = WorkoutFitWriter.CadenceTimeline(events)

        // 30 steps in 10 s after the resume = 90 strides/min, not diluted by the paused minute.
        assertEquals(90.toShort(), timeline.stridesPerMinuteAt(70_000L))
        // Too soon after the resume for a meaningful window.
        assertEquals(null, timeline.stridesPerMinuteAt(62_000L))
    }

    @Test
    fun bikeWorkoutWritesNoCadence() {
        val directory = Files.createTempDirectory("fit-bike-cadence-test").toFile()
        val session = WorkoutSessionTransitions.start(0L, WorkoutActivityType.BIKE)
        val events = (0..30).map { i -> WorkoutTrackEvent(i * 1_000L, heartRateBpm = 120, steps = i * 3) }

        val fitFile = WorkoutFitWriter.write(directory, session, events, 31_000L)

        val records = mutableListOf<RecordMesg>()
        var decodedSession: SessionMesg? = null
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(RecordMesgListener { mesg -> records.add(mesg) })
        broadcaster.addListener(SessionMesgListener { mesg -> decodedSession = mesg })
        FileInputStream(fitFile).use { decode.read(it, broadcaster) }

        assertTrue(records.all { it.cadence == null })
        assertEquals(null, decodedSession?.avgCadence)

        directory.deleteRecursively()
    }
}

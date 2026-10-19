package com.fpclient.android.garmin

import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.Sport
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarminFitWriterTest {

    private val entry = GarminInboxEntry(
        id = "1700000000-7",
        label = "Run",
        createdEpochSeconds = 1_700_000_000,
        deviceName = "fēnix 8",
        receivedAtEpochMs = 0,
        sport = "RUN",
        durationSeconds = 100,
        distanceMeters = 300,
        expectedChunks = 1,
        receivedChunks = listOf(0),
    )

    /** 1 Hz along a meridian (0.00003° ≈ 3.34 m per second), a 30 s pause after second 59, 170 steps/min. */
    private fun samples(): List<GarminProtocol.Sample> =
        (0L until 60L).map { sample(it, it) } + (90L until 130L).map { sample(it, it - 30) }

    private fun sample(seconds: Long, moved: Long) = GarminProtocol.Sample(
        secondsSinceStart = seconds,
        latE6 = 50_000_000 + moved * 30,
        lonE6 = 14_000_000,
        altitudeDm = 2_345,
        heartRate = 140,
        steps = moved * 170 / 60,
    )

    private fun decode(file: File): Pair<SessionMesg?, List<RecordMesg>> {
        var session: SessionMesg? = null
        val records = mutableListOf<RecordMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(SessionMesgListener { session = it })
        broadcaster.addListener(RecordMesgListener { records += it })
        FileInputStream(file).use { decode.read(it, broadcaster) }
        return session to records
    }

    @Test
    fun writesOneRecordPerFixWithSummaryFromTheWatch() {
        val dir = Files.createTempDirectory("garmin-fit").toFile()
        val file = GarminFitWriter.write(File(dir, "w.fit"), entry, samples(), "Run")

        val (session, records) = decode(file)
        assertNotNull(session)
        assertEquals(100, records.size)
        assertEquals(Sport.RUNNING, session!!.sport)
        // Moving time and distance come from the watch's native session, elapsed from the samples.
        assertEquals(100f, session.totalTimerTime, 0.01f)
        assertEquals(130f, session.totalElapsedTime, 0.01f)
        assertEquals(300f, session.totalDistance, 0.01f)
        assertEquals(140.toShort(), session.avgHeartRate)
        assertEquals((99L * 170 / 60 + 1) / 2, session.totalStrides)
        assertEquals("Run", session.sportProfileName)

        val first = records.first()
        assertEquals(50.0, first.positionLat / 2147483648.0 * 180.0, 1e-5)
        // FIT stores altitude in 0.2 m steps.
        assertEquals(234.5f, first.altitude, 0.2f)
        assertEquals(140.toShort(), first.heartRate)
        // 59 + 39 one-second steps of 0.00003° (~3.34 m); the step across the pause adds nothing.
        val trackDistance = records.last().distance
        assertEquals(98 * 3.336f, trackDistance, 0.5f)
        dir.deleteRecursively()
    }

    @Test
    fun cyclingHasNoCadenceAndIndoorKeepsHeartRateRecords() {
        val dir = Files.createTempDirectory("garmin-fit").toFile()
        val bike = GarminFitWriter.write(File(dir, "b.fit"), entry.copy(sport = "BIKE"), samples(), "Bike")
        val (bikeSession, bikeRecords) = decode(bike)
        assertNull(bikeSession!!.avgCadence)
        assertTrue(bikeRecords.all { it.cadence == null })

        val indoor = samples().map { it.copy(latE6 = null, lonE6 = null) }
        val (indoorSession, indoorRecords) = decode(GarminFitWriter.write(File(dir, "i.fit"), entry, indoor, "Run"))
        assertEquals(100, indoorRecords.size)
        assertTrue(indoorRecords.all { it.positionLat == null && it.heartRate == 140.toShort() })
        assertEquals(300f, indoorSession!!.totalDistance, 0.01f)
        dir.deleteRecursively()
    }

    @Test
    fun sportKeysMatchTheWearMapping() {
        assertEquals(Sport.RUNNING, GarminFitWriter.sportFor("RUN"))
        assertEquals(Sport.WALKING, GarminFitWriter.sportFor("WALK"))
        assertEquals(Sport.HIKING, GarminFitWriter.sportFor("HIKE"))
        assertEquals(Sport.CYCLING, GarminFitWriter.sportFor("BIKE"))
        assertEquals(Sport.GENERIC, GarminFitWriter.sportFor("OTHER"))
        assertEquals(Sport.GENERIC, GarminFitWriter.sportFor(null))
    }
}

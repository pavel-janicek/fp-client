package com.fpclient.android.wear.recording

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExportWriterTest {
    @Test
    fun exportCreatesGpxSegmentsAndSensorSidecar() {
        val directory = Files.createTempDirectory("watch-export-test").toFile()
        val session = WorkoutSessionTransitions.start(1_000L, WorkoutActivityType.RUN)
        val events = listOf(
            WorkoutTrackEvent(1_000L, 50.0, 14.0, altitudeMeters = 220.0, accuracyMeters = 4.0, heartRateBpm = 100, steps = 0),
            WorkoutTrackEvent(2_000L, heartRateBpm = 120, steps = 2),
            WorkoutTrackEvent(3_000L, boundary = "PAUSE"),
            WorkoutTrackEvent(4_000L, 50.1, 14.1, altitudeMeters = 230.0, accuracyMeters = 3.0, heartRateBpm = 130, steps = 3),
            WorkoutTrackEvent(5_000L, boundary = "STOP"),
        )

        val files = WorkoutExportWriter.write(directory, session, events, 5_000L)
        val gpx = files.gpxFile.readText()
        val sidecar = WorkoutExportWriter.decodeSidecar(files.sidecarFile.readText())!!

        assertTrue(gpx.contains("version=\"1.1\""))
        assertEquals(2, Regex("<trkseg>").findAll(gpx).count())
        assertTrue(gpx.contains("1970-01-01T00:00:01Z"))
        assertEquals(listOf(100, 120, 130), sidecar.heartRateSamples.map { it.bpm })
        assertEquals(3, sidecar.steps)
        directory.deleteRecursively()
    }

    @Test
    fun gpxEscapesTitleAndOmitsEmptySegments() {
        val directory = Files.createTempDirectory("watch-gpx-test").toFile()
        val session = WorkoutSessionTransitions.start(1_000L)
        val files = WorkoutExportWriter.write(
            directory,
            session,
            listOf(WorkoutTrackEvent(2_000L, boundary = "PAUSE")),
            3_000L,
            title = "Run <fast> & safe",
        )
        val gpx = files.gpxFile.readText()

        assertTrue(gpx.contains("<name>Run &lt;fast&gt; &amp; safe</name>"))
        assertFalse(gpx.contains("<trkseg>"))
        assertEquals(WorkoutActivityType.RUN.name, files.sidecar.activityType)
        directory.deleteRecursively()
    }
}
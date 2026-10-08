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
    fun gpxCarriesHeartRateViaTrackPointExtension() {
        val directory = Files.createTempDirectory("watch-export-hr-test").toFile()
        val session = WorkoutSessionTransitions.start(1_000L, WorkoutActivityType.RUN)
        val events = listOf(
            // PPG sample just before the fix; the fix itself carries no BPM.
            WorkoutTrackEvent(900L, heartRateBpm = 100),
            WorkoutTrackEvent(1_000L, 50.0, 14.0, accuracyMeters = 4.0),
            WorkoutTrackEvent(2_000L, heartRateBpm = 120),
            WorkoutTrackEvent(2_500L, 50.1, 14.1, accuracyMeters = 3.0),
            WorkoutTrackEvent(3_000L, boundary = "STOP"),
        )

        val files = WorkoutExportWriter.write(directory, session, events, 3_000L)
        val gpx = files.gpxFile.readText()

        // Standard Garmin TrackPointExtension namespace is declared on the root element.
        assertTrue(gpx.contains("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\""))
        // Both fixes are within the match window of a PPG sample, so both are tagged.
        assertEquals(2, Regex("<gpxtpx:hr>").findAll(gpx).count())
        assertTrue(gpx.contains("<gpxtpx:hr>100</gpxtpx:hr>"))
        assertTrue(gpx.contains("<gpxtpx:hr>120</gpxtpx:hr>"))
        directory.deleteRecursively()
    }

    @Test
    fun gpxOmitsHeartRateWhenNoSampleIsNearAFix() {
        val directory = Files.createTempDirectory("watch-export-hr-gap-test").toFile()
        val session = WorkoutSessionTransitions.start(1_000L, WorkoutActivityType.RUN)
        val events = listOf(
            WorkoutTrackEvent(1_000L, heartRateBpm = 100),
            // Fix is a minute after the only PPG sample — beyond the match window.
            WorkoutTrackEvent(61_000L, 50.0, 14.0, accuracyMeters = 4.0),
            WorkoutTrackEvent(62_000L, boundary = "STOP"),
        )

        val files = WorkoutExportWriter.write(directory, session, events, 62_000L)
        val gpx = files.gpxFile.readText()

        assertFalse(gpx.contains("gpxtpx:hr"))
        directory.deleteRecursively()
    }

    @Test
    fun nearestHeartRatePicksClosestSampleAndRespectsTheWindow() {
        val timeline = listOf(1_000L to 100, 5_000L to 120, 9_000L to 130)

        // Exact match, earlier sample, later sample.
        assertEquals(100, WorkoutExportWriter.nearestHeartRate(timeline, 1_000L))
        assertEquals(120, WorkoutExportWriter.nearestHeartRate(timeline, 5_400L))
        assertEquals(120, WorkoutExportWriter.nearestHeartRate(timeline, 5_600L))
        assertEquals(130, WorkoutExportWriter.nearestHeartRate(timeline, 9_000L))
        // Empty timeline and out-of-window both yield null.
        assertEquals(null, WorkoutExportWriter.nearestHeartRate(emptyList(), 5_000L))
        assertEquals(null, WorkoutExportWriter.nearestHeartRate(timeline, 100_000L))
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
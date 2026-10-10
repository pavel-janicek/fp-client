package com.fpclient.android.wear.recording

import java.nio.file.Files
import org.junit.Test

/** Throwaway: prints a real HR-bearing GPX so its exact shape can be eyeballed. Deleted after use. */
class GpxSamplePrintTest {
    @Test
    fun printSample() {
        val directory = Files.createTempDirectory("gpx-sample").toFile()
        val session = WorkoutSessionTransitions.start(1_707_000_000_000L, WorkoutActivityType.RUN)
        val events = listOf(
            WorkoutTrackEvent(1_707_000_000_000L, 50.0, 14.0, altitudeMeters = 220.0, accuracyMeters = 4.0, heartRateBpm = 138),
            WorkoutTrackEvent(1_707_000_001_000L, 50.0009, 14.0009, altitudeMeters = 221.0, accuracyMeters = 3.5, heartRateBpm = 141),
            WorkoutTrackEvent(1_707_000_002_000L, 50.0018, 14.0018, altitudeMeters = 222.0, accuracyMeters = 3.2, heartRateBpm = 145),
            WorkoutTrackEvent(1_707_000_003_000L, boundary = "STOP"),
        )
        val files = WorkoutExportWriter.write(directory, session, events, 1_707_000_003_000L, title = "Morning run")
        println("===BEGIN_GPX===")
        println(files.gpxFile.readText())
        println("===END_GPX===")
        directory.deleteRecursively()
    }
}

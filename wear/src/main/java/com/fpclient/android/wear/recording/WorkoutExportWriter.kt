package com.fpclient.android.wear.recording

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WorkoutSidecar(
    val schemaVersion: Int = 1,
    val sessionId: Long,
    val activityType: String,
    val startedAtEpochMs: Long,
    val stoppedAtEpochMs: Long,
    val elapsedMs: Long,
    val movingMs: Long,
    val heartRateSamples: List<WorkoutHeartRateSample>,
    val steps: Int,
    val events: List<WorkoutTrackEvent>,
)

@Serializable
data class WorkoutHeartRateSample(
    val timeEpochMs: Long,
    val bpm: Int,
)

data class WorkoutExportFiles(
    val gpxFile: File,
    val sidecarFile: File,
    val sidecar: WorkoutSidecar,
)

object WorkoutExportWriter {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
    private val gpxTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun write(
        directory: File,
        session: WorkoutSessionSnapshot,
        events: List<WorkoutTrackEvent>,
        stoppedAtEpochMs: Long,
        title: String = "${session.activityType.label} workout",
    ): WorkoutExportFiles {
        directory.mkdirs()
        val sessionId = session.startedAtEpochMs
        val sidecar = WorkoutSidecar(
            sessionId = sessionId,
            activityType = session.activityType.name,
            startedAtEpochMs = session.startedAtEpochMs,
            stoppedAtEpochMs = stoppedAtEpochMs,
            elapsedMs = session.elapsedMsAt(stoppedAtEpochMs),
            movingMs = session.movingMsAt(stoppedAtEpochMs),
            heartRateSamples = events.mapNotNull { event ->
                event.heartRateBpm?.let { WorkoutHeartRateSample(event.timeEpochMs, it) }
            },
            steps = events.mapNotNull { it.steps }.maxOrNull() ?: 0,
            events = events,
        )
        val gpxFile = File(directory, "workout-$sessionId.gpx")
        val sidecarFile = File(directory, "workout-$sessionId.json")
        writeAtomically(gpxFile, buildGpx(title, session.activityType.name, events))
        writeAtomically(sidecarFile, json.encodeToString(sidecar))
        return WorkoutExportFiles(gpxFile, sidecarFile, sidecar)
    }

    fun encodeSidecar(sidecar: WorkoutSidecar): String = json.encodeToString(sidecar)

    fun decodeSidecar(value: String): WorkoutSidecar? =
        runCatching { json.decodeFromString<WorkoutSidecar>(value) }.getOrNull()

    private fun buildGpx(title: String, activityType: String, events: List<WorkoutTrackEvent>): String {
        val segments = mutableListOf<MutableList<WorkoutTrackEvent>>()
        var current = mutableListOf<WorkoutTrackEvent>()
        events.forEach { event ->
            if (event.boundary == "PAUSE" || event.boundary == "RESUME" || event.boundary == "STOP") {
                if (current.isNotEmpty()) segments += current
                current = mutableListOf()
            } else if (event.latitude != null && event.longitude != null && event.accuracyMeters != null) {
                current += event
            }
        }
        if (current.isNotEmpty()) segments += current

        // Heart-rate timeline used to tag each GPS fix with the nearest reading. HR samples and
        // GPS fixes are recorded as separate events (the PPG fires ~1 Hz, GNSS coarser), so a
        // trackpoint's own event rarely carries BPM — the nearest-in-time sample does.
        val heartRateTimeline = events
            .mapNotNull { event -> event.heartRateBpm?.let { event.timeEpochMs to it } }
            .sortedBy { it.first }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"FP Client Wear\" ")
            // TrackPointExtension is the de-facto standard way to carry heart rate inside GPX 1.1;
            // server-side GPX parsers read <gpxtpx:hr> exactly as they would a Garmin/Strava export.
            append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
            append("xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\">\n")
            append("  <metadata><name>").append(xmlEscape(title)).append("</name></metadata>\n")
            append("  <trk><name>").append(xmlEscape(title)).append("</name><type>")
                .append(xmlEscape(activityType)).append("</type>\n")
            segments.forEach { segment ->
                append("    <trkseg>\n")
                segment.forEach { event ->
                    append("      <trkpt lat=\"").append(event.latitude)
                        .append("\" lon=\"").append(event.longitude).append("\">\n")
                    event.altitudeMeters?.let { append("        <ele>").append(it).append("</ele>\n") }
                    append("        <time>").append(gpxTimeFormatter.format(Instant.ofEpochMilli(event.timeEpochMs)))
                        .append("</time>\n")
                    nearestHeartRate(heartRateTimeline, event.timeEpochMs)?.let { bpm ->
                        append("        <extensions>\n")
                        append("          <gpxtpx:TrackPointExtension>\n")
                        append("            <gpxtpx:hr>").append(bpm).append("</gpxtpx:hr>\n")
                        append("          </gpxtpx:TrackPointExtension>\n")
                        append("        </extensions>\n")
                    }
                    append("      </trkpt>\n")
                }
                append("    </trkseg>\n")
            }
            append("  </trk>\n</gpx>\n")
        }
    }

    /**
     * Closest heart-rate reading to [timeEpochMs] from a time-sorted list, or null when the nearest
     * sample is further than [HEART_RATE_MATCH_WINDOW_MS] away (a long PPG gap next to a fix means
     * tagging the fix would misrepresent the trace). Pure and package-visible for unit testing.
     */
    internal fun nearestHeartRate(
        sortedSamples: List<Pair<Long, Int>>,
        timeEpochMs: Long,
        maxDeltaMs: Long = HEART_RATE_MATCH_WINDOW_MS,
    ): Int? {
        if (sortedSamples.isEmpty()) return null
        var bestBpm: Int? = null
        var bestDelta = Long.MAX_VALUE
        for ((sampleTime, bpm) in sortedSamples) {
            val delta = kotlin.math.abs(sampleTime - timeEpochMs)
            if (delta < bestDelta) {
                bestDelta = delta
                bestBpm = bpm
            }
        }
        return if (bestDelta <= maxDeltaMs) bestBpm else null
    }

    private const val HEART_RATE_MATCH_WINDOW_MS = 15_000L

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun writeAtomically(file: File, content: String) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (file.exists() && !file.delete()) error("Could not replace ${file.name}")
        if (!temporary.renameTo(file)) error("Could not finalize ${file.name}")
    }
}
package com.fpclient.android.recording

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Assembles a recorded session into a GPX 1.1 document (Iteration 8d): one `<trk>`
 * with a separate `<trkseg>` per paused segment, every point carrying `<ele>` and
 * `<time>`. Pure JVM (no Android dependencies) so the output is unit-testable and the
 * retry path can regenerate a GPX from the persisted JSONL on any thread.
 *
 * The time format deliberately drops fractional seconds (many server-side GPX parsers
 * are strict about the `xsd:dateTime` shape) and coordinates are fixed to 6 decimals —
 * the usual convention for GPS traces.
 */
object GpxBuilder {

    private val timeFormat = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .withZone(ZoneOffset.UTC)

    /**
     * @param segments the recorded segments in order; each becomes one <trkseg>
     * @param name optional track/metadata name (XML-escaped)
     * @param activityType optional FitPub activity type, written as the GPX <type> hint
     * @param creator app identifier written into the root element
     */
    fun build(
        segments: List<List<TrackPoint>>,
        name: String? = null,
        activityType: String? = null,
        creator: String = "FP-Client",
    ): String {
        // A pause with no accepted fixes in between yields an empty segment (the caller
        // may pass them through from the file); an empty <trkseg> is invalid GPX, so it is
        // dropped rather than written.
        val nonEmpty = segments.filter { it.isNotEmpty() }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"").append(escape(creator))
            .append("\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        // Metadata time = session start (first fix of the first segment).
        val first = nonEmpty.firstOrNull()?.firstOrNull()
        sb.append("  <metadata>\n")
        name?.let { sb.append("    <name>").append(escape(it)).append("</name>\n") }
        first?.let { sb.append("    <time>").append(time(it.time)).append("</time>\n") }
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        name?.let { sb.append("    <name>").append(escape(it)).append("</name>\n") }
        activityType?.let { sb.append("    <type>").append(escape(it.lowercase(Locale.US))).append("</type>\n") }
        nonEmpty.forEach { segment ->
            sb.append("    <trkseg>\n")
            segment.forEach { point ->
                sb.append("      <trkpt lat=\"").append(coordinate(point.lat))
                    .append("\" lon=\"").append(coordinate(point.lon)).append("\">\n")
                sb.append("        <ele>").append(elevation(point.ele)).append("</ele>\n")
                if (point.time > 0) {
                    sb.append("        <time>").append(time(point.time)).append("</time>\n")
                }
                sb.append("      </trkpt>\n")
            }
            sb.append("    </trkseg>\n")
        }
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /** ISO-8601 UTC timestamp of a fix, in the strict second-precision GPX shape. */
    fun time(epochMs: Long): String = timeFormat.format(Instant.ofEpochMilli(epochMs))

    private fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun elevation(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** Escapes the five XML specials; the only characters user-chosen names can carry. */
    fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

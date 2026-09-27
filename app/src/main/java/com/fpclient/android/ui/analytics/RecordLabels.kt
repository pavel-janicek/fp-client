package com.fpclient.android.ui.analytics

import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.data.dto.PersonalRecordDto
import com.fpclient.android.util.Format
import java.util.Locale

/**
 * Human wording for the personal-records screen (Analytics redesign).
 *
 * The server stores a record as a bare `(recordType, value, unit)` triple, so printing them
 * verbatim produced things like "Longest duration 2318.0 seconds" and "Max Speed 2.71 mps" —
 * technically correct and useless. Every unit the server can send is listed here with the
 * [Format] helper that already knew how to render it, so the screen shows "38:38" and
 * "9.8 km/h" instead.
 *
 * Android-free so the mapping is unit-tested; see `RecordLabelsTest`.
 */
object RecordLabels {

    /** Server enum `PersonalRecord.RecordType`. */
    private const val LONGEST_DISTANCE = "LONGEST_DISTANCE"
    private const val LONGEST_DURATION = "LONGEST_DURATION"
    private const val HIGHEST_ELEVATION_GAIN = "HIGHEST_ELEVATION_GAIN"
    private const val MAX_SPEED = "MAX_SPEED"
    private const val BEST_AVERAGE_PACE = "BEST_AVERAGE_PACE"

    /** Fallback when the server adds a record type this build predates. */
    private const val UNKNOWN_RECORD = "Record"

    /**
     * The record type in ordinary words: `LONGEST_DURATION` -> "Longest duration",
     * `BEST_AVERAGE_PACE` -> "Best average pace". Unknown types degrade to a title-cased
     * version of the enum rather than shouting `SOMETHING_NEW` at the user.
     */
    fun recordType(recordType: String?): String {
        val raw = recordType?.trim().orEmpty()
        if (raw.isEmpty()) return UNKNOWN_RECORD
        return when (raw.uppercase()) {
            LONGEST_DISTANCE -> "Longest distance"
            LONGEST_DURATION -> "Longest duration"
            HIGHEST_ELEVATION_GAIN -> "Highest elevation"
            MAX_SPEED -> "Max speed"
            BEST_AVERAGE_PACE -> "Best average pace"
            else -> raw.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * The record's value, rendered for a human and honouring the user's unit setting.
     *
     * Both the record type and the unit are consulted: they are not redundant. The server
     * stores `meters` for both a distance and an elevation gain (one must be shown as km, the
     * other as m of climb), and `mps` is metres per second regardless of the user's setting —
     * "2.71 mps" is metric and unconverted, which reads as a foreign unit to anyone who
     * picked km.
     */
    fun value(record: PersonalRecordDto, unitSystem: String?): String {
        val value = record.value ?: return "—"
        val type = record.recordType?.trim()?.uppercase().orEmpty()
        return when (type) {
            LONGEST_DURATION -> Format.duration(value.toLong())
            HIGHEST_ELEVATION_GAIN -> Format.elevation(value, unitSystem)
            MAX_SPEED -> Format.speed(value, unitSystem)
            BEST_AVERAGE_PACE -> Format.pace(value.toLong(), unitSystem)
            LONGEST_DISTANCE -> Format.distance(value, unitSystem)
            // No type we recognise: fall back to the unit, so a new server record type is
            // still readable rather than blank.
            else -> fallback(value, record.unit)
        }
    }

    /**
     * Last-resort rendering for a unit this build does not know. Anything that looks like a
     * duration in seconds is still made human, because a bare "2318.0 seconds" is the single
     * worst thing this screen can show.
     */
    private fun fallback(value: Double, unit: String?): String = when (unit?.trim()?.lowercase()) {
        "seconds", "second", "s" -> Format.duration(value.toLong())
        "meters", "meter", "m" -> Format.distance(value, "METRIC")
        else -> {
            val suffix = unit?.trim().orEmpty()
            if (suffix.isEmpty()) {
                String.format(Locale.US, "%.1f", value)
            } else {
                String.format(Locale.US, "%.1f %s", value, suffix)
            }
        }
    }

    /**
     * The group heading for an activity type: the same emoji the Record screen's type picker
     * uses, followed by the type in normal capitalised English — `RUN` -> "Run",
     * `NORDIC_SKI` -> "Nordic Ski".
     *
     * The Record screen renders the same thing lower-cased ("🏃 run") because that is a
     * button label; a section heading reads better capitalised, so each word is
     * capitalised here.
     */
    fun activityHeading(activityType: String?): String {
        val type = activityType?.trim().orEmpty()
        if (type.isEmpty()) return "\uD83C\uDFCB Other"
        val readable = type.replace('_', ' ')
            .lowercase()
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        return "${ActivityTypes.icon(type)} $readable"
    }
}

package com.fpclient.android.ui.analytics

import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.data.dto.PersonalRecordDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The records screen used to print the server's raw `(recordType, value, unit)` triple,
 * giving "Longest duration 2318.0 seconds" and "Max Speed 2.71 mps". These pin the human
 * rendering, including that the user's unit preference is honoured.
 */
class RecordLabelsTest {

    private fun record(type: String?, value: Double?, unit: String?) =
        PersonalRecordDto(recordType = type, value = value, unit = unit)

    // ------------------------------------------------------------------ wording

    @Test
    fun recordTypesReadAsOrdinaryEnglish() {
        assertEquals("Longest distance", RecordLabels.recordType("LONGEST_DISTANCE"))
        assertEquals("Longest duration", RecordLabels.recordType("LONGEST_DURATION"))
        assertEquals("Highest elevation", RecordLabels.recordType("HIGHEST_ELEVATION_GAIN"))
        assertEquals("Max speed", RecordLabels.recordType("MAX_SPEED"))
        assertEquals("Best average pace", RecordLabels.recordType("BEST_AVERAGE_PACE"))
    }

    @Test
    fun anUnknownRecordTypeIsStillReadable() {
        // A record type this build predates must not reach the user as SHOUTING_ENUM.
        assertEquals("Fastest 5k", RecordLabels.recordType("FASTEST_5K"))
        assertEquals("Record", RecordLabels.recordType(null))
    }

    // ------------------------------------------------------------------ values

    @Test
    fun durationIsNotShownInSeconds() {
        // 2318 s is 38:38, and "2318.0 seconds" told the user nothing.
        assertEquals(
            "38:38",
            RecordLabels.value(record("LONGEST_DURATION", 2318.0, "seconds"), "METRIC"),
        )
        assertEquals(
            "1:01:01",
            RecordLabels.value(record("LONGEST_DURATION", 3661.0, "seconds"), "METRIC"),
        )
    }

    @Test
    fun speedIsConvertedToTheUsersUnitInsteadOfShowingMps() {
        // "mps" is metres per second: metric, but unconverted and meaningless next to km.
        assertEquals(
            "9.8 km/h",
            RecordLabels.value(record("MAX_SPEED", 2.71, "mps"), "METRIC"),
        )
        assertEquals(
            "6.1 mph",
            RecordLabels.value(record("MAX_SPEED", 2.71, "mps"), "IMPERIAL"),
        )
    }

    @Test
    fun paceIsRenderedPerKmOrPerMile() {
        assertEquals(
            "5:18 /km",
            RecordLabels.value(record("BEST_AVERAGE_PACE", 318.0, "seconds_per_km"), "METRIC"),
        )
        assertEquals(
            "8:31 /mi",
            RecordLabels.value(record("BEST_AVERAGE_PACE", 318.0, "seconds_per_km"), "IMPERIAL"),
        )
    }

    @Test
    fun distanceAndElevationUseTheSameUnitButReadDifferently() {
        // Both arrive as "meters" — the record type is what tells them apart.
        assertEquals("42.20 km", RecordLabels.value(record("LONGEST_DISTANCE", 42195.0, "meters"), "METRIC"))
        assertEquals("1200 m", RecordLabels.value(record("HIGHEST_ELEVATION_GAIN", 1200.0, "meters"), "METRIC"))
        assertEquals("26.22 mi", RecordLabels.value(record("LONGEST_DISTANCE", 42195.0, "meters"), "IMPERIAL"))
    }

    @Test
    fun anUnknownRecordTypeStillNeverPrintsRawSeconds() {
        // The worst thing this screen can show is a bare seconds count, whatever the type.
        val rendered = RecordLabels.value(record("SOMETHING_NEW", 2318.0, "seconds"), "METRIC")
        assertEquals("38:38", rendered)
    }

    @Test
    fun aMissingValueShowsADash() {
        assertEquals("—", RecordLabels.value(record("LONGEST_DISTANCE", null, "meters"), "METRIC"))
    }

    // ------------------------------------------------------------------ headings

    @Test
    fun activityHeadingsUseTheRecordScreenEmojiAndNormalEnglish() {
        assertEquals("🏃 Run", RecordLabels.activityHeading("RUN"))
        assertEquals("🥾 Hike", RecordLabels.activityHeading("HIKE"))
        assertEquals("🚶 Walk", RecordLabels.activityHeading("WALK"))
        // The case the brief called out: NORDIC_SKI must not read "Nordic ski" or "NORDIC SKI".
        assertEquals("⛷️ Nordic Ski", RecordLabels.activityHeading("NORDIC_SKI"))
        assertEquals("🚴 Ride", RecordLabels.activityHeading("RIDE"))
    }

    @Test
    fun anUnknownActivityTypeStillGetsAnIconAndACapitalisedName() {
        // Compare against the icon the app actually uses rather than re-typing a surrogate
        // pair: the emoji carries a variation selector a hand-written escape would miss.
        assertEquals(
            "${ActivityTypes.icon("SOME_NEW_TYPE")} Some New Type",
            RecordLabels.activityHeading("SOME_NEW_TYPE"),
        )
    }
}

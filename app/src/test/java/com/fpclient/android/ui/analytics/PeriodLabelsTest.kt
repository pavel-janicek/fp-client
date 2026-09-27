package com.fpclient.android.ui.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.fpclient.android.data.dto.TrainingLoadDto
import java.util.Locale

/**
 * The period headings used to be the raw `periodStart` string, so a month rendered as
 * "2026-09-01". These pin the human-readable forms.
 */
class PeriodLabelsTest {

    /**
     * Pin the locale: the app formats month names in the user's locale, so a test that
     * hard-coded English passed or failed depending on the machine it ran on.
     */
    private val EN = Locale.ENGLISH

    private fun day(date: String, count: Int, distance: Double) = TrainingLoadDto(
        date = date,
        activityCount = count,
        totalDurationSeconds = 3600L,
        totalDistanceMeters = distance,
    )

    @Test
    fun aMonthReadsAsItsNameAndYear() {
        assertEquals("September 2026", PeriodLabels.heading("2026-09-01", "MONTH", EN))
        assertEquals("January 2025", PeriodLabels.heading("2025-01-01", "MONTH", EN))
        assertEquals("December 2026", PeriodLabels.heading("2026-12-01", "MONTH", EN))
    }

    @Test
    fun aYearReadsAsJustTheYear() {
        assertEquals("2026", PeriodLabels.heading("2026-01-01", "YEAR", EN))
    }

    @Test
    fun aWeekReadsAsItsDateRange() {
        // 2026-09-21 is a Monday, so the week is Mon 21st - Sun 27th.
        assertEquals("21 – 27 Sep 2026", PeriodLabels.heading("2026-09-21", "WEEK", EN))
    }

    @Test
    fun aWeekSpanningAMonthBoundarySaysSoOnBothSides() {
        // Pretending "28 Dec – 3 Jan" is all December would be quietly wrong.
        val heading = PeriodLabels.heading("2026-12-28", "WEEK", EN)
        assertEquals("28 Dec 2026 – 3 Jan 2027", heading)
    }

    @Test
    fun withoutAPeriodTypeAMonthIsInferredFromTheFirstOfTheMonth() {
        // The server always starts a month on the 1st, which is a reliable signal.
        assertEquals("September 2026", PeriodLabels.heading("2026-09-01", null, EN))
        // Anything else falls back to a plain day, never a wrong month.
        assertEquals("15 Sep 2026", PeriodLabels.heading("2026-09-15", null, EN))
    }

    @Test
    fun anUnparsableDateFallsBackToTheRawStringRatherThanBlanking() {
        assertEquals("2026-09", PeriodLabels.heading("2026-09", "MONTH", EN))
        assertEquals("", PeriodLabels.heading(null, null, EN))
        assertEquals("", PeriodLabels.heading("  ", null, EN))
    }

    /**
     * The guarantee behind the "who decides when a week starts" question: **Monday, always.**
     *
     * The server hardcodes `previousOrSame(DayOfWeek.MONDAY)` with no configuration and no
     * locale involved, so the app must not consult the locale either. This test is the
     * guard: a Sunday-first locale and a Monday-first locale must produce the *same* week
     * boundaries, or the app would mislabel the server's rows and disagree with its own
     * weekly summaries by a day.
     */
    @Test
    fun weekBoundariesAreMondayRegardlessOfLocale() {
        val sundayFirst = Locale.US // weeks start Sunday
        val mondayFirst = java.util.Locale.forLanguageTag("cs") // weeks start Monday
        val fromUs = PeriodLabels.heading("2026-09-21", "WEEK", sundayFirst)
        val fromCz = PeriodLabels.heading("2026-09-21", "WEEK", mondayFirst)

        // The month *abbreviations* are expected to differ ("Sep" / "zář") — that is the
        // locale doing its job. The week *boundaries* must not: same first day, same last
        // day, in both.
        val days = Regex("\\d+")
        assertEquals(
            "Sunday-first locale must not shift the week",
            days.findAll(fromUs).map { it.value }.toList(),
            days.findAll(fromCz).map { it.value }.toList(),
        )
        // 2026-09-21 is a Monday; the range must run Monday -> Sunday (21st to 27th).
        assertEquals(listOf("21", "27", "2026"), days.findAll(fromUs).map { it.value }.toList())
    }

    /**
     * The same guarantee for the week *bucketing* the Load/Trends bars are built from: a
     * Sunday belongs to the week that started on the preceding Monday.
     */
    @Test
    fun aSundayIsBucketedWithTheMondayThatStartedItsWeek() {
        // Mon 21st and Sun 27th are one server week; Mon 28th starts the next.
        val weeks = TrainingLoadMath.toWeeks(
            listOf(
                day("2026-09-21", 1, 10_000.0),
                day("2026-09-27", 1, 20_000.0),
                day("2026-09-28", 1, 30_000.0),
            ),
        )
        assertEquals(2, weeks.size)
        assertEquals("21 – 27 Sep 2026", PeriodLabels.heading(weeks[0].start.toString(), "WEEK", EN))
        assertEquals("28 Sep 2026 – 4 Oct 2026", PeriodLabels.heading(weeks[1].start.toString(), "WEEK", EN))
    }
}

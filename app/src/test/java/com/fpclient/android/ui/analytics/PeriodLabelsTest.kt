package com.fpclient.android.ui.analytics

import org.junit.Assert.assertEquals
import org.junit.Test
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
}

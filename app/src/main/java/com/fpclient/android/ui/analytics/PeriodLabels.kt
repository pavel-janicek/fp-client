package com.fpclient.android.ui.analytics

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Headings for the period tabs.
 *
 * The server sends `periodStart` as a bare ISO date and the list used to print it
 * verbatim, so a month read "2026-09-01" rather than "September 2026". The formatting
 * rules live here — Android-free, like the rest of this package's logic — because a
 * heading is exactly the sort of thing that has to work for every locale the app
 * supports, and it must be testable without a device.
 *
 * Unknown or unparsable input degrades to the raw string rather than showing an error
 * or an empty heading: a slightly ugly heading beats a missing one.
 *
 * **Weeks always start on Monday, in every locale.** That is not a stylistic choice: the
 * server hardcodes it — `TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)` in both
 * `ActivitySummaryAnalyticsService` and `ActivitySummaryAnalyticsModule`, with no
 * configuration and no locale involved — so the `periodStart` it sends for a `WEEK` row
 * is always a Monday, and the user cannot change it. The app therefore must not consult
 * the locale here. Doing so would be the "obvious improvement" and a real bug: a
 * Sunday-first locale would label the server's Mon–Sun row "21 – 27" against a Sunday
 * boundary, and the two would disagree by a day. If Sunday-start weeks are ever wanted,
 * that is a **server** change first, and an app setting after — never an app-only one.
 */
object PeriodLabels {

    /** Server enum values for `ActivitySummary.PeriodType`. */
    private const val WEEK = "WEEK"
    private const val MONTH = "MONTH"
    private const val YEAR = "YEAR"

    // Formatters are built per call rather than cached: they are locale-bound, and the
    // default locale can change while the process is alive (the user changing it in
    // Settings). Three small objects per heading is not worth a cache.
    private fun monthYear(locale: Locale) = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private fun dayMonthYear(locale: Locale) = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private fun dayMonth(locale: Locale) = DateTimeFormatter.ofPattern("d MMM", locale)

    /** The heading for one summary row, in the user's own locale. */
    fun heading(periodStart: String?, periodType: String? = null): String =
        heading(periodStart, periodType, Locale.getDefault())

    /**
     * [locale] is a parameter so the month names are testable. The app always passes the
     * user's locale — a Czech user should see "září 2026", not "September 2026" — and a
     * test that hard-coded English would otherwise pass or fail depending on the machine
     * it ran on. (It did: this suite failed in Czech until the locale was pinned.)
     *
     *  - month -> "September 2026"
     *  - week  -> "21 – 27 Sep 2026" (a start date alone does not say which week)
     *  - year  -> "2026"
     *
     * [periodType] is optional: when the caller does not have it, the shape of the date
     * itself decides (the server always starts a month on the 1st, which is a reliable
     * signal; anything else is treated as a single day).
     */
    fun heading(periodStart: String?, periodType: String?, locale: Locale): String {
        val raw = periodStart?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw
        return when (periodType?.uppercase()) {
            MONTH -> date.format(monthYear(locale))
            YEAR -> date.year.toString()
            WEEK -> weekHeading(date, locale)
            null -> if (date.dayOfMonth == 1) {
                date.format(monthYear(locale))
            } else {
                date.format(dayMonthYear(locale))
            }
            else -> date.format(dayMonthYear(locale))
        }
    }

    /**
     * "21 – 27 Sep 2026". A week spanning a month or year boundary is written out in full
     * on both sides ("28 Dec 2026 – 3 Jan 2027") rather than pretending the month is the
     * same, which would quietly misdate the week.
     *
     * [monday] is whatever the server sent as `periodStart`, which is **always a Monday** —
     * see the note on the companion `heading`. This only ever adds six days to it.
     */
    private fun weekHeading(monday: LocalDate, locale: Locale): String {
        val sunday = monday.plusDays(6)
        return if (monday.month == sunday.month && monday.year == sunday.year) {
            "${monday.dayOfMonth} – ${sunday.format(dayMonth(locale))} ${monday.year}"
        } else {
            "${monday.format(dayMonthYear(locale))} – ${sunday.format(dayMonthYear(locale))}"
        }
    }
}

package com.fpclient.android.ui.analytics

import com.fpclient.android.data.dto.TrainingLoadDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the Analytics mockups.
 *
 * The week bucketing is the part that is easy to get wrong — an off-by-one there silently
 * merges two weeks or splits one, and the "vs last week" numbers then lie. These pin it.
 */
class TrainingLoadMathTest {

    private fun day(date: String, count: Int, distance: Double = 10_000.0) = TrainingLoadDto(
        date = date,
        activityCount = count,
        totalDurationSeconds = if (count == 0) 0L else 3600L,
        totalDistanceMeters = if (count == 0) 0.0 else distance,
        trainingStressScore = if (count == 0) 0.0 else 50.0,
        chronicTrainingLoad = 48.0,
        acuteTrainingLoad = 55.0,
        trainingStressBalance = -7.0,
    )

    @Test
    fun daysOfOneWeekCollapseIntoASingleEntry() {
        // 2026-09-21 is a Monday, so these four days are one ISO week.
        val weeks = TrainingLoadMath.toWeeks(
            listOf(
                day("2026-09-21", 1, 10_000.0),
                day("2026-09-22", 1, 20_000.0),
                day("2026-09-23", 1, 5_000.0),
                day("2026-09-24", 1, 1_000.0),
            ),
        )
        assertEquals(1, weeks.size)
        assertEquals(36_000.0, weeks[0].distanceMeters, 0.01)
        assertEquals(4, weeks[0].activityCount)
    }

    @Test
    fun aNewWeekStartsANewEntryAndTheFinishedOneIsKept() {
        val weeks = TrainingLoadMath.toWeeks(
            listOf(
                day("2026-09-21", 1, 10_000.0), // Monday
                day("2026-09-27", 1, 20_000.0), // Sunday, same ISO week
                day("2026-09-28", 1, 30_000.0), // Monday, new week
            ),
        )
        assertEquals(2, weeks.size)
        assertEquals(30_000.0, weeks[0].distanceMeters, 0.01)
        assertEquals(30_000.0, weeks[1].distanceMeters, 0.01)
    }

    @Test
    fun restDaysAreSkippedRatherThanPaddedIntoTheChart() {
        val weeks = TrainingLoadMath.toWeeks(
            listOf(day("2026-09-21", 1, 10_000.0), day("2026-09-22", 0), day("2026-09-23", 0)),
        )
        assertEquals(1, weeks.size)
        assertEquals(10_000.0, weeks[0].distanceMeters, 0.01)
        assertEquals(1, weeks[0].activityCount)
    }

    @Test
    fun theInProgressWeekIsEmittedSoTheUserCanSeeTheWeekTheyAreIn() {
        val weeks = TrainingLoadMath.toWeeks(
            listOf(day("2026-09-14", 1, 10_000.0), day("2026-09-21", 1, 12_000.0)),
        )
        assertEquals(2, weeks.size)
        assertEquals(12_000.0, weeks.last().distanceMeters, 0.01)
        assertEquals(12_000.0, TrainingLoadMath.currentWeek(
            listOf(day("2026-09-14", 1, 10_000.0), day("2026-09-21", 1, 12_000.0)),
        )!!.distanceMeters, 0.01)
    }

    @Test
    fun weekOverWeekIsSignedAndNullWithoutABaseline() {
        val current = TrainingLoadMath.Week(LocalDate.parse("2026-09-21"), 12_000.0, 3_600L, 2)
        assertEquals(
            20,
            TrainingLoadMath.weekOverWeekPercent(current, current.copy(distanceMeters = 10_000.0)),
        )
        assertEquals(
            -20,
            TrainingLoadMath.weekOverWeekPercent(current, current.copy(distanceMeters = 15_000.0)),
        )
        // No previous week, or a previous week with nothing in it: no percentage to show.
        assertNull(TrainingLoadMath.weekOverWeekPercent(current, null))
        assertNull(
            TrainingLoadMath.weekOverWeekPercent(current, current.copy(distanceMeters = 0.0)),
        )
    }

    @Test
    fun aWeekWithNothingAfterAFullWeekReadsAsMinusAHundred() {
        val current = TrainingLoadMath.Week(LocalDate.parse("2026-09-21"), 0.0, 0L, 0)
        val previous = TrainingLoadMath.Week(LocalDate.parse("2026-09-14"), 10_000.0, 3_600L, 1)
        assertEquals(-100, TrainingLoadMath.weekOverWeekPercent(current, previous))
    }

    @Test
    fun percentagesAreFormattedWithASign() {
        assertEquals("+20%", TrainingLoadMath.formatPercent(20))
        assertEquals("-4%", TrainingLoadMath.formatPercent(-4))
        assertEquals("0%", TrainingLoadMath.formatPercent(0))
        assertNull(TrainingLoadMath.formatPercent(null))
    }

    @Test
    fun theVerdictNeverBlamesTheUserForMissingData() {
        // No form value at all must not read as "fatigued".
        assertEquals(
            "Not enough data to calculate form status.",
            TrainingLoadMath.describeBalance(null, 40.0, 40.0),
        )
    }

    @Test
    fun balanceIsDescribedInWordsWithNoAcronymsAnywhere() {
        val fresh = TrainingLoadMath.describeBalance(20.0, 50.0, 30.0)
        val fatigued = TrainingLoadMath.describeBalance(-40.0, 50.0, 90.0)
        val balanced = TrainingLoadMath.describeBalance(0.0, 1.0, 1.0)
        assertTrue("unexpected wording: $fresh", fresh.contains("rested", ignoreCase = true))
        assertTrue("unexpected wording: $fatigued", fatigued.contains("fatigue", ignoreCase = true))
        // The whole point of the variant: no sports-science vocabulary reaches the user.
        listOf(fresh, fatigued, balanced, TrainingLoadMath.BALANCE_GLOSSARY).forEach {
            assertFalse("jargon leaked: $it", it.contains("CTL") || it.contains("ATL") || it.contains("TSB"))
        }
    }

    @Test
    fun recentWeeksTakesTheLastNInChronologicalOrder() {
        val loads = (0..5).map { week ->
            day(
                LocalDate.parse("2026-01-05").plusWeeks(week.toLong()).toString(),
                1,
                1_000.0 * (week + 1),
            )
        }
        val weeks = TrainingLoadMath.recentWeeks(loads, count = 3)
        assertEquals(3, weeks.size)
        assertEquals(4_000.0, weeks[0].distanceMeters, 0.01)
        assertEquals(6_000.0, weeks[2].distanceMeters, 0.01)
    }

    @Test
    fun malformedDatesAreIgnoredRatherThanCrashingTheTab() {
        val weeks = TrainingLoadMath.toWeeks(
            listOf(day("not-a-date", 1), day("2026-09-21", 1, 10_000.0)),
        )
        assertEquals(1, weeks.size)
        assertEquals(10_000.0, weeks[0].distanceMeters, 0.01)
    }

    @Test
    fun anEmptyHistoryProducesNoWeeksAndNoCrash() {
        assertTrue(TrainingLoadMath.toWeeks(emptyList()).isEmpty())
        assertNull(TrainingLoadMath.currentWeek(emptyList()))
        assertNull(TrainingLoadMath.previousWeek(emptyList()))
    }
}

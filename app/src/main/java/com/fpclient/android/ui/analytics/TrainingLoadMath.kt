package com.fpclient.android.ui.analytics

import com.fpclient.android.data.dto.TrainingLoadDto
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The arithmetic behind the Load tab (and the rest of the Analytics redesign).
 *
 * Android-free on purpose (house style, like `NotificationText` / `NtfyMessages`): the
 * percentage and bucketing rules are the parts that are easy to get subtly wrong, so
 * they are plain functions under unit test rather than arithmetic buried in a composable.
 *
 * Everything here is computed **on the device** from rows the server already returns —
 * no server change is involved.
 */
object TrainingLoadMath {

    /** One calendar week of daily training-load rows, oldest day first. */
    data class Week(
        val start: LocalDate,
        val distanceMeters: Double,
        val durationSeconds: Long,
        val activityCount: Int,
    )

    /**
     * Change against the previous week, as a signed percentage, or null when there is no
     * meaningful baseline (no previous week, or the previous week was empty — dividing by
     * zero would otherwise render "∞%").
     */
    fun weekOverWeekPercent(current: Week, previous: Week?): Int? {
        if (previous == null || previous.distanceMeters <= 0.0) return null
        if (current.distanceMeters <= 0.0) return -100
        val delta = (current.distanceMeters - previous.distanceMeters) / previous.distanceMeters
        return (delta * 100).roundToInt()
    }

    /** "+12%", "-4%", or null when there is no baseline. */
    fun formatPercent(percent: Int?): String? = when {
        percent == null -> null
        percent > 0 -> "+$percent%"
        percent == 0 -> "0%"
        else -> "$percent%"
    }

    /**
     * Folds the server's daily rows into whole weeks, oldest first, skipping empty days so
     * the chart is not padded with rest days. A trailing partial week is kept — it is the
     * one the user is living in.
     */
    fun toWeeks(loads: List<TrainingLoadDto>): List<Week> {
        // The endpoint returns oldest-first; sorting by date makes this independent of it.
        val byDate = loads.mapNotNull { day ->
            val date = day.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            date?.let { it to day }
        }.sortedBy { it.first }

        val weeks = mutableListOf<Week>()
        var current: Week? = null
        for ((date, day) in byDate) {
            // Rest days are skipped entirely: a chart padded with zero-height bars is noise.
            if (day.activityCount == 0) continue
            val weekStart = date.minusDays(date.dayOfWeek.value.toLong() - 1L) // Monday
            val existing = current
            current = if (existing != null && existing.start == weekStart) {
                // Same week: fold this day in.
                Week(
                    start = existing.start,
                    distanceMeters = existing.distanceMeters + day.totalDistanceMeters,
                    durationSeconds = existing.durationSeconds + day.totalDurationSeconds,
                    activityCount = existing.activityCount + day.activityCount,
                )
            } else {
                // A new week started, so the previous one is finished and must be emitted
                // exactly once — emitting here rather than per-day is what keeps one entry
                // per week.
                if (existing != null) weeks += existing
                Week(weekStart, day.totalDistanceMeters, day.totalDurationSeconds, day.activityCount)
            }
        }
        // Flush the week in progress, which is the one the user is living in.
        current?.let { weeks += it }
        return weeks
    }

    /** The most recent [count] weeks, oldest first (the order a chart wants). */
    fun recentWeeks(loads: List<TrainingLoadDto>, count: Int = 8): List<Week> =
        toWeeks(loads).takeLast(count)

    /** The single most recent week, or null when the user has logged nothing. */
    fun currentWeek(loads: List<TrainingLoadDto>): Week? = toWeeks(loads).lastOrNull()

    /** The week before it — the comparison baseline. */
    fun previousWeek(loads: List<TrainingLoadDto>): Week? {
        val weeks = toWeeks(loads)
        return if (weeks.size >= 2) weeks[weeks.size - 2] else null
    }

    /** Human sentence for a stress/fitness/fatigue trio, the way a coach would say it. */
    fun describeBalance(form: Double?, fitness: Double?, fatigue: Double?): String {
        if (form == null) return "Not enough data to calculate form status."
        return when {
            form > 10 -> "You are well rested and ready for hard training."
            form >= -10 -> "Good balance between fitness and fatigue."
            form >= -30 -> "You are carrying some fatigue. An easy day would not hurt."
            else -> "High fatigue detected. Consider taking a rest day."
        }
    }

    /** The one-line glossary that makes the numbers above legible. */
    const val BALANCE_GLOSSARY: String =
        "Fitness is what your training has built up. Fatigue is what a recent hard effort " +
            "costs you. Form is the difference between the two."

    /** Largest stress score in the set, for scaling the bar chart. */
    fun peakStress(loads: List<TrainingLoadDto>): Float =
        loads.mapNotNull { it.trainingStressScore }.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

    /** Formats a metric without a trailing ".0". */
    fun whole(value: Double?): String =
        value?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
}

package com.fpclient.android.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.TrainingLoadDto
import com.fpclient.android.ui.theme.FPClientTheme
import com.fpclient.android.util.Format
import java.time.LocalDate
import java.util.Locale

/**
 * **Mockup only — not part of the shipped Analytics tab.**
 *
 * The brief for the Analytics redesign was "I do not understand the Load tab at
 * all", so the first question is whether the tab is confusing *because it is
 * jargon* or confusing *because it is badly laid out*. The only honest way to
 * answer that is to look at the alternatives, which is what these previews are.
 *
 * Three variants, all driven by the same sample data:
 *
 *  - [LoadTabMockupCurrent] — what ships today, for a side-by-side baseline.
 *  - [LoadTabMockupExplained] — same numbers, but the server's own plain-English
 *    `description` is surfaced, the acronyms are expanded, and 90 daily rows
 *    become a chart plus a handful of detail rows.
 *  - [LoadTabMockupPlain] — the third option: the numbers, framed as one sentence
 *    of "how you are doing", with no sports-science vocabulary at all.
 *
 * See `docs/ANALYTICS-TAB-REDESIGN.md` §5 and §8.1.
 */
private val sampleLoad: List<TrainingLoadDto> = (0 until 90).map { back ->
    val day = back.toLong()
    val wave = 0.5 + 0.5 * kotlin.math.sin(day / 9.0)
    TrainingLoadDto(
        date = LocalDate.now().minusDays(back.toLong()).toString(),
        activityCount = if (back % 7L == 0L) 0 else 1,
        totalDurationSeconds = if (back % 7L == 0L) 0L else 2700L + back * 60L,
        totalDistanceMeters = if (back % 7L == 0L) 0.0 else 8000.0 * wave,
        totalElevationGainMeters = if (back % 7L == 0L) 0.0 else 120.0 * wave,
        trainingStressScore = 40.0 * wave + 25.0,
        acuteTrainingLoad = 55.0 * wave + 30.0,
        chronicTrainingLoad = 48.0 * wave + 28.0,
        trainingStressBalance = 7.0 * wave - 3.0,
    )
}

/** The server's own wording, verbatim from `AnalyticsResource.getFormStatusDescription`. */
private fun descriptionFor(status: String): String = when (status) {
    "FRESH" -> "You're well rested and ready for hard training!"
    "OPTIMAL" -> "Good balance between fitness and fatigue."
    "FATIGUED" -> "High fatigue detected. Consider taking a rest day."
    else -> "Not enough data to calculate form status."
}

// ---------------------------------------------------------------- variant 1: today

/** Exactly what `TrainingLoadContent` renders today, for comparison. */
@Preview(name = "1 - current (as shipped)", heightDp = 900, showBackground = true)
@Composable
private fun LoadTabMockupCurrent() = FPClientTheme {
    CurrentLoadContent(sampleLoad)
}

@Composable
private fun CurrentLoadContent(loads: List<TrainingLoadDto>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        items(loads.size) { index ->
            val l = loads.reversed()[index]
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(text = l.date ?: "", style = MaterialTheme.typography.titleSmall)
                    val stress = l.trainingStressScore?.toFloat() ?: 0f
                    val maxStress = loads.mapNotNull { it.trainingStressScore?.toFloat() }.maxOrNull() ?: 1f
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (stress / maxStress.coerceAtLeast(1f)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    com.fpclient.android.ui.components.StatRow(
                        listOf(
                            "Stress" to (l.trainingStressScore?.let { String.format(Locale.US, "%.0f", it) } ?: "-"),
                            "Fitness (CTL)" to (l.chronicTrainingLoad?.let { String.format(Locale.US, "%.0f", it) } ?: "-"),
                            "Fatigue (ATL)" to (l.acuteTrainingLoad?.let { String.format(Locale.US, "%.0f", it) } ?: "-"),
                            "Form" to (l.trainingStressBalance?.let { String.format(Locale.US, "%+.0f", it) } ?: "-"),
                        ),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------- variant 2: explained

/**
 * Same data, but the three things that make the current tab unreadable are fixed:
 * the server's own plain-English sentence is shown, the acronyms are expanded where
 * they first appear, and 90 rows become a chart with a few recent days underneath.
 */
@Preview(name = "2 - explained", heightDp = 900, showBackground = true)
@Composable
private fun LoadTabMockupExplained() = FPClientTheme {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("How you are doing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "High fatigue detected. Consider taking a rest day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // The one line that makes the numbers below legible.
                    Text(
                        "Fitness is what training has built up. Fatigue is what a recent " +
                            "hard effort costs you. Form is the difference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    com.fpclient.android.ui.components.StatRow(
                        listOf("Fitness" to "58", "Fatigue" to "62", "Form" to "-4"),
                    )
                }
            }
        }
        item { LoadChart(loads = sampleLoad) }
        item {
            Text(
                "Last 7 days",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(7) { index ->
            val l = sampleLoad[index]
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l.date ?: "", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (l.activityCount == 0) "rest day" else Format.duration(l.totalDurationSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        l.trainingStressScore?.let { String.format(Locale.US, "%.0f", it) } ?: "-",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Dependency-free bar chart, in the same spirit as the existing `WeeklyDistanceChart`. */
@Composable
private fun LoadChart(loads: List<TrainingLoadDto>) {
    val max = loads.mapNotNull { it.trainingStressScore }.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Last 90 days", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().height(90.dp).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                loads.reversed().forEach { d ->
                    val fraction = ((d.trainingStressScore ?: 0.0) / max).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp),
                            ),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------- variant 3: plain

/**
 * The third option: no sports-science vocabulary, and no chart of a metric the
 * user did not ask for. One sentence, and the week they just did.
 */
@Preview(name = "3 - plain", heightDp = 900, showBackground = true)
@Composable
private fun LoadTabMockupPlain() = FPClientTheme {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "4 activities, 3 h 40 min, 28 km. That is 12% more than last week.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("You are carrying some fatigue", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "High fatigue detected. Consider taking a rest day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item { DistanceBars(weeks = sampleLoad.chunked(7).takeLast(8).reversed()) }
    }
}

/** Weekly distance bars, each labelled with its change against the week before. */
@Composable
private fun DistanceBars(weeks: List<List<TrainingLoadDto>>) {
    val distances = weeks.map { it.sumOf { d -> d.totalDistanceMeters } }
    val max = distances.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Last 8 weeks", style = MaterialTheme.typography.titleSmall)
            distances.forEachIndexed { i, d ->
                val previous = distances.getOrNull(i - 1)
                val delta = if (previous != null && previous > 0) {
                    val pct = ((d - previous) / previous * 100).toInt()
                    if (pct > 0) "+$pct%" else "$pct%"
                } else null
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .weight((d / max).toFloat().coerceIn(0.02f, 1f))
                            .height(10.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                    Text(
                        delta ?: "new",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

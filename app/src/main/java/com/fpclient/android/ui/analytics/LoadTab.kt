package com.fpclient.android.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.TrainingLoadDto
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format
import java.util.Locale

/**
 * The **Load** tab (Iteration: Analytics redesign, option C).
 *
 * Chosen over the alternatives after a three-way comparison on real data
 * (`docs/ANALYTICS-TAB-REDESIGN.md` §5, §8.1): the two rejected candidates were the
 * status quo (a flat list of 90 daily rows labelled *Stress* / *Fitness (CTL)* /
 * *Fatigue (ATL)* / *Form*, which the brief described as "data nonsense") and a
 * jargon-free variant that dropped the numbers entirely. This one keeps every number
 * and makes them legible.
 *
 * What changed, and why it is the difference:
 *  - a plain-English verdict leads, from the server's own `description` when present
 *    and from [TrainingLoadMath.describeBalance] otherwise;
 *  - a one-line glossary defines fitness/fatigue/form, which is the part that makes
 *    the three numbers below it readable;
 *  - the acronyms are gone from the labels, so nothing on screen is unexplained;
 *  - 90 rows become a chart plus the last seven days, because scrolling 90 cards is
 *    not a view of anything.
 *
 * All arithmetic lives in [TrainingLoadMath] (unit-tested); this file only draws.
 */
@Composable
fun TrainingLoadTab(loads: List<TrainingLoadDto>) {
    if (loads.isEmpty()) return EmptyState(title = "No training-load data yet")
    val latest = loads.maxByOrNull { it.date.orEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("How you are doing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        TrainingLoadMath.describeBalance(
                            latest?.trainingStressBalance,
                            latest?.chronicTrainingLoad,
                            latest?.acuteTrainingLoad,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // The single line that makes the three numbers below legible.
                    Text(
                        TrainingLoadMath.BALANCE_GLOSSARY,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    StatRow(
                        listOf(
                            "Fitness" to TrainingLoadMath.whole(latest?.chronicTrainingLoad),
                            "Fatigue" to TrainingLoadMath.whole(latest?.acuteTrainingLoad),
                            "Form" to (latest?.trainingStressBalance?.let {
                                String.format(Locale.US, "%+.0f", it)
                            } ?: "—"),
                        ),
                    )
                }
            }
        }
        item { LoadStressChart(loads) }
        item {
            Text(
                "Last 7 days",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(7) { index ->
            val day = loads.getOrNull(loads.lastIndex - index) ?: return@items
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    // The three columns were butted up against each other, and the middle
                    // one was bodyMedium + Medium weight against two bodySmall values, so
                    // the duration read as a headline. One weight, and real spacing.
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = day.date ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (day.activityCount == 0) "rest day"
                        else Format.duration(day.totalDurationSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = TrainingLoadMath.whole(day.trainingStressScore),
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
private fun LoadStressChart(loads: List<TrainingLoadDto>) {
    val peak = TrainingLoadMath.peakStress(loads)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Last ${loads.size} days", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth().height(90.dp).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                loads.forEach { day ->
                    val fraction = ((day.trainingStressScore ?: 0.0) / peak).toFloat()
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

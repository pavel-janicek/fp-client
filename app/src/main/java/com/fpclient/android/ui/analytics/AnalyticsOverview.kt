package com.fpclient.android.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.AchievementDto
import com.fpclient.android.data.dto.ActivitySummaryPeriodDto
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format

@Composable
internal fun OverviewContent(
    ui: AnalyticsViewModel.UiState,
    unitSystem: String,
    onOpenRecords: () -> Unit = {},
) {
    val dash = ui.dashboard ?: return EmptyState(title = "No analytics yet")
    // Same outer rhythm as the other Analytics tabs, which were padded first.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            WeeklyDistanceChart(weeks = ui.weekly.takeLast(12))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    val w = dash.currentWeekSummary
                    StatRow(
                        listOf(
                            "Activities" to (w?.activityCount ?: 0).toString(),
                            "Time" to Format.duration(w?.totalDurationSeconds),
                            "Distance" to Format.distanceShort(w?.totalDistanceMeters),
                        ),
                    )
                    Text(
                        "Elev. gain ${Format.elevation(w?.totalElevationGainMeters, unitSystem)} · PRs ${w?.personalRecordsSet ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStatCard(
                    "Personal records",
                    dash.personalRecordsCount.toString(),
                    Modifier.weight(1f),
                    // A count that looks like a link and is not is worse than no tile:
                    // this one now opens the full list.
                    onClick = onOpenRecords,
                )
                MiniStatCard("Achievements", dash.achievementsCount.toString(), Modifier.weight(1f))
                MiniStatCard(
                    "Form",
                    dash.formStatus?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—",
                    Modifier.weight(1f),
                )
            }
        }
        // "You have 8 achievements" is only half an answer — this is the other half.
        // The list was already being fetched and thrown away; now it is shown.
        if (ui.achievements.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "Achievements (${ui.achievements.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ui.achievements.forEach { achievement ->
                            AchievementRow(achievement)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One earned achievement. The name is the server's, and the description says what it took —
 * both of which were already on the wire and unused.
 */
@Composable
private fun AchievementRow(achievement: AchievementDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = achievement.name ?: achievement.achievementType ?: "Achievement",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            achievement.earnedAt?.let {
                Text(
                    text = Format.relative(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        achievement.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Simple dependency-free bar chart of distance per week over the last N weeks. */
@Composable
private fun WeeklyDistanceChart(weeks: List<ActivitySummaryPeriodDto>) {
    if (weeks.none { (it.totalDistanceMeters ?: 0.0) > 0.0 }) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Last ${weeks.size} weeks", style = MaterialTheme.typography.titleMedium)
            val maxDistance = weeks.maxOf { it.totalDistanceMeters ?: 0.0 }.coerceAtLeast(1.0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weeks.forEach { week ->
                    val fraction = ((week.totalDistanceMeters ?: 0.0) / maxDistance).toFloat()
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            Format.distanceShort(week.totalDistanceMeters),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceIn(0.02f, 1f))
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                ),
                        )
                    }
                }
            }
        }
    }
}

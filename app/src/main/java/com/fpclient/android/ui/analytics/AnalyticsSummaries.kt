package com.fpclient.android.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.ActivitySummaryPeriodDto
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format

@Composable
fun SummariesList(summaries: List<ActivitySummaryPeriodDto>, periodLabel: String) {
    if (summaries.isEmpty()) return EmptyState(title = "No $periodLabel summaries yet")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The numbers sat hard against the left edge and the first/last card ran into the
        // tab bar; both are a padding problem, not a layout one.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(summaries.size) { index ->
            val s = summaries[index]
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                    // A bare "2026-09-01" told the reader nothing; PeriodLabels turns the
                    // same value into "September 2026" (and a week into its date range).
                    Text(
                        text = PeriodLabels.heading(s.periodStart, s.periodType),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    StatRow(
                        listOf(
                            "Activities" to s.activityCount.toString(),
                            "Time" to Format.duration(s.totalDurationSeconds),
                            "Distance" to Format.distanceShort(s.totalDistanceMeters),
                        ),
                    )
                }
            }
        }
    }
}

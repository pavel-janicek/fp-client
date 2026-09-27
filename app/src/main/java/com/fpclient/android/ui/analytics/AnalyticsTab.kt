package com.fpclient.android.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.ActivitySummaryPeriodDto
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format

private val TABS = listOf("Overview", "Weekly", "Monthly", "Yearly", "Load")

@Composable
fun AnalyticsTabContent(
    container: AppContainer,
    unitSystem: String,
    modifier: Modifier = Modifier,
) {
    val vm: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(container))
    val ui by vm.ui.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
        ) {
            TABS.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when {
            ui.loading -> LoadingIndicator()
            ui.error != null -> ErrorState(message = ui.error, onRetry = vm::refresh)
            else -> when (tab) {
                0 -> OverviewContent(ui, unitSystem)
                1 -> SummariesList(ui.weekly.reversed(), "week")
                2 -> SummariesList(ui.monthly.reversed(), "month")
                3 -> SummariesList(ui.yearly.reversed(), "year")
                else -> TrainingLoadTab(ui.trainingLoad)
            }
        }
    }
}

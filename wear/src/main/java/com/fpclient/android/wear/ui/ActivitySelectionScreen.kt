package com.fpclient.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutActivityUsageStore
import com.fpclient.android.wear.recording.WorkoutRecordingController

@Composable
fun ActivitySelectionScreen(
    onSelectActivity: (WorkoutActivityType) -> Unit,
    onRequestSensorPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val usageStore = remember { WorkoutActivityUsageStore(context) }
    val sortedTypes = remember { usageStore.sortedTypes() }
    val missingPermissions = remember { WorkoutRecordingController.missingPermissions(context) }

    Scaffold(
        modifier = modifier.background(Color.Black),
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    text = "Workout",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                )
            }
            if (missingPermissions.isNotEmpty()) {
                item {
                    Chip(
                        onClick = onRequestSensorPermissions,
                        label = {
                            Text(
                                text = "Grant permissions",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            sortedTypes.forEach { type ->
                item {
                    Chip(
                        onClick = {
                            usageStore.recordUsage(type)
                            onSelectActivity(type)
                        },
                        label = {
                            Text(
                                text = type.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

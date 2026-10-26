package com.fpclient.android.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.ManualActivityRequest
import com.fpclient.android.util.Format
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
internal fun DurationFields(
    hours: String,
    minutes: String,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = hours, onValueChange = { onHours(it.filter { c -> c.isDigit() }) },
            label = { Text("Hours") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = minutes, onValueChange = { onMinutes(it.filter { c -> c.isDigit() }) },
            label = { Text("Minutes") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun MetricFields(
    distanceKm: String,
    elevationM: String,
    onDistance: (String) -> Unit,
    onElevation: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = distanceKm, onValueChange = onDistance,
            label = { Text("Distance (km)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = elevationM, onValueChange = onElevation,
            label = { Text("Elev. gain (m)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun CreateButton(
    vm: CreateViewModel,
    type: String,
    title: String,
    dateText: String,
    timeText: String,
    hours: String,
    minutes: String,
    distanceKm: String,
    elevationM: String,
    indoor: Boolean,
) {
    val ui by vm.ui.collectAsState()
    val durationSeconds = ((hours.toLongOrNull() ?: 0L) * 3600) + ((minutes.toLongOrNull() ?: 0L) * 60)
    val startDateTime = runCatching {
        LocalDateTime.parse("$dateText${timeText.takeIf { it.isNotBlank() }?.let { "T$it" } ?: "T12:00"}")
    }.getOrElse { LocalDateTime.now() }

    val error = ui.error
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error)
    }
    Button(
        onClick = {
            vm.createManual(
                ManualActivityRequest(
                    activityType = type,
                    title = title.ifBlank { null },
                    startedAt = Format.toInstantString(startDateTime, ZoneId.systemDefault()),
                    timezone = ZoneId.systemDefault().id,
                    durationSeconds = durationSeconds,
                    distanceMeters = distanceKm.toDoubleOrNull()?.times(1000),
                    elevationGainMeters = elevationM.toDoubleOrNull(),
                    indoor = indoor,
                    visibility = vm.defaultVisibility,
                ),
            )
        },
        enabled = !ui.busy && durationSeconds > 0 && dateText.isNotBlank() && timeText.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Text(if (ui.busy) "Saving…" else "Create activity")
    }
}

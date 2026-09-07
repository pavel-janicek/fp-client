package com.fpclient.android.ui.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.data.dto.ManualActivityRequest
import com.fpclient.android.util.Format
import com.fpclient.android.util.TextLimits
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualForm(vm: CreateViewModel) {
    var type by rememberSaveable { mutableStateOf("RUN") }
    var title by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var timeText by rememberSaveable { mutableStateOf(LocalTime.now().withSecond(0).toString().substring(0, 5)) }
    var hours by rememberSaveable { mutableStateOf("1") }
    var minutes by rememberSaveable { mutableStateOf("0") }
    var distanceKm by rememberSaveable { mutableStateOf("") }
    var elevationM by rememberSaveable { mutableStateOf("") }
    var indoor by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Activity type", style = MaterialTheme.typography.labelLarge)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("RUN", "RIDE", "HIKE", "WALK", "SWIM", "WORKOUT").forEach { t ->
                FilterChip(
                    selected = type == t,
                    onClick = {
                        type = t
                        indoor = t == "WORKOUT"
                    },
                    label = { Text("${ActivityTypes.icon(t)} ${t.lowercase()}") },
                )
            }
        }
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(TextLimits.ACTIVITY_TITLE) }, label = { Text("Title (optional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        // Date is picked from a calendar dialog; the read-only field only displays it, so
        // the value can always be parsed as YYYY-MM-DD when the activity is created.
        Box {
            OutlinedTextField(
                value = dateText, onValueChange = {},
                label = { Text("Date") }, singleLine = true, readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Pick date")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // Transparent overlay so a tap anywhere on the field opens the picker.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { showDatePicker = true },
            )
        }
        OutlinedTextField(
            value = timeText, onValueChange = { timeText = it },
            label = { Text("Start time (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        DurationFields(hours, minutes, onHours = { hours = it }, onMinutes = { minutes = it })
        MetricFields(distanceKm, elevationM, onDistance = { distanceKm = it }, onElevation = { elevationM = it })
        FilterChip(selected = indoor, onClick = { indoor = !indoor }, label = { Text("Indoor") })
        CreateButton(vm, type, title, dateText, timeText, hours, minutes, distanceKm, elevationM, indoor)

        if (showDatePicker) {
            // Material3 DatePicker works in UTC millis; convert through UTC to avoid
            // timezone drift on the selected LocalDate.
            val initialMillis = remember(dateText) {
                runCatching {
                    LocalDate.parse(dateText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }.getOrElse { System.currentTimeMillis() }
            }
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            dateText = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }
    }
}

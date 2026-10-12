package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.body.BodyProfile
import com.fpclient.android.data.body.BodyProfileStore
import com.fpclient.android.data.body.Sex

/**
 * "Calorie recording" card: an optional opt-in that, once switched on, reveals the body metrics
 * (height / weight / age / sex) a calorie estimate needs. Everything is stored **only on this
 * phone** through the encrypted [BodyProfileStore] and is never uploaded; turning the switch off
 * wipes the stored values. The estimate itself is computed on-device later — this card only
 * collects and persists the inputs, so it stays useful regardless of when calorie support lands.
 */
@Composable
internal fun CalorieRecordingCard(store: BodyProfileStore) {
    val profile by store.profile.collectAsState()

    // Editable text mirrors the stored profile. Keyed on `profile` so it re-seeds after a save
    // (round-tripping 70 -> "70") or after an opt-out wipe, while staying stable while typing.
    var heightText by remember(profile) { mutableStateOf(BodyProfile.inputText(profile.heightCm)) }
    var weightText by remember(profile) { mutableStateOf(BodyProfile.inputText(profile.weightKg)) }
    var ageText by remember(profile) { mutableStateOf(profile.ageYears?.toString() ?: "") }
    var sex by remember(profile) { mutableStateOf(profile.sex) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Calorie recording", style = MaterialTheme.typography.titleSmall)
            Text(
                "Optionally record calories for your workouts. This needs your body metrics, " +
                    "which are calculated on this phone only and never uploaded. Turn this off " +
                    "at any time to delete them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = profile.optIn,
                    onCheckedChange = { checked ->
                        // Switching on seeds from what's already stored; switching off wipes it all.
                        if (checked) store.save(store.current().copy(optIn = true)) else store.clear()
                    },
                )
                Text(
                    "I voluntarily provide my body data solely for on-device calorie calculations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (profile.optIn) {
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Text(
                    "Age and sex only sharpen the estimate — they are optional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it },
                    label = { Text("Age (years, optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Sex.entries.forEach { option ->
                        FilterChip(
                            selected = sex == option,
                            onClick = { sex = option },
                            label = { Text(option.label()) },
                        )
                    }
                }
                val complete =
                    BodyProfile.sanitizeHeightCm(heightText) != null &&
                        BodyProfile.sanitizeWeightKg(weightText) != null
                Text(
                    if (complete)
                        "Height and weight are set."
                    else
                        "Enter at least a height and a weight — a calorie estimate needs both.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        store.save(
                            BodyProfile(
                                optIn = true,
                                sex = sex,
                                heightCm = BodyProfile.sanitizeHeightCm(heightText),
                                weightKg = BodyProfile.sanitizeWeightKg(weightText),
                                ageYears = BodyProfile.sanitizeAgeYears(ageText),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Save body data") }
            }
        }
    }
}

/** Human-readable label for a [Sex] chip. */
private fun Sex.label(): String = when (this) {
    Sex.FEMALE -> "Female"
    Sex.MALE -> "Male"
    Sex.UNSPECIFIED -> "Unspecified"
}


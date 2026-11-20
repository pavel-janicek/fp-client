package com.fpclient.android.ui.auth

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ServerSetupContent(
    busy: Boolean,
    hint: String?,
    onSave: (String) -> Unit,
    onSkip: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    initialUrl: String? = null,
) {
    // null = no quick-pick selected (custom text field active), non-null = quick-pick URL
    var serverUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    var customMode by rememberSaveable {
        mutableStateOf(initialUrl != null && initialUrl != "https://fitpub.social" && initialUrl != "https://makni.cz")
    }
    val quickPicks = listOf("https://fitpub.social" to "fitpub.social", "https://makni.cz" to "makni.cz")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🏃", style = MaterialTheme.typography.displayLarge)
        Text(
            "FP Client",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Connect to your federated fitness instance to follow friends, upload activities and track your training.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickPicks.forEach { (url, label) ->
                val selected = !customMode && serverUrl == url
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = !busy) {
                            serverUrl = url
                            customMode = false
                            onSave(url)
                        }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🌐", style = MaterialTheme.typography.titleMedium)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (customMode) 2.dp else 1.dp,
                        color = if (customMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = !busy) {
                        customMode = true
                        serverUrl = ""
                    }
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("✏️", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Vlastní",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (customMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (customMode) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Instance URL") },
                placeholder = { Text("https://my-fitpub-server.example") },
                leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = { onSave(serverUrl) },
                enabled = serverUrl.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    Text("Connecting…")
                } else {
                    Text("Connect")
                }
            }
        } else if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp)) {
                Text("Skip for now")
            }
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
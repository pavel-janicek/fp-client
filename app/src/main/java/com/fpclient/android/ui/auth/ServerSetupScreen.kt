package com.fpclient.android.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
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
    var serverUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
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

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Instance URL") },
            placeholder = { Text("https://fitpub.social") },
            leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        Text(
            "e.g. https://fitpub.social or your own self-hosted server",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text("Connecting…")
            } else {
                Text("Connect")
            }
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onSave("https://fitpub.social") },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("fitpub.social") }
            OutlinedButton(
                onClick = { onSave("https://makni.cz") },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("makni.cz") }
        }
        if (onSkip != null) {
            TextButton(onClick = onSkip) {
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
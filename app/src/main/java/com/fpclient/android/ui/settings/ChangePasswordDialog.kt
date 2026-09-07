package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fpclient.android.AppContainer
import kotlinx.coroutines.runBlocking

@Composable
fun ChangePasswordDialog(container: AppContainer, onDismiss: () -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showCurrent by rememberSaveable { mutableStateOf(false) }
    var showNew by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(
                                if (showCurrent) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showCurrent) "Hide password" else "Show password",
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it.take(100) },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showNew) "Hide password" else "Show password",
                            )
                        }
                    },
                )
                if (error != null) {
                    Text(
                        error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.isNotBlank() && new.length >= 8 && error == null,
                onClick = {
                    val result = runBlocking { container.userRepository.changePassword(current, new) }
                    when (result) {
                        is com.fpclient.android.data.network.ApiResult.Success -> onDismiss()
                        is com.fpclient.android.data.network.ApiResult.Error -> error = result.message
                    }
                },
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

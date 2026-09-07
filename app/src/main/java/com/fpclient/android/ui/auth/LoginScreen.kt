package com.fpclient.android.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LoginContent(
    busy: Boolean,
    error: String?,
    serverUrl: String,
    onLogin: (username: String, password: String) -> Unit,
    onOpenRegister: () -> Unit,
    onOpenPasswordReset: () -> Unit,
    onChangeServer: () -> Unit,
    onBrowseAsGuest: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val host = serverUrl.removePrefix("https://").removePrefix("http://")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Log in", style = MaterialTheme.typography.headlineMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                Icons.Outlined.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp).size(16.dp),
            )
            Text(
                host.ifBlank { "no instance configured" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        OutlinedButton(
            onClick = onChangeServer,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Change instance", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "Welcome back to your training",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username or email") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = { onLogin(username, password) },
            enabled = username.isNotBlank() && password.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                Text("Signing in…")
            } else {
                Text("Sign in")
            }
        }
        TextButton(onClick = onOpenPasswordReset, modifier = Modifier.padding(top = 4.dp)) {
            Text("Forgot password?")
        }
        TextButton(onClick = onOpenRegister) {
            Text("New here? Create an account")
        }
        TextButton(onClick = onBrowseAsGuest) {
            Text("Browse public timeline without an account")
        }
    }
}
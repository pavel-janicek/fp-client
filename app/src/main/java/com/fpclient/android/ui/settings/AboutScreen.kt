package com.fpclient.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fpclient.android.BuildConfig
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.UpdateCheckResult
import com.fpclient.android.data.network.UpdateChecker
import kotlinx.coroutines.launch

/** Manual update-check UI state. Only ever entered by an explicit user tap — no background checks. */
private sealed interface UpdateCheckUiState {
    object Idle : UpdateCheckUiState
    object Checking : UpdateCheckUiState
    data class Checked(val result: UpdateCheckResult) : UpdateCheckUiState
    data class Failed(val message: String) : UpdateCheckUiState
}

/** Play Store listing for the app — updates on Play builds are installed by the Play Store. */
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.fpclient.android"

/** F-Droid package page — updates on F-Droid builds are installed by the F-Droid client. */
private const val FDROID_PACKAGE_URL = "https://f-droid.org/en/packages/com.fpclient.android/"

/**
 * Static "About" information: app version, contact/community links, bug
 * reporting guidance, and the HTTP User-Agent that instance administrators
 * can use to identify the app in their server logs. The version is always
 * read from [BuildConfig] so it can never drift from the shipped APK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // Compose-observable state; read into a local (`val state`) inside the card because
    // smart casts are unsupported on delegated (remember) properties.
    val updateState = remember { mutableStateOf<UpdateCheckUiState>(UpdateCheckUiState.Idle) }
    val userAgent = "FP-Client/${BuildConfig.VERSION_NAME}"
    val matrixRoomUrl = "https://matrix.to/#/#fitpub-users:matrix.org"
    val issuesUrl = "https://github.com/pavel-janicek/fp-client/issues"
    val projectUrl = "https://github.com/pavel-janicek/fp-client"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("FP Client", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Unofficial client for self-hosted FitPub instances.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Updates", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Check whether a newer version of FP Client is available. " +
                            "Updates are installed by your app store — the app never installs itself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    val state = updateState.value
                    when (state) {
                        is UpdateCheckUiState.Checked -> {
                            Text(
                                if (state.result.updateAvailable)
                                    "Version ${state.result.latestVersion} is available — you have ${BuildConfig.VERSION_NAME}."
                                else
                                    "You're up to date — ${BuildConfig.VERSION_NAME} is the latest version.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (state.result.updateAvailable) {
                                Text(
                                    "Open the app store you installed FP Client from and tap Update:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(PLAY_STORE_URL) },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) { Text("Play Store") }
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(FDROID_PACKAGE_URL) },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) { Text("F-Droid") }
                                if (!state.result.releaseUrl.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { uriHandler.openUri(state.result.releaseUrl) },
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) { Text("View release notes") }
                                }
                            }
                        }
                        is UpdateCheckUiState.Failed -> {
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        else -> Unit
                    }
                    Button(
                        enabled = state !is UpdateCheckUiState.Checking,
                        onClick = {
                            scope.launch {
                                updateState.value = UpdateCheckUiState.Checking
                                when (val r = UpdateChecker.check()) {
                                    is ApiResult.Success -> updateState.value = UpdateCheckUiState.Checked(r.data)
                                    is ApiResult.Error -> updateState.value = UpdateCheckUiState.Failed(
                                        r.message ?: "Couldn't check for updates.",
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(if (state is UpdateCheckUiState.Checking) "Checking…" else "Check for updates")
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Contact", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The author is an active member of the FitPub Matrix server — " +
                            "you can reach him in the \"FitPub Users\" room:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        matrixRoomUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { uriHandler.openUri(matrixRoomUrl) },
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Report a bug", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The best way to report a bug is under GitHub Issues:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        issuesUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { uriHandler.openUri(issuesUrl) },
                    )
                    Text(
                        "When reporting a bug, please include the app version (shown above).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("For instance administrators", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "This app identifies itself in every HTTP request with the User-Agent header:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        userAgent,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Map tile requests (osmdroid) use the same User-Agent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Project", style = MaterialTheme.typography.titleSmall)
                    Text(
                        projectUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { uriHandler.openUri(projectUrl) },
                    )
                    Text(
                        "Map data © OpenStreetMap contributors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

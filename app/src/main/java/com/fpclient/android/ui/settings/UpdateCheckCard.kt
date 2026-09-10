package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
 * The "Updates" card: a manual "Check for updates" button plus the outcome
 * (up-to-date message, or the new version with store / release-notes links).
 * Shared by the About screen and the Settings screen so both entry points
 * behave identically. The check is always user-initiated and installs
 * nothing itself — updates stay in the user's app store (F-Droid / Play policy).
 */
@Composable
internal fun UpdateCheckCard() {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // Compose-observable state; read into a local (`val state`) inside the card because
    // smart casts are unsupported on delegated (remember) properties.
    val updateState = remember { mutableStateOf<UpdateCheckUiState>(UpdateCheckUiState.Idle) }
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
}

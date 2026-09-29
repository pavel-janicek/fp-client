package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Changing the account e-mail address.
 *
 * The server runs this as a two-address handshake: `start` mails a code to the **new** address
 * and only moves the account once `verify` confirms it, so the old address keeps working until
 * then. The screen mirrors that — it opens on the pending state if there is one, and never
 * asks for a code that was never sent.
 */
class EmailChangeViewModel(private val users: UserRepository) : ViewModel() {

    data class UiState(
        val currentEmail: String? = null,
        /** The pending new address from the server, when a change is in flight. */
        val pendingEmail: String? = null,
        val newEmail: String = "",
        val code: String = "",
        val loading: Boolean = true,
        val busy: Boolean = false,
        val error: String? = null,
        /** Set when the change completed, so the screen can confirm instead of resetting. */
        val done: Boolean = false,
    ) {
        val hasPending: Boolean get() = pendingEmail != null
        val canSend: Boolean
            get() = !busy && newEmail.isNotBlank() && newEmail.contains("@")
        val canVerify: Boolean get() = !busy && code.isNotBlank()
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            when (val r = users.emailChangeStatus()) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    loading = false,
                    pendingEmail = r.data.newEmail,
                    error = null,
                )
                is ApiResult.Error -> _ui.value = _ui.value.copy(loading = false, error = r.message)
            }
            // The account's own address comes from the profile, not from the change status.
            when (val p = users.profile("me")) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(currentEmail = p.data.email)
                else -> Unit
            }
        }
    }

    fun onNewEmailChange(value: String) {
        _ui.value = _ui.value.copy(newEmail = value, error = null)
    }

    fun onCodeChange(value: String) {
        // Digits only: the server's pattern is `^\d+$`, so anything else is a guaranteed 400.
        _ui.value = _ui.value.copy(code = value.filter { it.isDigit() }, error = null)
    }

    fun start() {
        val state = _ui.value
        if (!state.canSend) return
        viewModelScope.launch {
            _ui.value = state.copy(busy = true, error = null)
            when (val r = users.startEmailChange(state.newEmail)) {
                is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(busy = false, newEmail = "")
                    refresh()
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(busy = false, error = r.message)
            }
        }
    }

    fun verify() {
        val state = _ui.value
        if (!state.canVerify) return
        viewModelScope.launch {
            _ui.value = state.copy(busy = true, error = null)
            when (val r = users.verifyEmailChange(state.code)) {
                is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(busy = false, code = "", done = true)
                    refresh()
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(busy = false, error = r.message)
            }
        }
    }

    fun resend() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            when (val r = users.resendEmailChange()) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(busy = false, error = null)
                is ApiResult.Error -> _ui.value = _ui.value.copy(busy = false, error = r.message)
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            when (val r = users.cancelEmailChange()) {
                is ApiResult.Success -> {
                    _ui.value = _ui.value.copy(busy = false, code = "", newEmail = "")
                    refresh()
                }
                is ApiResult.Error -> _ui.value = _ui.value.copy(busy = false, error = r.message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { EmailChangeViewModel(container.userRepository) }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailChangeScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: EmailChangeViewModel = viewModel(factory = EmailChangeViewModel.factory(container))
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change email") },
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
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Your account address: ${ui.currentEmail ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (ui.done) {
                DoneCard(ui, onBack)
            } else if (ui.hasPending) {
                PendingCard(ui, vm)
            } else {
                OutlinedTextField(
                    value = ui.newEmail,
                    onValueChange = vm::onNewEmailChange,
                    label = { Text("New email address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.start() },
                    enabled = ui.canSend,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send verification code") }
            }
            ui.error?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PendingCard(ui: EmailChangeViewModel.UiState, vm: EmailChangeViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Waiting for confirmation", style = MaterialTheme.typography.titleMedium)
            Text(
                "A code was sent to ${ui.pendingEmail}. Your current address keeps working " +
                    "until the new one is confirmed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedTextField(
                value = ui.code,
                onValueChange = vm::onCodeChange,
                label = { Text("Verification code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { vm.verify() },
                    enabled = ui.canVerify,
                    modifier = Modifier.weight(1f),
                ) { Text("Confirm") }
                OutlinedButton(
                    onClick = { vm.resend() },
                    enabled = !ui.busy,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) { Text("Resend code") }
            }
            TextButton(
                onClick = { vm.cancel() },
                enabled = !ui.busy,
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Cancel this change") }
        }
    }
}

@Composable
private fun DoneCard(ui: EmailChangeViewModel.UiState, onBack: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Email changed", style = MaterialTheme.typography.titleMedium)
            Text(
                "Your account now uses ${ui.currentEmail ?: "the new address"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Done") }
        }
    }
}


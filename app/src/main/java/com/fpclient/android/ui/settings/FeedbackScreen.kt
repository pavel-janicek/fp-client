package com.fpclient.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.FeedbackSubmissionRequest
import com.fpclient.android.data.dto.FeedbackTopics
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.FeedbackRepository
import com.fpclient.android.util.TextLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Feedback to the instance admins, reached from the Me tab.
 *
 * Sign-in only — the server's security config puts `/api/web/feedback` behind
 * authentication — so this screen is only reachable from the signed-in profile.
 */
class FeedbackViewModel(private val repository: FeedbackRepository) : ViewModel() {

    data class UiState(
        val topic: String = FeedbackTopics.BUG_REPORT,
        val message: String = "",
        val replyAllowed: Boolean = false,
        val sending: Boolean = false,
        val error: String? = null,
        /** Set once the server has accepted the feedback. */
        val sentId: String? = null,
    ) {
        val canSend: Boolean
            get() = message.isNotBlank() && !sending
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun onTopicChange(topic: String) {
        _ui.value = _ui.value.copy(topic = topic)
    }

    fun onMessageChange(value: String) {
        // Capped to the server's limit so the request can never be rejected for length.
        _ui.value = _ui.value.copy(message = value.take(TextLimits.FEEDBACK_MESSAGE))
    }

    fun onReplyAllowedChange(value: Boolean) {
        _ui.value = _ui.value.copy(replyAllowed = value)
    }

    fun send() {
        val state = _ui.value
        if (!state.canSend) return
        viewModelScope.launch {
            _ui.value = state.copy(sending = true, error = null)
            when (val r = repository.submit(state.toRequest())) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(
                    sending = false,
                    sentId = r.data.id ?: "sent",
                )
                is ApiResult.Error -> _ui.value = _ui.value.copy(sending = false, error = r.message)
            }
        }
    }

    /** Starts a second, unrelated report without leaving the screen. */
    fun sendAnother() {
        _ui.value = UiState()
    }

    private fun UiState.toRequest() = FeedbackSubmissionRequest(
        topic = topic,
        message = message.trim(),
        replyAllowed = replyAllowed,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FeedbackViewModel(container.feedbackRepository) }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: FeedbackViewModel = viewModel(factory = FeedbackViewModel.factory(container))
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send feedback") },
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
            if (ui.sentId != null) {
                SentCard(vm, onBack)
            } else {
                Text(
                    "The admins of this instance receive your name, username, topic and " +
                        "message. Feedback stays on this instance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Topic", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackTopics.ALL.forEach { topic ->
                        FilterChip(
                            selected = ui.topic == topic,
                            onClick = { vm.onTopicChange(topic) },
                            label = { Text(FeedbackTopics.label(topic)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = ui.message,
                    onValueChange = vm::onMessageChange,
                    label = { Text("Message") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("${ui.message.length} / ${TextLimits.FEEDBACK_MESSAGE}")
                    },
                )
                ReplySwitch(ui, vm)
                ui.error?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { vm.send() },
                    enabled = ui.canSend,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ui.sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text("Send feedback")
                }
            }
        }
    }
}

@Composable
private fun ReplySwitch(ui: FeedbackViewModel.UiState, vm: FeedbackViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Allow replies by email", style = MaterialTheme.typography.bodyLarge)
                Text(
                    // Stated before the choice, because this is what actually changes: the
                    // server only exposes the account's address when it is granted.
                    "Gives instance admins your account's current email address so they can " +
                        "reply from their own email client. Without it they still receive " +
                        "your feedback, just without a way to answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = ui.replyAllowed,
                onCheckedChange = vm::onReplyAllowedChange,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SentCard(vm: FeedbackViewModel, onBack: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Thanks — feedback sent", style = MaterialTheme.typography.titleMedium)
            Text(
                "The instance admins can now read it. You will get a notification if they " +
                    "reply and you allowed email replies.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = { vm.sendAnother() }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Send more feedback")
            }
            TextButton(onClick = onBack) { Text("Done") }
        }
    }
}

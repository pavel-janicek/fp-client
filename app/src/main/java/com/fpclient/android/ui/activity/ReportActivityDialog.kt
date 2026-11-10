package com.fpclient.android.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fpclient.android.data.dto.AbuseReportRequest

/**
 * Report an activity (or, with another [title], a profile) to this instance's administrators. The wording follows the web's
 * report dialog (`moderation/report-dialog.html`): who receives the report and who doesn't.
 * [onSend] answers null on success, else the message to show; [onSent] then closes the dialog.
 */
@Composable
fun ReportActivityDialog(
    title: String = "Report this activity",
    onDismiss: () -> Unit,
    onSend: (reason: String, onDone: (String?) -> Unit) -> Unit,
    onSent: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Your instance's administrators receive your name, username and reason. " +
                        "The report is not sent to the reported person or their server.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(AbuseReportRequest.MAX_REASON); error = null },
                    label = { Text("Reason") },
                    supportingText = { Text("${reason.length} / ${AbuseReportRequest.MAX_REASON}") },
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(top = 12.dp),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reason.isNotBlank() && !sending,
                onClick = {
                    sending = true
                    onSend(reason) { message ->
                        sending = false
                        if (message == null) onSent() else error = message
                    }
                },
            ) { Text(if (sending) "Sending…" else "Send report") }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

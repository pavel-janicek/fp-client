package com.fpclient.android.ui.create

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.util.TextLimits

@Composable
fun UploadForm(ui: CreateViewModel.UiState, vm: CreateViewModel, sharedUri: Uri? = null) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf(ActivityVisibilities.PUBLIC) }
    var pickedName by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedUri by rememberSaveable { mutableStateOf<Uri?>(sharedUri) }
    val context = LocalContext.current

    // File arriving through the share sheet / "Open with": pre-select it and resolve
    // its display name for the button label.
    LaunchedEffect(sharedUri) {
        if (sharedUri != null) pickedName = resolveDisplayName(context, sharedUri) ?: "File selected"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickedName = resolveDisplayName(context, uri) ?: "File selected"
        }
    }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it.take(TextLimits.ACTIVITY_TITLE) },
        label = { Text("Title (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = description,
        onValueChange = { description = it.take(TextLimits.ACTIVITY_DESCRIPTION) },
        label = { Text("Description (optional)") },
        modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 8.dp),
    )
    Text("Visibility", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActivityVisibilities.ALL.forEach { v ->
            FilterChip(selected = visibility == v, onClick = { visibility = v }, label = { Text(v.lowercase()) })
        }
    }
    Spacer(Modifier.height(14.dp))
    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
        Text(pickedName ?: "Choose FIT / GPX / TCX file")
    }
    if (ui.error != null) {
        Text(ui.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
    Button(
        onClick = {
            pickedUri?.let {
                vm.uploadFile(context, it, title.ifBlank { null }, description.ifBlank { null }, visibility)
            }
        },
        enabled = !ui.busy && pickedUri != null,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Text(if (ui.busy) "Uploading…" else "Upload activity")
    }
}

/** Resolves the picked document's display name through the system picker. Content URIs
 * otherwise expose cryptic internal ids (e.g. "msf:2323") as their last path segment,
 * which is meaningless to the user. */
private fun resolveDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getString(index).ifBlank { null }
            } else {
                null
            }
        }
}.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { name ->
    // Ignore internal-id-looking segments ("msf:2323", "raw%3A123"); fall back to a
    // neutral label instead of showing them.
    name.isNotBlank() && !name.contains(':') && !name.contains("%3A", ignoreCase = true)
}

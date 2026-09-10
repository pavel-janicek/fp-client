package com.fpclient.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fpclient.android.AppContainer
import com.fpclient.android.data.dto.DefaultTimelines
import com.fpclient.android.data.dto.ProfileVisibilities
import com.fpclient.android.data.dto.UserUpdateRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.util.TextLimits
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val vm: EditProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = EditProfileViewModel.factory(container),
    )
    val ui by vm.ui.collectAsState()
    val appUi by appViewModel.uiState.collectAsState()

    var current by androidx.compose.runtime.remember {
        mutableStateOf<com.fpclient.android.data.dto.UserDto?>(null)
    }
    LaunchedEffect(Unit) {
        when (val r = container.userRepository.me()) {
            is ApiResult.Success -> current = r.data
            else -> Unit
        }
    }

    if (ui.done) {
        val saved = ui.saved
        if (saved != null) {
            // Explicit user action — the saved unit system becomes authoritative
            // (applied everywhere and persisted on device).
            appViewModel.onProfileSaved(saved)
        }
        onDone()
        return
    }

    var displayName by rememberSaveable(current?.displayName) {
        mutableStateOf(current?.displayName.orEmpty())
    }
    var bio by rememberSaveable(current?.bio) { mutableStateOf(current?.bio.orEmpty()) }
    var visibility by rememberSaveable(current?.profileVisibility) {
        mutableStateOf(current?.profileVisibility ?: "PUBLIC")
    }
    var timeline by rememberSaveable(current?.defaultTimeline) {
        mutableStateOf(current?.defaultTimeline ?: DefaultTimelines.FEDERATED)
    }
    var timezone by rememberSaveable(current?.timezone) {
        mutableStateOf(current?.timezone.orEmpty())
    }
    var timezoneOptions by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    }
    var showTimezoneDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var timezoneQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    LaunchedEffect(Unit) {
        when (val r = container.userRepository.timezones()) {
            is ApiResult.Success -> timezoneOptions = r.data
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
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
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(TextLimits.DISPLAY_NAME) },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(TextLimits.USER_BIO) },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp),
            )
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val context = androidx.compose.ui.platform.LocalContext.current
            val avatarImage = com.fpclient.android.util.UrlBuilder.avatar(
                appUi.serverUrl,
                current?.avatarUrl,
            )
            Text("Avatar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (avatarImage != null) {
                    coil.compose.AsyncImage(
                        model = avatarImage,
                        contentDescription = "Avatar",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (displayName.ifBlank { "@" }).take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            when (val r = container.userRepository.uploadAvatar(context, uri)) {
                                is ApiResult.Success -> current = r.data
                                else -> Unit
                            }
                        }
                    }
                }
                Button(onClick = { avatarPicker.launch("image/*") }) {
                    Text(if (avatarImage == null) "Upload avatar" else "Replace")
                }
                if (avatarImage != null) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            when (val r = container.userRepository.deleteAvatar()) {
                                is ApiResult.Success -> current = current?.copy(avatarUrl = null, hasUploadedAvatar = false)
                                else -> Unit
                            }
                        }
                    }) { Text("Remove") }
                }
            }
            val headerUrl = com.fpclient.android.util.UrlBuilder.avatar(
                appUi.serverUrl,
                current?.profileHeaderUrl,
            )
            Text("Profile header", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            if (headerUrl != null) {
                coil.compose.AsyncImage(
                    model = headerUrl,
                    contentDescription = "Profile header",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(top = 8.dp),
                )
            }
            val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            ) { uri ->
                if (uri != null) {
                    scope.launch {
                        when (val r = container.userRepository.uploadProfileHeader(context, uri)) {
                            is ApiResult.Success -> current = r.data
                            else -> Unit
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { imagePicker.launch("image/*") }) {
                    Text(if (headerUrl == null) "Upload header" else "Replace header")
                }
                if (headerUrl != null) {
                    androidx.compose.material3.OutlinedButton(onClick = {
                        scope.launch {
                            when (val r = container.userRepository.deleteProfileHeader()) {
                                is ApiResult.Success -> current = current?.copy(profileHeaderUrl = null)
                                else -> Unit
                            }
                        }
                    }) { Text("Remove") }
                }
            }
            Text("Profile visibility", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProfileVisibilities.ALL.forEach { v ->
                    FilterChip(selected = visibility == v, onClick = { visibility = v }, label = { Text(v.lowercase()) })
                }
            }
            Text("Default timeline", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DefaultTimelines.ALL.forEach { t ->
                    FilterChip(selected = timeline == t, onClick = { timeline = t }, label = { Text(t.lowercase()) })
                }
            }
            Text("Time zone", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
            OutlinedButton(
                onClick = { showTimezoneDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(timezone.ifBlank { "Select time zone" })
            }
            if (showTimezoneDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showTimezoneDialog = false },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showTimezoneDialog = false }) { Text("Close") }
                    },
                    title = { Text("Select time zone") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = timezoneQuery,
                                onValueChange = { timezoneQuery = it },
                                placeholder = { Text("Search…") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(340.dp).padding(top = 8.dp),
                            ) {
                                val filtered = if (timezoneQuery.isBlank()) timezoneOptions
                                    else timezoneOptions.filter { it.contains(timezoneQuery, ignoreCase = true) }
                                items(filtered.size) { i ->
                                    val zone = filtered[i]
                                    Text(
                                        zone,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                timezone = zone
                                                showTimezoneDialog = false
                                            }
                                            .padding(vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    },
                )
            }
            val error = ui.error
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            }
            Button(
                onClick = {
                    vm.save(
                        UserUpdateRequest(
                            displayName = displayName.ifBlank { null },
                            bio = bio.ifBlank { null },
                            profileVisibility = visibility,
                            defaultTimeline = timeline,
                            timezone = timezone.ifBlank { null },
                        ),
                    )
                },
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(if (ui.busy) "Saving…" else "Save changes")
            }
        }
    }
}

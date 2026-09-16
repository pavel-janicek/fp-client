package com.fpclient.android.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.util.Format
import com.fpclient.android.data.dto.ActivitySummaryDto
import com.fpclient.android.data.dto.ActorDto
import com.fpclient.android.data.dto.FollowStatusDto
import com.fpclient.android.data.dto.HeatmapResponse
import com.fpclient.android.data.dto.UserDto
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.repository.ActivityRepository
import com.fpclient.android.data.repository.UserRepository
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.ui.components.UserAvatar
import com.fpclient.android.util.ActorHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val users: UserRepository,
    private val activities: ActivityRepository,
    private val appViewModel: AppViewModel,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val user: UserDto? = null,
        val followStatus: FollowStatusDto? = null,
        val activitiesList: List<ActivitySummaryDto> = emptyList(),
        val heatmap: HeatmapResponse? = null,
        val busy: Boolean = false,
        /** True when the server refused the profile body because it is followers-only. */
        val isPrivateProfile: Boolean = false,
        /** True when the requested handle belongs to a federated (remote) user discovered via WebFinger. */
        val isRemoteProfile: Boolean = false,
        /** Username this screen was opened for; kept so actions work even without a profile body. */
        val username: String? = null,
        /** Lightweight actor profile for remote users (populated instead of [user] for federated handles). */
        val actor: ActorDto? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load(username: String) {
        val cleaned = username.trim()
        // Federated handle (@user@host where host is NOT our instance)? The local profile
        // endpoint only resolves local usernames — remote actors must be discovered via
        // the dedicated discover-remote endpoint.
        val serverDomain = ActorHandle.hostOf(appViewModel.uiState.value.serverUrl)
        val handleHost = if (ActorHandle.isFullHandle(cleaned)) {
            cleaned.removePrefix("@").substringAfter('@').takeIf { it.isNotBlank() }?.let {
                ActorHandle.hostOf("https://$it")
            }
        } else null
        val isRemote = handleHost != null && !handleHost.equals(serverDomain, ignoreCase = true)

        if (isRemote) {
            loadRemoteProfile(cleaned)
            return
        }

        val localName = if (ActorHandle.isFullHandle(cleaned)) ActorHandle.localPart(cleaned) else cleaned.removePrefix("@")
        loadLocalProfile(localName.ifBlank { cleaned })
    }

    /** Loads a remote (federated) actor discovered via WebFinger — no activities/heatmap available. */
    private fun loadRemoteProfile(handle: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                loading = true, error = null, isPrivateProfile = false,
                isRemoteProfile = true, user = null, actor = null,
                username = handle, activitiesList = emptyList(), heatmap = null,
            )
            when (val r = users.discoverRemote(handle)) {
                is ApiResult.Success ->
                    _ui.value = _ui.value.copy(loading = false, actor = r.data)
                is ApiResult.Error ->
                    _ui.value = _ui.value.copy(loading = false, error = r.message)
            }
        }
    }

    /** Loads a local user's full profile (activities, heatmap, follow status). */
    private fun loadLocalProfile(username: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                loading = true, error = null, isPrivateProfile = false, isRemoteProfile = false,
                username = username.ifBlank { null },
            )
            var user: UserDto? = null
            var error: String? = null
            when (val r = users.profile(username)) {
                is ApiResult.Success -> {
                    user = r.data
                    if (username == appViewModel.uiState.value.username || username == "me") {
                        appViewModel.onProfileLoaded(r.data)
                    }
                }
                is ApiResult.Error -> {
                    error = r.message
                    // Private accounts answer 403 ("only visible to followers"). We must NOT
                    // bail out here — the follow/request-to-follow flow depends on the
                    // follow-status endpoint which stays callable for such accounts.
                    _ui.value = _ui.value.copy(
                        isPrivateProfile = r.statusCode == 403 || r.message?.contains("only visible", ignoreCase = true) == true,
                    )
                }
            }
            _ui.value = _ui.value.copy(loading = false, user = user, error = error)
            if (user == null && !_ui.value.isPrivateProfile) return@launch

            val target = if (username == "me") appViewModel.uiState.value.username else username
            if (!target.isNullOrBlank() && target != appViewModel.uiState.value.username) {
                when (val f = users.followStatus(target)) {
                    is ApiResult.Success -> _ui.value = _ui.value.copy(followStatus = f.data)
                    else -> Unit
                }
            }
            if (user == null) return@launch

            when (val a = activities.userActivities(target.ifBlank { "me" }, page = 0, size = 20)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(activitiesList = a.data.content)
                else -> Unit
            }
            when (val h = users.heatmap(target)) {
                is ApiResult.Success -> _ui.value = _ui.value.copy(heatmap = h.data)
                else -> Unit
            }
        }
    }

    fun toggleFollow() {
        // On locked (private) profiles there is no user object — fall back to the
        // username the screen was opened for, otherwise request-to-follow breaks.
        val raw = (_ui.value.user?.username ?: _ui.value.username)?.takeIf { it.isNotBlank() && it != "me" } ?: return
        // The discover-remote response is authoritative about locality: a handle can
        // LOOK remote (host alias, port, ...), yet the actor may live on our own
        // instance — and the server's follow endpoint only accepts plain usernames
        // for local users, so same-instance handles must be collapsed first.
        val isRemote = _ui.value.isRemoteProfile && _ui.value.actor?.local != true
        val target = if (isRemote) {
            raw.trim().removePrefix("@")
        } else {
            ActorHandle.normalizeToUsername(raw, appViewModel.uiState.value.serverUrl) ?: return
        }
        val remoteFollowStatus = _ui.value.actor?.followStatus
        val status = _ui.value.followStatus
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            // An accepted follow AND a pending request are both removed via the
            // unfollow endpoint (the server cancels pending requests the same way).
            // No status yet → default to follow, matching the button label.
            val shouldUnfollow = if (isRemote) {
                remoteFollowStatus == "ACCEPTED" || remoteFollowStatus == "PENDING"
            } else {
                status != null && (status.isAccepted || status.isPending)
            }
            val result = if (shouldUnfollow) {
                users.unfollow(target)
            } else {
                users.follow(target)
            }
            // busy MUST be cleared before/when reloading — previously it stayed true on
            // success, which permanently disabled ("grayed out") the follow button.
            _ui.value = _ui.value.copy(busy = false)
            when (result) {
                is ApiResult.Success -> load(target)
                is ApiResult.Error -> _ui.value = _ui.value.copy(error = result.message)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, appViewModel: AppViewModel): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ProfileViewModel(container.userRepository, container.activityRepository, appViewModel)
                }
            }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String,
    container: AppContainer,
    appViewModel: AppViewModel,
    embedded: Boolean,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCreate: () -> Unit = {},
    onOpenFollowers: (String) -> Unit = {},
    onOpenFollowing: (String) -> Unit = {},
) {
    val vm: ProfileViewModel = viewModel(
        key = username,
        factory = ProfileViewModel.factory(container, appViewModel),
    )
    androidx.compose.runtime.LaunchedEffect(username) { vm.load(username) }
    // Re-fetch when the screen comes back to the foreground (e.g., after editing the
    // profile), so avatar/bio/timezone changes show up without an app restart.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, username) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && vm.ui.value.user != null) {
                vm.load(username)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val ui by vm.ui.collectAsState()
    val sessionState by appViewModel.uiState.collectAsState()
    val unitSystem by appViewModel.unitSystem.collectAsState()
    val serverUrl = sessionState.serverUrl
    val isMe = username == sessionState.username || username == "me"
    val remoteActor = ui.actor

    val content: @Composable (Modifier) -> Unit = { modifier ->
        when {
            ui.loading && ui.user == null && remoteActor == null -> LoadingIndicator(modifier)
            ui.isRemoteProfile && remoteActor != null -> RemoteProfileBody(
                actor = remoteActor,
                error = ui.error,
                busy = ui.busy,
                status = ui.followStatus,
                onToggleFollow = { vm.toggleFollow() },
            )
            ui.user == null && ui.isPrivateProfile -> LockedProfileBody(
                username = ui.username ?: username,
                error = ui.error,
                status = ui.followStatus,
                busy = ui.busy,
                onToggleFollow = vm::toggleFollow,
                modifier = modifier,
            )
            ui.error != null && ui.user == null -> ErrorState(message = ui.error, onRetry = { vm.load(username) }, modifier = modifier)
            else -> ProfileBody(
                ui = ui, isMe = isMe, serverUrl = serverUrl, unitSystem = unitSystem,
                onOpenActivity = onOpenActivity, onToggleFollow = vm::toggleFollow,
                onOpenFollowers = onOpenFollowers, onOpenFollowing = onOpenFollowing,
                onEditProfile = onEditProfile,
                modifier = modifier,
            )
        }
    }

    if (embedded) {
        androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize())
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                IconButton(onClick = onOpenCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "New activity")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val title = remoteActor?.fullHandle ?: ui.user?.displayName
                        Text(title ?: username.trim().let { if (it.startsWith("@")) it else "@$it" })
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isMe) {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize()) {
                content(Modifier.fillMaxSize())
            }
        }
    }
}
@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileBody(
    ui: ProfileViewModel.UiState,
    isMe: Boolean,
    serverUrl: String,
    unitSystem: String,
    onOpenActivity: (String) -> Unit,
    onToggleFollow: () -> Unit,
    onOpenFollowers: (String) -> Unit = {},
    onOpenFollowing: (String) -> Unit = {},
    onEditProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val user = ui.user ?: return
    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(avatarUrl = user.avatarUrl, displayName = user.displayName, serverUrl = serverUrl, size = 84)
                Spacer(Modifier.height(10.dp))
                Text(user.displayName ?: "", style = MaterialTheme.typography.titleLarge)
                Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!user.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    // Bios come back HTML-encoded (e.g. emoticons as &#x1F609;) — decode
                    // them so Compose renders the same characters as the web client.
                    Text(Format.decodeHtml(user.bio) ?: user.bio, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatPill("Activities", (user.activityCount ?: 0).toString(), Modifier.weight(1f))
                    StatPill(
                        "Followers",
                        (user.followersCount ?: 0).toString(),
                        Modifier.weight(1f),
                        onClick = { onOpenFollowers(user.username ?: "me") },
                    )
                    StatPill(
                        "Following",
                        (user.followingCount ?: 0).toString(),
                        Modifier.weight(1f),
                        onClick = { onOpenFollowing(user.username ?: "me") },
                    )
                }
                if (isMe) {
                    OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) { Text("Edit profile") }
                } else {
                    val status = ui.followStatus
                    val following = status?.isAccepted == true
                    Button(onClick = onToggleFollow, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                status?.isFollowRequestReceived == true -> "Respond to follow request"
                                following -> "Unfollow"
                                status?.isPending == true -> "Requested"
                                else -> "Follow"
                            },
                        )
                    }
                    if (ui.error != null) {
                        Text(
                            ui.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        val heatmap = ui.heatmap
        item {
            HeatmapCard(points = heatmap?.points.orEmpty(), bounds = heatmap?.bounds)
        }
        if (ui.activitiesList.isEmpty()) {
            item { EmptyState(title = "No activities yet") }
        } else {
            items(ui.activitiesList.size) { index ->
                val a = ui.activitiesList[index]
                Card(
                    onClick = { onOpenActivity(a.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(com.fpclient.android.data.dto.ActivityTypes.icon(a.activityType), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.padding(start = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(a.title ?: Format.uppercaseFirst(a.activityType), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${Format.date(a.startedAt)} · ${Format.distance(a.totalDistance, unitSystem)} · ${Format.duration(a.totalDurationSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimal profile card for a federated (remote) actor discovered via WebFinger. These
 * accounts don't have a local profile page — no activities or heatmap — so we show the
 * handle, display name, avatar and bio returned by the discover-remote endpoint.
 */
@Composable
private fun RemoteProfileBody(
    actor: ActorDto,
    error: String?,
    busy: Boolean,
    status: FollowStatusDto?,
    onToggleFollow: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        UserAvatar(
            avatarUrl = actor.avatarUrl,
            displayName = actor.displayName ?: actor.username,
            serverUrl = actor.domain?.let { "https://$it" } ?: "",
            size = 88,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            actor.displayName ?: actor.username ?: actor.fullHandle,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            actor.fullHandle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!actor.bioHtml.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                Format.decodeHtml(actor.bioHtml) ?: actor.bio ?: "",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!actor.domain.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "From ${actor.domain}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        val following = status?.isAccepted == true
        Button(
            onClick = onToggleFollow,
            enabled = !busy,
        ) {
            Text(
                when {
                    status?.isPending == true -> "Request sent"
                    following -> "Unfollow"
                    else -> "Request to follow"
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
@Composable
private fun LockedProfileBody(
    username: String,
    error: String?,
    status: FollowStatusDto?,
    busy: Boolean,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = "Private profile",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("@$username keeps their profile private", style = MaterialTheme.typography.titleMedium)
        Text(
            "Send a follow request to see their activities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val following = status?.isAccepted == true
        Button(
            onClick = onToggleFollow,
            enabled = !busy,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                when {
                    status?.isPending == true -> "Request sent"
                    following -> "Unfollow"
                    else -> "Request to follow"
                },
            )
        }
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

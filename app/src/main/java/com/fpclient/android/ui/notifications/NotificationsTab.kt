package com.fpclient.android.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.dto.NotificationTypes
import com.fpclient.android.notifications.PushNotifications
import com.fpclient.android.ui.components.EmptyState
import com.fpclient.android.ui.components.ErrorState
import com.fpclient.android.ui.components.LoadingIndicator
import com.fpclient.android.util.Format
import com.fpclient.android.notifications.NotificationText

@Composable
fun NotificationsTabContent(
    viewModel: NotificationsViewModel,
    container: AppContainer,
    onOpenActivity: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsState()

    // Opening this tab is the "already read" signal for the background summary notification:
    // the user is looking at the very list it summarized (Iteration 8f).
    val context = LocalContext.current
    LaunchedEffect(Unit) { PushNotifications.cancel(context) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activity", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Surface(
                onClick = { viewModel.markAllRead() },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Mark all read", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        when {
            ui.loading -> LoadingIndicator()
            ui.error != null -> ErrorState(message = ui.error, onRetry = viewModel::refresh)
            ui.items.isEmpty() -> EmptyState(title = "No notifications yet")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(ui.items, key = { it.id ?: "" }) { n ->
                    val username = n.actorUsername
                    NotificationRow(
                        notification = n,
                        outcome = username?.let { ui.followRequestOutcomes[it] },
                        acting = ui.followRequestActingOn != null && ui.followRequestActingOn == username,
                        actionError = if (username != null && ui.followRequestErrorUsername == username) ui.followRequestError else null,
                        onClick = {
                            if (!n.read) n.id?.let(viewModel::markRead)
                            val activityId = n.activityId
                            if (!activityId.isNullOrBlank()) onOpenActivity(activityId)
                            // Federated actor: `actorUsername` is only the local part, and the
                            // profile endpoint resolves LOCAL usernames only — navigating with
                            // it sent every remote follower to a profile that does not exist
                            // ("User not found"). ActorHandle carries the `@user@host` that
                            // ProfileScreen resolves through WebFinger discovery, the same
                            // handle Discover and the follower list already pass.
                            else NotificationText.actorHandle(n)?.let(onOpenProfile)
                        },
                        onDelete = { n.id?.let(viewModel::delete) },
                        onAccept = { n.actorUsername?.let(viewModel::acceptFollowRequest) },
                        onReject = { n.actorUsername?.let(viewModel::rejectFollowRequest) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val vm: NotificationsViewModel = viewModel(factory = NotificationsViewModel.factory(container))
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.pollUnreadCount() }
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Activity") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            NotificationsTabContent(
                viewModel = vm,
                container = container,
                onOpenActivity = onOpenActivity,
                onOpenProfile = onOpenProfile,
            )
        }
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationDto,
    outcome: FollowRequestOutcome? = null,
    acting: Boolean = false,
    actionError: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAccept: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    // Wording comes from NotificationText so the in-app row and the background push
    // notification (Iteration 8f) always read identically.
    val text = NotificationText.describe(notification)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (notification.read) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Format.relative(notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                notification.activityTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            when (
                followRequestAction(
                    type = notification.type,
                    actorUsername = notification.actorUsername,
                    followRequestPending = notification.followRequestPending,
                    outcome = outcome,
                )
            ) {
                FollowRequestAction.BUTTONS -> {
                    Spacer(Modifier.padding(top = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAccept, enabled = !acting) { Text("Accept") }
                        OutlinedButton(onClick = onReject, enabled = !acting) { Text("Reject") }
                    }
                    if (actionError != null) {
                        Text(
                            text = actionError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                FollowRequestAction.ACCEPTED -> {
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        text = "✓ Accepted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FollowRequestAction.REJECTED -> {
                    Spacer(Modifier.padding(top = 6.dp))
                    Text(
                        text = "✗ Rejected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FollowRequestAction.NONE -> Unit
            }
        }
    }
}

/**
 * What the action area of a FOLLOW_REQUEST notification row shows.
 *
 * The server never deletes a follow-request notification: accepting or rejecting it only
 * flips the `followRequestPending` flag the list response carries (the web UI in
 * `templates/notifications.html` hides its buttons on that flag and swaps a successful
 * press for an "Accepted"/"Rejected" label). The row used to render the buttons purely
 * from `type == "FOLLOW_REQUEST"`, so a handled request still offered Accept/Reject.
 */
internal enum class FollowRequestAction { BUTTONS, ACCEPTED, REJECTED, NONE }

/**
 * Decides the row's action state.
 *
 * @param outcome a decision this session already completed for [actorUsername]; it wins
 *   over the server flag so the label survives the post-action refresh.
 */
internal fun followRequestAction(
    type: String?,
    actorUsername: String?,
    followRequestPending: Boolean?,
    outcome: FollowRequestOutcome? = null,
): FollowRequestAction {
    if (type != NotificationTypes.FOLLOW_REQUEST) return FollowRequestAction.NONE
    when (outcome) {
        FollowRequestOutcome.ACCEPTED -> return FollowRequestAction.ACCEPTED
        FollowRequestOutcome.REJECTED -> return FollowRequestAction.REJECTED
        null -> Unit
    }
    // The accept/reject POST addresses a local username; without one there is no button
    // to press (the web UI drops them for the same reason).
    if (actorUsername == null) return FollowRequestAction.NONE
    // Handled from the web UI or another device: keep the row, drop the buttons.
    if (followRequestPending == false) return FollowRequestAction.NONE
    return FollowRequestAction.BUTTONS
}

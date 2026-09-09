package com.fpclient.android.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fpclient.android.ui.components.StatRow
import com.fpclient.android.util.Format
import kotlinx.coroutines.launch

@Composable
internal fun DetailBody(
    activityId: String,
    viewModel: ActivityDetailViewModel,
    ui: ActivityDetailViewModel.UiState,
    unitSystem: String,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activity = ui.activity ?: return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(activity.title ?: Format.uppercaseFirst(activity.activityType), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                AuthorCard(
                    ui = ui,
                    username = activity.fullHandle ?: activity.resolvedUsername,
                    displayName = activity.resolvedDisplayName,
                    avatarUrl = activity.resolvedAvatarUrl,
                    onOpenProfile = onOpenProfile,
                    onToggleFollow = viewModel::toggleFollow,
                )
                if (!activity.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(activity.description)
                }
            }
        }
        item {
            StatRow(
                listOf(
                    "Distance" to Format.distance(activity.totalDistance, unitSystem),
                    "Time" to Format.duration(activity.totalDurationSeconds),
                    "Pace" to Format.pace(
                        activity.metrics?.averagePaceSeconds,
                        activity.metrics?.averageSpeed,
                        activity.metrics?.movingTimeSeconds ?: activity.totalDurationSeconds,
                        activity.totalDistance,
                        unitSystem,
                    ),
                    "Elev" to Format.elevation(activity.metrics?.totalAscent ?: activity.elevationGain, unitSystem),
                ),
            )
        }
        item {
            StatRow(
                listOf(
                    "Heart rate" to Format.heartRate(activity.metrics?.averageHeartRate),
                    "Speed" to Format.speedKmh(activity.metrics?.averageSpeed, unitSystem),
                    "Calories" to Format.calories(activity.metrics?.calories),
                    "Cadence" to Format.cadence(activity.metrics?.averageCadence),
                ),
            )
        }
        item { TrackMap(segments = viewModel.trackSegments(), hasTrack = ui.activity?.simplifiedTrack != null) }
        item { ReactionRow(activityId = activityId, viewModel = viewModel, ui = ui) }
        item {
            CommentComposer(
                activityId = activityId,
                viewModel = viewModel,
                onFocused = {
                    // Keep the composer visible above the soft keyboard.
                    scope.launch {
                        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                    }
                },
            )
        }
    }
}

/** Author identity card: avatar, name, handle + date, and a follow/unfollow button. */
@Composable
private fun AuthorCard(
    ui: ActivityDetailViewModel.UiState,
    username: String?,
    displayName: String?,
    avatarUrl: String?,
    onOpenProfile: (String) -> Unit,
    onToggleFollow: () -> Unit,
) {
    val serverUrl = ui.serverUrl
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!username.isNullOrBlank()) Modifier.clickable { onOpenProfile(username) } else Modifier)
                .padding(10.dp),
        ) {
            com.fpclient.android.ui.components.UserAvatar(
                avatarUrl = avatarUrl,
                displayName = displayName ?: username,
                serverUrl = serverUrl,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    "Recorded by",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    displayName ?: username ?: "Athlete",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    buildString {
                        if (!username.isNullOrBlank()) {
                            val handle = username.trim()
                            append(if (handle.startsWith("@")) handle else "@$handle")
                        }
                        if (!ui.activity?.startedAt.isNullOrBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(Format.dateTime(ui.activity?.startedAt))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Always visible for other athletes — even before a follow status arrives.
            if (!username.isNullOrBlank() && !ui.isOwnActivity) {
                val status = ui.followStatus
                val label = when {
                    status == null -> "Follow"
                    status.isFollowing || status.canUnfollow -> "Unfollow"
                    status.isFollowRequestPending -> "Request sent"
                    else -> "Follow"
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onToggleFollow,
                    enabled = !ui.followBusy,
                ) {
                    Text(label)
                }
            }
        }
    }
}

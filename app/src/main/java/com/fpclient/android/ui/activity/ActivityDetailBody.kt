package com.fpclient.android.ui.activity

import com.fpclient.android.util.RoutePrivacy
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
import androidx.compose.material3.FilterChip
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
                // The web's "Indoor" badge next to the title (trainer / Zwift / treadmill rides,
                // detected server-side: FIT sport/sub-sport, source app, or no GPS).
                if (activity.indoor == true) {
                    IndoorBadge(modifier = Modifier.padding(top = 4.dp))
                }
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
        // Steps only exist for activities whose source file carried them (e.g. FIT uploads from
        // the watch, which stamp `totalStrides`), so the row is shown only when there is a value
        // rather than as a "—" tile on every ride or manual entry.
        activity.metrics?.totalSteps?.takeIf { it > 0 }?.let { steps ->
            item { StatRow(listOf("Steps" to Format.steps(steps))) }
        }
        // Power (watts) — what a trainer ride (e.g. Zwift) is all about. The web shows average power
        // with the headline metrics and average + maximum power in the details; the app has a
        // single level of tiles, so both sit in one row, shown only when the file carried power.
        // A zero is "no power meter", not a reading: servers report 0 W for rides without one
        // (fitpub.social, e.g. a gravel ride from a phone), so it counts as absent like steps.
        val averagePower = activity.metrics?.averagePower?.takeIf { it > 0 }
        val maxPower = activity.metrics?.maxPower?.takeIf { it > 0 }
        if (averagePower != null || maxPower != null) {
            item {
                StatRow(
                    listOf(
                        "Avg power" to Format.power(averagePower),
                        "Max power" to Format.power(maxPower),
                    ),
                )
            }
        }
        // Same three-way choice as the web: an indoor activity never shows a map — a virtual
        // course (Zwift's Watopia, …) would otherwise be drawn as a real-world route — then the
        // track map, else a "No route recorded" hint.
        val hasTrack = activity.simplifiedTrack != null
        when {
            activity.indoor == true -> item { IndoorPlaceholder(activityType = activity.activityType) }
            hasTrack -> {
                val segments = viewModel.trackSegments()
                item { TrackMap(segments = segments, hasTrack = true) }
                // Owner only: the server sends privacyZones just to the owner, who sees the whole
                // route while everyone else gets it without the parts inside the zones.
                val zones = activity.privacyZones.orEmpty()
                if (ui.isOwnActivity && zones.isNotEmpty()) {
                    val coverage = RoutePrivacy.coverage(
                        segments.flatten().map { it.latitude to it.longitude },
                        zones,
                    )
                    if (coverage != RoutePrivacy.Coverage.NONE) {
                        item { PrivacyZoneNotice(coverage) }
                    }
                }
            }
            else -> item {
                Text(
                    "No route recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item { ReactionRow(activityId = activityId, viewModel = viewModel, ui = ui) }
        item { BoostRow(activityId = activityId, viewModel = viewModel, ui = ui, onOpenProfile = onOpenProfile) }
        item {
            CommentComposer(
                activityId = activityId,
                viewModel = viewModel,
                // The home instance cannot attach comments to a federated activity.
                commentOnOrigin = ui.origin,
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

/** Boosts (announces) of the activity: who boosted it, with a Boost/Unboost toggle when allowed. */
@Composable
private fun BoostRow(
    activityId: String,
    viewModel: ActivityDetailViewModel,
    ui: ActivityDetailViewModel.UiState,
    onOpenProfile: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Boosts (${ui.boosts.size})", style = MaterialTheme.typography.titleSmall)
            if (ui.activity?.boostEligible == true) {
                Spacer(Modifier.weight(1f))
                val boosted = ui.activity?.boostedByCurrentUser == true
                FilterChip(
                    selected = boosted,
                    enabled = !ui.boostBusy,
                    onClick = { viewModel.toggleBoost(activityId) },
                    label = { Text(if (boosted) "Boosted" else "Boost") },
                )
            }
        }
        if (ui.boosts.isEmpty()) {
            Text(
                "No boosts yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            ui.boosts.forEach { b ->
                val handle = b.fullHandle
                androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!handle.isNullOrBlank()) Modifier.clickable { onOpenProfile(handle) } else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        com.fpclient.android.ui.components.UserAvatar(
                            avatarUrl = b.avatarUrl,
                            displayName = b.displayName ?: b.fullHandle,
                            serverUrl = ui.serverUrl,
                            size = 28,
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(b.displayName ?: b.fullHandle ?: "Athlete", style = MaterialTheme.typography.labelLarge)
                            if (!handle.isNullOrBlank()) {
                                Text(
                                    handle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            Format.relative(b.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
                    status.isAccepted -> "Unfollow"
                    status.isPending -> "Request sent"
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

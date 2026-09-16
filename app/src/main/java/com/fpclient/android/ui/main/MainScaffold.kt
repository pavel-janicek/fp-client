package com.fpclient.android.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fpclient.android.AppContainer
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.ui.analytics.AnalyticsTabContent
import com.fpclient.android.ui.discover.DiscoverTabContent
import com.fpclient.android.ui.navigation.Routes
import com.fpclient.android.ui.notifications.NotificationsTabContent
import com.fpclient.android.ui.notifications.NotificationsViewModel
import com.fpclient.android.ui.profile.ProfileScreen
import com.fpclient.android.ui.record.RecordingBanner
import com.fpclient.android.ui.timeline.TimelineScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    container: AppContainer,
    appViewModel: AppViewModel,
    onOpenActivity: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRecord: () -> Unit = {},
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFollowers: (String) -> Unit = {},
    onOpenFollowing: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(Routes.BottomTab.TIMELINE.name) }

    // Badge for unread notifications.
    val notificationsVm: NotificationsViewModel =
        viewModel(factory = NotificationsViewModel.factory(container))
    val unread by notificationsVm.unreadCount.collectAsState()
    LaunchedEffect(Unit) { notificationsVm.pollUnreadCount() }

    val unitSystem by appViewModel.unitSystem.collectAsState()
    val sessionState by appViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Routes.BottomTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab.name,
                        onClick = {
                            selectedTab = tab.name
                            if (tab == Routes.BottomTab.NOTIFICATIONS) notificationsVm.markPollingDirty()
                        },
                        icon = {
                            val icon = when (tab) {
                                Routes.BottomTab.TIMELINE -> Icons.Filled.Timeline
                                Routes.BottomTab.SEARCH_TAB -> Icons.Filled.Search
                                Routes.BottomTab.ANALYTICS -> Icons.Filled.BarChart
                                Routes.BottomTab.NOTIFICATIONS -> Icons.Filled.Notifications
                                Routes.BottomTab.ME_TAB -> Icons.Filled.Person
                            }
                            if (tab == Routes.BottomTab.NOTIFICATIONS && unread > 0) {
                                BadgedBox(badge = { Badge { Text(unread.toString()) } }) { Icon(icon, null) }
                            } else {
                                Icon(icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // App-wide "recording in progress" banner (Iteration 8c): visible above
            // every tab while a GPS session runs; tapping it opens the live screen.
            RecordingBanner(onOpen = onOpenRecord)
            val modifier = Modifier.weight(1f).fillMaxWidth()
            when (Routes.BottomTab.valueOf(selectedTab)) {
            Routes.BottomTab.TIMELINE -> TimelineScreen(
                container = container,
                unitSystem = unitSystem,
                guestMode = sessionState.guest,
                modifier = modifier,
                onOpenActivity = onOpenActivity,
                onOpenProfile = onOpenProfile,
                onOpenCreate = onOpenCreate,
                onRequireSignIn = {
                    scope.launch {
                        // Clearing the guest flag switches to the auth flow.
                        container.sessionStore.clearGuest()
                    }
                },
            )
            Routes.BottomTab.SEARCH_TAB -> DiscoverTabContent(
                container = container,
                serverUrl = sessionState.serverUrl,
                onOpenProfile = onOpenProfile,
                modifier = modifier,
            )
            Routes.BottomTab.ANALYTICS -> AnalyticsTabContent(
                container = container,
                unitSystem = unitSystem,
                modifier = modifier,
            )
            Routes.BottomTab.NOTIFICATIONS -> NotificationsTabContent(
                viewModel = notificationsVm,
                container = container,
                onOpenActivity = onOpenActivity,
                onOpenProfile = onOpenProfile,
                modifier = modifier,
            )
            Routes.BottomTab.ME_TAB -> if (!sessionState.loggedIn) {
                val scope = rememberCoroutineScope()
                GuestMePanel(
                    serverUrl = sessionState.serverUrl,
                    onSignIn = {
                        scope.launch {
                            // Clearing the guest flag switches to the auth flow.
                            container.sessionStore.clearGuest()
                        }
                    },
                    onOpenSettings = onOpenSettings,
                )
            } else {
                ProfileScreen(
                    username = sessionState.username.ifBlank { "me" },
                    container = container,
                    appViewModel = appViewModel,
                    embedded = true,
                    onBack = {},
                    onOpenActivity = onOpenActivity,
                    onEditProfile = onOpenEditProfile,
                    onOpenSettings = onOpenSettings,
                    modifier = modifier,
                    onOpenCreate = onOpenCreate,
                    onOpenRecord = onOpenRecord,
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing,
                )
            }
        }
        }
    }
}

@Composable
private fun GuestMePanel(
    serverUrl: String,
    onSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("\uD83D\uDC64", style = MaterialTheme.typography.displayMedium)
        Text(
            "Browsing as a guest",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            serverUrl.removePrefix("https://").removePrefix("http://"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "You're viewing public activities only. Sign in to post activities, follow athletes and get notifications.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) { Text("Sign in or create account") }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Settings") }
    }
}

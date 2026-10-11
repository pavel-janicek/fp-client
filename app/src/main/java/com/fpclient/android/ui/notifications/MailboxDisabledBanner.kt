package com.fpclient.android.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fpclient.android.AppContainer
import com.fpclient.android.notifications.NotificationPolling
import com.fpclient.android.notifications.PushSubscription
import kotlinx.coroutines.launch

/**
 * Whether the "notifications are disabled" nudge banner should show on the Activity tab.
 *
 * Mailbox push (Iteration 8h) is this build's primary delivery path, so the banner is about *that*
 * being off — the OS `POST_NOTIFICATIONS` permission is handled by the separate in-Settings gate
 * ([com.fpclient.android.ui.settings.PushNotificationCard]) and is not duplicated here. The banner
 * shows when the user is signed in, has not dismissed it forever, and has no mailbox subscription
 * active for their own account.
 *
 * @param loggedIn guest/signed-out users have no mailbox to enable, so never nudge them.
 * @param dismissed the persisted "dismiss forever" choice.
 * @param subscriptionOwner the `serverUrl|username` owner of the stored subscription, or null when
 *   none is stored (push disabled). A subscription owned by a different account does not deliver
 *   for this user, so it counts as disabled for them.
 * @param owner the current session's owner key.
 */
internal fun bannerVisible(
    loggedIn: Boolean,
    dismissed: Boolean,
    subscriptionOwner: String?,
    owner: String?,
): Boolean = loggedIn && !dismissed && (subscriptionOwner == null || subscriptionOwner != owner)

/**
 * Top-of-Activity-tab banner: "Notifications are disabled, do you want to enable them?".
 *
 * Tapping the bar opens Settings (scrolled to the Push section — see `SettingsScreen`), and the
 * small "Dismiss forever" button persists that choice so the banner never returns. It is designed
 * to coexist with the unsynced-watch-workout banner above it: it renders *below* that bar as its
 * own `Surface`, so both can show as two stacked bars at the top of the tab.
 *
 * Styling follows [com.fpclient.android.ui.record.RecordingBanner] (a full-width `Surface` with a
 * clickable `Row`) so the two banners read consistently.
 */
@Composable
fun MailboxDisabledBanner(
    container: AppContainer,
    onOpenSettings: () -> Unit,
) {
    val nudgeStore = remember(container) { container.mailboxNudgeStore }
    val dismissed by nudgeStore.dismissed.collectAsState(initial = false)
    val session by container.sessionStore.session.collectAsState(initial = null)
    val subscription by container.pushSubscriptionStore.subscription.collectAsState()

    val loggedIn = session?.isLoggedIn == true
    val owner = session?.let { com.fpclient.android.notifications.NotificationPolling.cursorOwner(it.serverUrl, it.username) }
    val subscriptionOwner = subscription?.owner

    // Local override so the banner disappears the instant "Dismiss forever" is tapped, while the
    // persisted flag (written in the coroutine) keeps it hidden across recompositions and restarts.
    var hiddenLocally by remember { mutableStateOf(false) }
    if (!bannerVisible(loggedIn, dismissed, subscriptionOwner, owner)) return
    if (hiddenLocally) return

    val scope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Notifications are disabled",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Do you want to enable them?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    hiddenLocally = true
                    scope.launch { nudgeStore.dismiss() }
                },
            ) {
                Text("Dismiss forever", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

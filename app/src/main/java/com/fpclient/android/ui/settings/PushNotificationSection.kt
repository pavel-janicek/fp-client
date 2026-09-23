package com.fpclient.android.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fpclient.android.AppContainer
import com.fpclient.android.notifications.NotificationPollWorker
import com.fpclient.android.notifications.NotificationPolling
import com.fpclient.android.notifications.PushNotifications
import com.fpclient.android.util.Format
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Settings → Push (Iteration 8f).
 *
 * States the delivery model honestly — the poll runs every ~30 minutes and Android (Doze)
 * may defer it further, so these are "eventual" notifications, not instant push — and owns
 * the POST_NOTIFICATIONS runtime gate, structured like `LocationPermissionGate`: request from
 * here (never on app start), an in-app rationale first, and the system settings page as the
 * fallback once the OS stops showing the dialog. The permission is only ever *asked* once;
 * after that the card points at the OS-level toggle for the `fitpub_push` channel, which is
 * the user's real on/off switch for this feature.
 */
@Composable
internal fun PushNotificationCard(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cursor by container.notificationPollStore.cursor.collectAsState(initial = null)
    val prompted by container.notificationPollStore.permissionPrompted.collectAsState(initial = false)
    val session by container.sessionStore.session.collectAsState(initial = null)
    // Only this session's own timestamp is meaningful: another account's poll says nothing
    // about when *your* notifications were last checked.
    val owner = session?.let { NotificationPolling.cursorOwner(it.serverUrl, it.username) }

    var allowed by remember { mutableStateOf(PushNotifications.canPost(context)) }
    var showRationale by remember { mutableStateOf(false) }

    // The permission dialog (and the system settings page) takes the user away from the app;
    // re-check on resume so the card never shows a stale verdict.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = PushNotifications.canPost(context)
                if (allowed) showRationale = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        // Asked (and answered) once — the OS may not show the dialog again on a second denial.
        scope.launch { container.notificationPollStore.markPermissionPrompted() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Push", style = MaterialTheme.typography.titleSmall)
            Text(
                "FP Client checks your instance for new reactions, comments, shares and " +
                    "followers in the background — roughly every 30 minutes, and Android may " +
                    "delay it further to save battery. Delivery is therefore eventual, not " +
                    "instant; opening the Activity tab always shows everything immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            val lastPollAt = cursor?.takeIf { it.owner == owner }?.lastPollAt ?: 0L
            Text(
                if (lastPollAt > 0L) {
                    "Last checked ${Format.relative(Instant.ofEpochMilli(lastPollAt).toString())}."
                } else {
                    "No background check has run yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (allowed) "Notifications are allowed for FP Client."
                    else "Notifications are turned off for FP Client.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (!allowed) {
                if (Build.VERSION.SDK_INT >= 33 && !prompted) {
                    Button(
                        onClick = {
                            if (shouldShowNotificationRationale(context)) showRationale = true
                            else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Allow notifications") }
                } else {
                    Text(
                        "Turn them back on in the system settings for this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { openNotificationSettings(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Open notification settings") }
                }
            } else {
                OutlinedButton(
                    onClick = { openNotificationSettings(context) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Notification settings") }
                Text(
                    "Turn these off with the \"FitPub activity\" switch in the system " +
                        "notification settings — that switch applies to this feature only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Re-arming the schedule here makes the card a repair path: if the periodic poll
            // was ever dropped (e.g. by a system cleanup), tapping this brings it back.
            OutlinedButton(
                onClick = { NotificationPollWorker.schedule(context) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Ensure background checks are scheduled") }
        }
    }

    if (showRationale) {
        AlertDialog(
            // Resizable on large screens: not limited to the platform default width.
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showRationale = false },
            title = { Text("Why we send notifications") },
            text = {
                Text(
                    "Background checks let FP Client tell you about reactions, comments, " +
                        "shares and new followers without opening the app. They run on your " +
                        "device only, roughly every 30 minutes, and only while you are signed " +
                        "in — nothing is sent to any third-party push service.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRationale = false
                        // The dialog is only ever composed on API 33+ (the card's gate), but
                        // the guard is repeated here so lint can see it too (InlinedApi).
                        if (Build.VERSION.SDK_INT >= 33) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRationale = false }) { Text("Not now") }
            },
        )
    }
}

private fun shouldShowNotificationRationale(context: Context): Boolean {
    // POST_NOTIFICATIONS exists only on API 33+; below that there is nothing to explain.
    // The explicit guard also keeps lint from flagging the inlined constant (InlinedApi),
    // mirroring the SDK_INT check in PushNotifications.canPost().
    if (Build.VERSION.SDK_INT < 33) return false
    val activity = context as? Activity ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    )
}

/**
 * Opens this app's notification settings, where the `fitpub_push` channel appears as
 * "FitPub activity" and can be turned off without affecting anything else. minSdk is 26, so
 * the per-channel settings page (`ACTION_APP_NOTIFICATION_SETTINGS`) always exists.
 */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

package com.fpclient.android.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fpclient.android.AppContainer
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.notifications.InstantDeliveryBus
import com.fpclient.android.notifications.InstantDeliveryService
import com.fpclient.android.notifications.InstantDeliveryState
import com.fpclient.android.notifications.NotificationPollWorker
import com.fpclient.android.notifications.NotificationPolling
import com.fpclient.android.notifications.PushFetchWorker
import com.fpclient.android.notifications.PushNotifications
import com.fpclient.android.notifications.PushSubscriptionStore
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
    // Confirmation for the "Ensure background checks are scheduled" repair button below:
    // re-arming an already-scheduled check is deliberately a no-op (KEEP never shifts the next
    // run), so without this the tap would look broken.
    var scheduleConfirmed by remember { mutableStateOf(false) }

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

            // Re-arming the schedule here makes the card a repair path: if a background check
            // was ever dropped (e.g. by a system cleanup), tapping this brings it back. Both the
            // 8f poll and the 8h mailbox check are re-armed — either can be dropped, and both
            // calls are idempotent (KEEP), so a live schedule is never shifted.
            OutlinedButton(
                onClick = {
                    NotificationPollWorker.schedule(context)
                    PushFetchWorker.schedule(context)
                    // Re-arming is invisible when the work is already in place; say so plainly
                    // so the tap is never mistaken for a dead button.
                    scheduleConfirmed = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Ensure background checks are scheduled") }
            if (scheduleConfirmed) {
                Text(
                    "Background checks are scheduled — the next one runs within about " +
                        "30 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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

/**
 * Settings → Push, the direct-mailbox half (Iteration 8h).
 *
 * Probes whether the configured instance has Web Push on (`GET /api/web/push/vapid-key`; a
 * **503** means the admin has it disabled — the card then says so and the 8f background check
 * above remains the delivery path), lets the user point at their push mailbox (the
 * dockerized-server stack ships one at `https://push.paveljanicek.cz`), and enables/disables
 * the subscription. The copy states the trust model plainly: the keys are generated on-device
 * with JCA, the mailbox only ever sees ciphertext, no password or token is stored off-device,
 * and one tap runs `DELETE /subscribe` + mailbox removal + local key wipe. While this is on,
 * the 8f poll above keeps running as the fallback but stops announcing (this path delivers).
 */
@Composable
internal fun MailboxPushCard(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by container.sessionStore.session.collectAsState(initial = null)
    val subscription by container.pushSubscriptionStore.subscription.collectAsState()
    val storedMailbox by container.pushSubscriptionStore.mailboxBase.collectAsState()

    var mailboxInput by remember(storedMailbox) { mutableStateOf(storedMailbox) }
    var probing by remember { mutableStateOf(false) }
    // null = unknown (still probing, or the instance could not be asked), true/false = probe.
    var available by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val loggedIn = session?.isLoggedIn == true
    val owner = session?.let { NotificationPolling.cursorOwner(it.serverUrl, it.username) }
    val active = subscription != null && subscription?.owner == owner

    // Re-probe whenever the signed-in account (or instance) changes — a fresh Settings visit
    // always reflects the instance's current FITPUB_PUSH_ENABLED state.
    LaunchedEffect(session?.serverUrl, session?.username) {
        available = null
        error = null
        if (loggedIn) {
            probing = true
            available = when (val probe = container.pushRepository.probe()) {
                is ApiResult.Success -> probe.data
                // The instance could not be asked (offline, restarting): leave unknown —
                // enabling is still allowed because the subscribe call answers 503 itself.
                is ApiResult.Error -> null
            }
            probing = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Mailbox push", style = MaterialTheme.typography.titleSmall)
            Text(
                "Fetches encrypted notifications straight from your push mailbox roughly " +
                    "every 15 minutes, without any third-party push service. The decryption " +
                    "keys are generated on this device and never leave it — the mailbox only " +
                    "ever holds ciphertext — and disabling below revokes the subscription " +
                    "with a single call. While it is on, the background check above stays " +
                    "scheduled as a fallback but stops announcing (this path delivers instead).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (!loggedIn) {
                Text(
                    "Sign in to enable mailbox push.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                return@Column
            }

            if (active) {
                Text(
                    "Enabled for ${session?.username ?: "this account"} — the mailbox is " +
                        "checked about every 15 minutes and notifications collapse by their " +
                        "server tag.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // The optional 8i fast path only makes sense while this account owns the mailbox.
                InstantDeliveryBlock(container)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            error = when (val result = container.pushRepository.disable()) {
                                is ApiResult.Success -> null
                                is ApiResult.Error -> result.message
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (busy) "Disabling…" else "Disable mailbox push") }
            } else {
                if (subscription != null) {
                    Text(
                        "A mailbox subscription for a different account on this device is " +
                            "active; enabling push here replaces it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                OutlinedTextField(
                    value = mailboxInput,
                    onValueChange = { mailboxInput = it },
                    label = { Text("Mailbox URL") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    when {
                        probing -> "Checking whether this instance has push enabled…"
                        available == false ->
                            "Push is switched off on this instance, so the background check " +
                                "above remains your delivery path."
                        available == null ->
                            "Couldn't check whether this instance has push enabled — you can " +
                                "still try; enabling reports the instance's answer."
                        else ->
                            "This instance has Web Push enabled. Point the URL above at your " +
                                "mailbox (the shared one is " +
                                "${PushSubscriptionStore.DEFAULT_MAILBOX})."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            when (val result = container.pushRepository.enable(mailboxInput)) {
                                is ApiResult.Success ->
                                    // Idempotent (KEEP) — makes sure the 15-minute check runs
                                    // even if the app was updated since it was last scheduled.
                                    PushFetchWorker.schedule(context)
                                is ApiResult.Error -> error = result.message
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && available != false,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (busy) "Enabling…" else "Enable mailbox push") }
            }

            error?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Settings → Push, the optional instant fast path (Iteration 8i).
 *
 * Mailbox push alone is "eventual": the phone drains the queue about every 15 minutes. With this
 * switch on, the relay instead decrypts each message *as it arrives* and republishes the readable
 * `{title, body, url}` to a private ntfy topic, which the ntfy Android app (subscribed over
 * WebSocket) shows within seconds even while FP Client is closed.
 *
 * The card states the trust boundary verbatim, because this is the one switch that moves a key
 * off the device: the uploaded key is the same one the phone already holds and can only decrypt
 * notification payloads (it cannot read your account, your messages or the session token), it is
 * stored on the relay you chose, and turning the switch off — or disabling mailbox push — deletes
 * it from the relay in the same request.
 */
@Composable
private fun InstantDeliveryBlock(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subscription by container.pushSubscriptionStore.subscription.collectAsState()
    val storedServer by container.pushSubscriptionStore.ntfyServer.collectAsState()
    val socket by InstantDeliveryBus.state.collectAsState()

    var serverInput by remember(storedServer) { mutableStateOf(storedServer) }
    var topicInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Re-read on resume, like RecordingHealthCard does: the battery exemption is changed in
    // system settings, so the row below it must be re-evaluated when the user comes back.
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val exempt = remember(resumed) {
        runCatching {
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    val current = subscription ?: return
    // Relays before 8i mint no manage token, so there is no authorized way to configure this.
    val supported = current.manageToken != null
    val enabledNow = current.instantEnabled
    val server = serverInput.trim().ifEmpty { PushSubscriptionStore.DEFAULT_NTFY }

    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            "Instant delivery (optional)",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Instead of waiting for the ~15 minute check, FP Client keeps a connection open to " +
                "your ntfy server itself — no second app, nothing to configure elsewhere. The " +
                "relay forwards each notification still encrypted, and this phone decrypts it. " +
                "Your key never leaves this phone, not even for instant delivery.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (!supported) {
            Text(
                "This mailbox relay does not support instant delivery — ask its operator to " +
                    "update it. Notifications keep arriving through the ~15 minute check.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@Column
        }
        InstantToggleRow(
            enabledNow = enabledNow,
            busy = busy,
            onToggle = { on ->
                scope.launch {
                    busy = true
                    error = null
                    // Remember the ntfy address either way: it is where the receiver connects
                    // regardless of what the relay answers.
                    container.pushSubscriptionStore.setNtfyServer(serverInput)
                    error = if (on) {
                        when (val result = container.pushRepository.enableInstant(topicInput)) {
                            is ApiResult.Success -> {
                                // Only now is there something to listen to, so only now does
                                // the socket start.
                                InstantDeliveryService.start(context)
                                null
                            }
                            is ApiResult.Error -> result.message
                        }
                    } else {
                        // Stop the socket first: leaving it running would keep a permanent
                        // notification and an open connection for a feature the user just off.
                        InstantDeliveryService.stop(context)
                        when (val result = container.pushRepository.disableInstant()) {
                            is ApiResult.Success -> null
                            is ApiResult.Error -> result.message
                        }
                    }
                    topicInput = ""
                    busy = false
                }
            },
        )
        error?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!enabledNow) {
            InstantConfigureForm(
                topicInput = topicInput,
                serverInput = serverInput,
                busy = busy,
                onTopicChange = { topicInput = it },
                onServerChange = { serverInput = it },
            )
            return@Column
        }

        // --- the live state, which is the whole point of an in-app receiver -------------
        Text(
            "Status",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        InstantSocketStatus(state = socket)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ntfy server: $server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { InstantDeliveryService.start(context) }) {
                Text("Restart")
            }
        }
        Text(
            "The topic is generated on this device and never has to be copied anywhere. It is " +
                "still a secret: anyone who knows it can read these notifications, so do not " +
                "share it. Notifications older than about 12 hours (or older than the " +
                "~15 minute check's own queue) are picked up by that check instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        InstantBatteryRow(exempt = exempt)
    }
}

/** "Connecting / connected since / retrying", plus the last-message stamp. */
@Composable
private fun InstantSocketStatus(state: InstantDeliveryState) {
    val (headline, detail) = when (state) {
        is InstantDeliveryState.Stopped ->
            "Not listening" to
                "The receiver is not running. Turn instant delivery off and on, or restart the app."
        is InstantDeliveryState.Connecting ->
            "Connecting…" to "Opening the connection to your ntfy server."
        is InstantDeliveryState.Connected ->
            "Connected" to buildString {
                append("Listening since ")
                append(Format.dateTime(isoAt(state.since)))
                state.lastMessageAt?.let {
                    append(" · last notification ")
                    append(Format.relative(isoAt(it)))
                }
            }
        is InstantDeliveryState.Retrying ->
            "Retrying" to buildString {
                append("Attempt ${state.attempts}, next try in ${Format.duration(state.nextRetryInMs / 1000)}")
                state.lastError?.let { append(" — $it") }
            }
    }
    Text(headline, style = MaterialTheme.typography.bodyMedium)
    Text(
        detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** Epoch millis → the ISO-8601 string `Format` speaks, or null when unset. */
private fun isoAt(epochMillis: Long?): String? =
    epochMillis?.let { runCatching { Instant.ofEpochMilli(it).toString() }.getOrNull() }

/**
 * The battery row, modeled on `RecordingHealthCard`: seconds-long delivery needs a connection
 * Android does not kill, and a foreground service alone is not enough once Doze starts.
 * Modelled on it because the same explanation has already been shown to this user once, for
 * recording, and the two exemptions are the same Android setting.
 */
@Composable
private fun InstantBatteryRow(exempt: Boolean) {
    val context = LocalContext.current
    Text(
        if (exempt) "Battery optimization: unrestricted." else
            "Battery optimization is enabled. Android may delay or drop the connection when " +
                "the screen is off, which is the difference between instant and ~15 minutes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "A permanent \"listening for notifications\" notification is required for instant " +
            "delivery — it is what Android shows for a foreground service. Turn instant " +
            "delivery off above to remove it. The ongoing notification itself can be hidden in " +
            "Android's app notification settings; doing so does not stop the connection.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
    if (!exempt) {
        TextButton(onClick = { openInstantBatterySettings(context) }) {
            Text("Battery settings")
        }
    }
}

private fun openInstantBatterySettings(context: Context) {
    // Best effort: some OEM builds have no such screen, and a crash here would be far worse
    // than the user having to find the setting themselves.
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/** The 8i switch row, kept separate so both the enabled and the configuring state share it. */
@Composable
private fun InstantToggleRow(enabledNow: Boolean, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Deliver instantly through ntfy",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabledNow, enabled = !busy, onCheckedChange = onToggle)
    }
}

/** Shown while instant delivery is off: the optional custom topic and the ntfy server. */
@Composable
private fun InstantConfigureForm(
    topicInput: String,
    serverInput: String,
    busy: Boolean,
    onTopicChange: (String) -> Unit,
    onServerChange: (String) -> Unit,
) {
    Text(
        "Leave the topic empty to have an unguessable one generated on this device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    OutlinedTextField(
        value = topicInput,
        onValueChange = onTopicChange,
        label = { Text("ntfy topic (optional)") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
    OutlinedTextField(
        value = serverInput,
        onValueChange = onServerChange,
        label = { Text("ntfy server") },
        singleLine = true,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

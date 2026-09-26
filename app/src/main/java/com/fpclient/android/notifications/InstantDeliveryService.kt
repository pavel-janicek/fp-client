package com.fpclient.android.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.fpclient.android.FitPubApplication
import com.fpclient.android.MainActivity
import com.fpclient.android.R
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Iteration 8j — the in-app ntfy receiver. Subscribes to the private ntfy topic the relay
 * publishes to and posts the notification itself, so instant delivery never requires a second
 * app.
 *
 * A foreground service because seconds-long latency on Android is only reachable with a
 * long-lived socket: WorkManager's floor is 15 minutes, and a plain background connection is
 * killed by Doze. The permanent "listening for notifications" notification is therefore not
 * an annoyance but the mechanism — Settings → Push says so plainly and offers the battery
 * exemption that keeps Doze from closing the socket.
 *
 * Foreground-service type, and why it is `specialUse`:
 *  - The obvious candidate, `dataSync`, is **capped** on API 35+ (6 h in 24 h), and
 *    `Service.onTimeout()` is then mandatory — a subscription that is supposed to be
 *    always-on would be torn down every few hours and the user would silently fall back to
 *    ~15-minute delivery. We are not syncing user data; we are holding a push connection open
 *    for the user, which is precisely the "special use" case. It has no time cap.
 *  - `connectedDevice` is arguably close but drags in a permission (BLUETOOTH_CONNECT et al.)
 *    that has nothing to do with notifications, and would put a Bluetooth grant in the app's
 *    manifest for no reason — which would also make the F-Droid audit harder to justify.
 *  - Google Play requires a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justification string for this
 *    type; it is declared in the manifest next to the service.
 *  - `onTimeout()` is still implemented below: it is required on API 35+, and the correct
 *    behaviour for us is the same in every case — stop cleanly and let the user restart.
 *
 * Everything the socket does is best-effort by design. The 15-minute
 * `fitpub_push_mailbox_check` remains scheduled and remains the real backstop: the relay
 * enqueues the ciphertext even when a forward succeeds, so a message the socket missed is
 * still recovered, and messages older than ntfy's 12 h cache are recovered by it too.
 *
 * Connection contract:
 *  1. Connect only while a session is signed in AND owns a `PushSubscription` with
 *     `instantEnabled` — the same guard `PushFetchWorker` and `NotificationPollWorker` use.
 *     A guest, a signed-out device or another account's subscription must never connect.
 *  2. Reconnect with exponential backoff + jitter, and honour ntfy's `poll_request` by
 *     reconnecting with a gap-replay poll.
 *  3. A silent connection is a dead connection: the OkHttp read timeout is shorter than ntfy's
 *     `keepalive` interval is allowed to be, so a network that stopped delivering *fails*
 *     instead of lingering forever.
 *  4. On reconnect, replay with `?poll=1&since=<lastNtfyMessageId>` so nothing that arrived
 *     while the socket was down is lost.
 *  5. Dedupe by ntfy message id, because `since=` deliberately re-delivers its boundary.
 */
class InstantDeliveryService : LifecycleService() {

    private var scope: CoroutineScope? = null
    private var loop: Job? = null
    private val deduper = NtfyDeduper()

    /** When the current stream came up, and when a message last arrived on it. */
    private var connectedSince: Long = 0L
    private var lastMessageAt: Long? = null

    /**
     * OkHttp client for the NDJSON stream. No cookies, no CSRF, no session interceptor: the
     * ntfy server is on its own origin and must never see FitPub credentials. The topic *is*
     * the capability — exactly like the mailbox endpoint, it is a secret by virtue of being
     * unguessable, which is why it is generated on-device.
     *
     * `readTimeout` is the liveness guarantee: ntfy sends `keepalive` well inside 45 s, so a
     * 40 s read timeout means "no heartbeat arrived" reliably surfaces as an IOException and
     * triggers a reconnect. `pingInterval` matters only for the WebSocket mode the same client
     * could be pointed at; it is set so that mode is not silently unusable either.
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // A dedicated scope, cancelled in onDestroy: the socket must never outlive the
        // service, and a supervisor means one failed reconnect cannot tear the whole thing
        // down in a way that leaks the OkHttp call.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // The gate needs the session, which is a suspending read of the DataStore-backed
        // flow, so the whole decision happens on the service scope. Going foreground first
        // is what the platform requires within a few seconds of a startService, so the
        // ongoing notification appears even if the gate then says "no" — it is removed again
        // by the stopSelf() below.
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val current = scope
        if (current != null && loop?.isActive != true) {
            loop = current.launch {
                val target = currentTarget()
                if (target == null) {
                    // Guest / signed out / instant delivery off for this account: there is
                    // nothing to listen to, and staying up would show a permanent notification
                    // for no reason.
                    InstantDeliveryBus.reset()
                    stopSelf()
                    return@launch
                }
                // Seed the replay cursor from the store before the first connect: a service
                // that was killed and restarted must resume from where it stopped, not from
                // "no cursor" (which would replay ntfy's whole cache window as if it were new).
                deduper.restore(target.subscription.lastNtfyMessageId)
                runLoop(target)
            }
        }
        // START_STICKY: a long-lived socket is exactly the thing the OS kills first, and the
        // subscription is reconstructible from the store, so being restarted is correct.
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // API 35+ contract. `specialUse` has no cap today, so this is a safety net rather
        // than a routine event: stop cleanly (the scope dies with onDestroy) and let the user
        // or the next app start bring it back.
        stopSelf()
    }

    override fun onDestroy() {
        loop = null
        // Cancel the whole scope: no OkHttp call, no retry delay and no store write may
        // outlive the service, so a stopped service leaks nothing and reconnects nowhere.
        scope?.cancel()
        scope = null
        InstantDeliveryBus.reset()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ the loop

    private suspend fun runLoop(target: InstantTarget) {
        val current = scope ?: return
        var attempt = 0
        // A `poll_request` means "re-poll and reconnect", not "wait out the backoff".
        var forceReplay = false
        while (current.isActive) {
            val replaying = forceReplay || attempt > 0
            val url = if (replaying) {
                NtfyMessages.pollUrl(target.ntfyServer, target.topic, deduper.lastId)
            } else {
                NtfyMessages.subscribeUrl(target.ntfyServer, target.topic)
            }
            if (url == null) {
                // A misconfigured server/topic cannot be fixed by retrying — the Settings
                // card surfaces this reason and the user changes the field.
                InstantDeliveryBus.publish(
                    InstantDeliveryState.Retrying(
                        attempt,
                        0,
                        "The ntfy server or topic in Settings is not valid.",
                    ),
                )
                return
            }
            InstantDeliveryBus.publish(InstantDeliveryState.Connecting)
            val result = try {
                consume(url, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NtfyStreamResult.Failed(describe(e))
            }
            if (!current.isActive) return
            when (result) {
                // A clean end of stream is still an end of stream: reconnect, but do not
                // charge it as a failure attempt (a server that closes idle streams should
                // not escalate the backoff forever).
                is NtfyStreamResult.Ended -> {
                    attempt = 0
                    forceReplay = true
                }

                is NtfyStreamResult.Failed -> {
                    attempt++
                    forceReplay = true
                }

                is NtfyStreamResult.Replay -> {
                    attempt = 0
                    forceReplay = true
                }
            }
            if (result !is NtfyStreamResult.Failed) {
                // Replay or clean end: go straight back up, no sleep. A gap is time-critical.
                continue
            }
            val backoff = backoffMillis(attempt)
            InstantDeliveryBus.publish(
                InstantDeliveryState.Retrying(
                    attempt,
                    backoff,
                    (result as NtfyStreamResult.Failed).reason,
                ),
            )
            delay(backoff)
        }
    }

    /** What ended one pass over the stream. */
    private sealed interface NtfyStreamResult {
        /** The server closed the stream cleanly. */
        data object Ended : NtfyStreamResult

        /** ntfy said we fell behind; reconnect with `?poll=1&since=`. */
        data object Replay : NtfyStreamResult

        /** Transport/parse/protocol failure; retry with backoff. */
        data class Failed(val reason: String) : NtfyStreamResult
    }

    /**
     * Reads the NDJSON stream until it ends, handing every `message` event to [deliver].
     *
     * A malformed line is dropped and reading continues: the stream is a long-lived NDJSON
     * feed over a mobile network and a truncated line is routine, not fatal. Only a real
     * transport failure ends the pass, and the caller turns that into a backoff.
     */
    private suspend fun consume(url: String, target: InstantTarget): NtfyStreamResult {
        val current = scope ?: return NtfyStreamResult.Ended
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/x-ndjson")
            .build()
        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            return NtfyStreamResult.Failed(describe(e))
        }
        response.use { r ->
            if (!r.isSuccessful) {
                // 404 = topic does not exist (relay config lost); 401/403 = the ntfy server
                // requires auth. Both are configuration problems the user can act on, so the
                // reason travels to the Settings card.
                return NtfyStreamResult.Failed("ntfy answered HTTP ${r.code}.")
            }
            val body = r.body ?: return NtfyStreamResult.Failed("ntfy sent an empty response.")
            connectedSince = System.currentTimeMillis()
            InstantDeliveryBus.publish(InstantDeliveryState.Connected(connectedSince))
            val source = body.source()
            while (current.isActive) {
                // readUtf8Line returns null at end of stream — the server closed it, which is
                // an Ended, not a failure.
                val line = try {
                    source.readUtf8Line()
                } catch (e: IOException) {
                    return NtfyStreamResult.Failed(describe(e))
                } ?: return NtfyStreamResult.Ended
                when (val event = NtfyMessages.parse(line)) {
                    null -> Unit // blank / unknown / malformed line: keep reading
                    is NtfyEvent.Keepalive -> Unit
                    is NtfyEvent.Open -> Unit
                    is NtfyEvent.PollRequest -> return NtfyStreamResult.Replay
                    is NtfyEvent.Message -> deliver(event, target)
                }
            }
        }
        return NtfyStreamResult.Ended
    }

    /**
     * Decrypts one relayed blob and posts it through the existing [PushNotifications] path,
     * which owns the channel, the POST_NOTIFICATIONS gate, the collapse tag and the tap
     * deep-link. The ntfy `tags` array is honoured first (the relay publishes the payload tag
     * as a second entry), with the decrypted payload's own `tag` as the authority — the two
     * carry the same value, and preferring the decrypted one keeps this path byte-identical
     * to the 15-minute one.
     */
    private suspend fun deliver(message: NtfyEvent.Message, target: InstantTarget) {
        if (!deduper.accept(message.id)) return
        val container = (application as? FitPubApplication)?.container ?: return
        val payload = container.pushRepository.decryptPayload(message.message, target.subscription)
            ?: return
        val tag = message.tags.firstOrNull { NtfyMessages.isPayloadTag(it) } ?: payload.tag
        PushNotifications.postItem(
            this,
            if (tag == payload.tag) payload else payload.copy(tag = tag),
        )
        lastMessageAt = System.currentTimeMillis()
        InstantDeliveryBus.publish(InstantDeliveryState.Connected(connectedSince, lastMessageAt))
        // Advance the persisted cursor only *after* a successful post, so a crash in between
        // replays one message rather than silently skipping it.
        container.pushSubscriptionStore.setLastNtfyMessageId(deduper.lastId)
    }

    /** Why a pass failed, in words a user can act on (never an exception dump). */
    private fun describe(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException -> "The connection went quiet (no keep-alive)."
        is java.net.UnknownHostException -> "The ntfy server could not be resolved."
        is java.net.ConnectException -> "The ntfy server refused the connection."
        is javax.net.ssl.SSLException -> "The TLS connection to ntfy failed."
        else -> "The connection to ntfy failed (${e.javaClass.simpleName})."
    }

    // ------------------------------------------------------------------ the gate

    /** Everything one connection needs, resolved once per start. */
    internal data class InstantTarget(
        val ntfyServer: String,
        val topic: String,
        val subscription: PushSubscription,
    )

    /**
     * The subscription this device may listen to right now, or null when it must not connect.
     *
     * Mirrors `PushFetchWorker.doWork` exactly: a signed-out session, a guest, and a
     * subscription belonging to a *different* account on this device are all no-ops, and
     * instant delivery additionally has to be switched on. Free of Android types and exposed
     * to the test suite so the gate itself is pinned without running a service.
     */
    internal fun resolveTarget(
        isLoggedIn: Boolean,
        owner: String?,
        subscription: PushSubscription?,
        ntfyServer: String?,
    ): InstantTarget? {
        if (!isLoggedIn || owner == null || subscription == null) return null
        if (subscription.owner != owner) return null
        if (!subscription.instantEnabled) return null
        val topic = subscription.ntfyTopic?.takeIf { it.isNotBlank() } ?: return null
        val server = ntfyServer?.takeIf { it.isNotBlank() } ?: return null
        return InstantTarget(server, topic, subscription)
    }

    private suspend fun currentTarget(): InstantTarget? {
        val container = (application as? FitPubApplication)?.container ?: return null
        val session = container.sessionStore.currentSession()
        val owner = if (session.isLoggedIn) {
            PushSubscriptionStore.ownerOf(session.serverUrl, session.username)
        } else {
            null
        }
        return resolveTarget(
            isLoggedIn = session.isLoggedIn,
            owner = owner,
            subscription = container.pushSubscriptionStore.subscription.value,
            ntfyServer = container.pushSubscriptionStore.ntfyServer.value,
        )
    }

    /**
     * Exponential backoff with full jitter, capped at [MAX_BACKOFF_MS]. Jitter is not a
     * nicety: without it, every device behind one self-hosted ntfy reconnects in lockstep
     * after a server restart and knocks it over again.
     */
    internal fun backoffMillis(attempt: Int): Long {
        if (attempt <= 0) return 0
        val exponential = BASE_BACKOFF_MS.toDouble() * 2.0.pow((attempt - 1).coerceAtMost(16))
        val capped = min(exponential, MAX_BACKOFF_MS.toDouble()).toLong()
        return (SystemClock.elapsedRealtime() % (capped + 1)).coerceAtLeast(BASE_BACKOFF_MS)
    }

    // ------------------------------------------------------- the notification

    /**
     * Promotes to the foreground with the `specialUse` type. Returns false instead of
     * throwing when the platform refuses (e.g. `POST_NOTIFICATIONS` revoked while the process
     * was dead), so the caller stops cleanly rather than crashing.
     */
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                // The type constant was added in API 34; below that there is no type to pass
                // and startForeground(id, notification) is the whole API.
                0
            },
        )
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    /**
     * The permanent "listening for notifications" notification. Deliberately on its own
     * channel so the OS-level toggle is independent of `fitpub_push` (the notifications you
     * receive) and `track_recording` — a user who silences "FitPub instant delivery" should
     * not also lose their activity notifications.
     */
    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Listening for notifications")
            .setContentText("Instant delivery is on. Tap to open the app.")
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FitPub instant delivery",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Shown while FP Client keeps a connection open for instant notifications"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        /** Its own channel: independent OS-level toggle, separate from the other two. */
        const val CHANNEL_ID = "fitpub_instant_delivery"
        private const val NOTIFICATION_ID = 5102
        private const val ACTION_STOP = "com.fpclient.android.action.STOP_INSTANT_DELIVERY"

        /** Shorter than ntfy's `keepalive` interval, so silence becomes an IOException. */
        private const val READ_TIMEOUT_SECONDS = 40L

        internal const val BASE_BACKOFF_MS = 2_000L
        internal const val MAX_BACKOFF_MS = 5 * 60_000L

        /**
         * Starts (or re-promotes) the receiver. Safe to call repeatedly.
         *
         * `startService` throws when the caller is in the background on Android 8+, and this
         * is called from app start as well as from Settings. A missed start is not a crash
         * and not a silent failure either: the next app start, or the Settings toggle, tries
         * again, and the card shows "not listening" in the meantime — which is exactly what
         * the user needs to see.
         */
        fun start(context: Context) {
            runCatching { context.startService(Intent(context, InstantDeliveryService::class.java)) }
        }

        /**
         * Stops the receiver and removes its ongoing notification.
         *
         * A plain `stopService` would leave the FGS running its loop until the process died,
         * so the stop is delivered as an action the service handles. The `runCatching` has
         * the same rationale as in [start], and a failed stop is self-correcting: the gate
         * in [onStartCommand] no longer passes on the next start, because the user turning
         * the switch off already cleared `instantEnabled`.
         */
        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, InstantDeliveryService::class.java).setAction(ACTION_STOP),
                )
            }
        }

        /**
         * Starts the receiver only when this device's own store says instant delivery is on.
         *
         * The cheap half of the gate, evaluated without touching the session: the service
         * still re-checks the session and the owning account itself, so a stale "on" here can
         * never make it connect for the wrong user — it just stops immediately. Used at app
         * start, where starting a disabled feature would otherwise show a pointless permanent
         * notification to a user who turned instant delivery off.
         */
        fun startIfEnabled(context: Context) {
            val store = (context.applicationContext as? FitPubApplication)
                ?.container?.pushSubscriptionStore
            if (store?.subscription?.value?.instantEnabled == true) start(context)
        }
    }
}

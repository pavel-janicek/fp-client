package com.fpclient.android.data.repository

import com.fpclient.android.data.dto.ForwardKeysDto
import com.fpclient.android.data.dto.ForwardRequestDto
import com.fpclient.android.data.dto.PushKeysDto
import com.fpclient.android.data.dto.PushPayloadDto
import com.fpclient.android.data.dto.PushSubscribeRequest
import com.fpclient.android.data.dto.PushUnsubscribeRequest
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.ErrorMessages
import com.fpclient.android.data.network.FitPubApi
import com.fpclient.android.data.network.MailboxClient
import com.fpclient.android.data.session.SessionStore
import com.fpclient.android.notifications.PushPayloads
import com.fpclient.android.notifications.PushSubscription
import com.fpclient.android.notifications.PushSubscriptionStore
import com.fpclient.android.notifications.WebPushCrypto
import java.util.Base64

/**
 * Iteration 8h — the client half of Web Push: availability probe, subscribe/unsubscribe
 * through the app's existing session + CSRF flow, on-device key handling, and the
 * fetch → decrypt → parse pipeline that turns mailbox blobs into notification payloads.
 *
 * The instance calls ride [FitPubApi] (cookies + `X-XSRF-TOKEN` handled by `ApiClient`'s
 * interceptor); the mailbox calls ride the credential-free [MailboxClient]. No password or
 * token ever leaves the device — the only thing registered off-device is a keypair that can
 * do nothing but decrypt notification payloads, and `DELETE /subscribe` revokes it.
 */
class PushRepository(
    private val api: FitPubApi,
    private val mailbox: MailboxClient,
    private val store: PushSubscriptionStore,
    private val sessionStore: SessionStore,
) {

    /**
     * Availability probe against `GET /api/web/push/vapid-key`: `true` when the instance has
     * Web Push configured, `false` on **503** ("Push notifications are not configured" —
     * `FITPUB_PUSH_ENABLED` off or VAPID keys missing), an [ApiResult.Error] when the
     * instance could not be asked at all. The UI keeps the 8f poll fallback visible on `false`.
     */
    suspend fun probe(): ApiResult<Boolean> = try {
        val response = api.vapidKey()
        when {
            response.isSuccessful -> ApiResult.Success(true)
            response.code() == 503 -> ApiResult.Success(false)
            else -> ApiResult.Error(
                ErrorMessages.extract(
                    response.errorBody()?.string(),
                    "Couldn't check push availability on this instance.",
                ),
                response.code(),
            )
        }
    } catch (e: Exception) {
        ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
    }

    /**
     * Enables mailbox push for the signed-in session: mints a mailbox on the 8g relay,
     * generates the ECDH P-256 keypair + 16-byte auth secret on-device (JCA only), registers
     * the subscription via `POST /api/web/push/subscribe` and stores everything app-privately.
     * A failure after minting unregisters the fresh mailbox again so no orphan is left behind.
     */
    suspend fun enable(mailboxBase: String): ApiResult<Unit> {
        val session = sessionStore.currentSession()
        if (!session.isLoggedIn) return ApiResult.Error("Sign in to enable push notifications.")
        // Replacing an existing subscription (any account on this device): tear the old one
        // down first so neither the instance nor the old mailbox keeps a stale endpoint.
        if (store.subscription.value != null) disable()

        val minted = when (val result = mailbox.mint(mailboxBase)) {
            is ApiResult.Error -> return result
            is ApiResult.Success -> result.data
        }
        val endpoint = minted.endpoint

        val keys = WebPushCrypto.generateRecipientKeys()
        val subscribe = try {
            api.pushSubscribe(
                PushSubscribeRequest(
                    endpoint = endpoint,
                    keys = PushKeysDto(
                        p256dh = WebPushCrypto.toBase64Url(keys.publicKey),
                        auth = WebPushCrypto.toBase64Url(keys.authSecret),
                    ),
                ),
            )
        } catch (e: Exception) {
            mailbox.unregister(endpoint)
            return ApiResult.Error(ErrorMessages.fromThrowable(e), throwable = e)
        }
        if (!subscribe.isSuccessful) {
            mailbox.unregister(endpoint)
            val message = if (subscribe.code() == 503) {
                "Push notifications are not enabled on this instance."
            } else {
                ErrorMessages.extract(
                    subscribe.errorBody()?.string(),
                    "The instance rejected the push subscription.",
                )
            }
            return ApiResult.Error(message, subscribe.code())
        }

        store.save(
            PushSubscription(
                owner = PushSubscriptionStore.ownerOf(session.serverUrl, session.username),
                mailboxBase = MailboxClient.normalizeBaseUrl(mailboxBase),
                endpoint = endpoint,
                publicKey = WebPushCrypto.toBase64Url(keys.publicKey),
                privateKey = WebPushCrypto.toBase64Url(keys.privateKey),
                authSecret = WebPushCrypto.toBase64Url(keys.authSecret),
                // Only 8i-aware relays mint one; null means instant delivery stays off.
                manageToken = minted.manageToken,
            ),
        )
        return ApiResult.Success(Unit)
    }

    /**
     * Iteration 8i — switches the already-enabled mailbox from "the phone drains the queue every
     * ~15 minutes" to "the relay decrypts each message on arrival and publishes the readable text
     * to a private ntfy topic", so notifications land within seconds while the app is closed.
     *
     * The relay receives the *same* keypair that already lives on the phone, so it can do nothing
     * except decrypt notification payloads — no account, password or session material is involved.
     * Turning the switch off again ([disableInstant]) deletes that key from the relay.
     *
     * @param customTopic an ntfy topic of the user's choosing, or null to generate an
     *   unguessable one. Only the charset ntfy itself accepts is allowed.
     * @return the topic that was configured, so the UI can show and let the user copy it.
     */
    suspend fun enableInstant(customTopic: String? = null): ApiResult<String> {
        val subscription = store.subscription.value
            ?: return ApiResult.Error("Turn on mailbox push notifications first.")
        if (subscription.owner != currentOwner()) {
            return ApiResult.Error(
                "Mailbox push belongs to a different account on this device. Turn it off there first.",
            )
        }
        val manageToken = subscription.manageToken
            ?: return ApiResult.Error(
                "This mailbox relay does not support instant delivery. Ask its operator to update " +
                    "it, or keep the ~15 minute delivery.",
            )
        val session = sessionStore.currentSession()
        if (!session.isLoggedIn) return ApiResult.Error("Sign in to enable instant delivery.")

        val topic = customTopic?.trim()?.takeIf { it.isNotEmpty() }
            ?: PushSubscriptionStore.generateNtfyTopic()
        if (!PushSubscriptionStore.isValidNtfyTopic(topic)) {
            return ApiResult.Error(
                "An ntfy topic may only use a-z, 0-9, '-' and '_', and be at most 64 characters.",
            )
        }
        // Since 8j the relay forwards the *untouched ciphertext* — it never needs the key, so
        // no key is uploaded. This is the whole point of the step: instant delivery now moves
        // exactly as much off the phone as mailbox push does, and nothing else. The `keys`
        // object is still accepted by a pre-8j relay for one release (see ForwardRequestDto).
        val result = mailbox.setForward(
            endpoint = subscription.endpoint,
            manageToken = manageToken,
            request = ForwardRequestDto(
                topic = topic,
                clickBase = session.serverUrl,
                keys = ForwardKeysDto(
                    p256dh = subscription.publicKey,
                    auth = subscription.authSecret,
                ),
            ),
        )
        if (result is ApiResult.Error) return result
        store.setInstantForward(true, topic)
        return ApiResult.Success(topic)
    }

    /**
     * Iteration 8i teardown: `DELETE /push/<id>/forward` makes the relay discard the uploaded key
     * and stop publishing. Like [disable], the local mirror is updated even when the relay cannot
     * be reached — anything left there only ever decrypted notifications for a mailbox this
     * device no longer polls, and the relay expires it on its own.
     */
    suspend fun disableInstant(): ApiResult<Unit> {
        val subscription = store.subscription.value ?: return ApiResult.Success(Unit)
        if (!subscription.instantEnabled) return ApiResult.Success(Unit)
        val manageToken = subscription.manageToken
        val relayResult = if (manageToken == null) {
            ApiResult.Success(Unit)
        } else {
            mailbox.clearForward(subscription.endpoint, manageToken)
        }
        store.setInstantForward(false, null)
        return relayResult
    }

    /** The owner key of the signed-in session, or null when signed out. */
    private suspend fun currentOwner(): String? {
        val session = sessionStore.currentSession()
        if (!session.isLoggedIn) return null
        return PushSubscriptionStore.ownerOf(session.serverUrl, session.username)
    }

    /**
     * Disables mailbox push exactly as specified: `DELETE /subscribe` on the instance, then
     * `DELETE` the mailbox on the relay, then wipe the local keys. Each network step is
     * best-effort — the local wipe always happens, because that is the guarantee the user
     * asked for; anything left behind expires on its own (the relay tombstones the mailbox,
     * and the FitPub server drops subscriptions whose endpoint answers 410).
     */
    suspend fun disable(): ApiResult<Unit> {
        val subscription = store.subscription.value ?: return ApiResult.Success(Unit)
        val failures = mutableListOf<String>()

        // The server-side call needs a live session; a signed-out device can still wipe itself.
        if (sessionStore.currentSession().isLoggedIn) {
            try {
                val response = api.pushUnsubscribe(PushUnsubscribeRequest(subscription.endpoint))
                // 404 = the instance already forgot the endpoint; that is "unsubscribed".
                if (!response.isSuccessful && response.code() != 404) {
                    failures += "the instance did not confirm (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                failures += "the instance could not be reached"
            }
        }
        // Unregistering the mailbox also drops its forward config, so the key uploaded for 8i
        // instant delivery leaves the relay in the same call.
        if (mailbox.unregister(subscription.endpoint) is ApiResult.Error) {
            failures += "the mailbox could not be reached"
        }
        store.clear()
        return if (failures.isEmpty()) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error(
                "Push was turned off on this device, but " + failures.joinToString("; ") +
                    ". Anything left behind expires automatically after ~30 days.",
            )
        }
    }

    /**
     * Fetches the queued mailbox messages, decrypts each blob on-device (RFC 8291 `aes128gcm`,
     * a client-side mirror of the server's `WebPushService.encrypt`) and parses the
     * `{title, body, icon, tag, url}` JSON. A single undecryptable or unparsable message is
     * dropped — one bad blob must not block the rest of the batch.
     */
    suspend fun fetchPayloads(subscription: PushSubscription): ApiResult<List<PushPayloadDto>> {
        val queued = when (val fetched = mailbox.fetch(subscription.endpoint)) {
            is ApiResult.Error -> return fetched
            is ApiResult.Success -> fetched.data
        }
        val keys = when (val read = keysOf(subscription)) {
            is ApiResult.Error -> return read
            is ApiResult.Success -> read.data
        }
        return ApiResult.Success(queued.mapNotNull { decrypt(it.payload, subscription, keys) })
    }

    /**
     * Decrypts + parses one standard-base64 aes128gcm blob into a postable payload, or null when
     * it cannot be read (wrong key, corrupted relay copy, unparsable plaintext).
     *
     * Shared by the two delivery paths on purpose: the 15-minute `fitpub_push_mailbox_check` and
     * the 8j ntfy stream receive the *same* bytes over different transports (the relay hands
     * the mailbox queue the untouched blob as base64, and publishes the untouched blob to ntfy
     * as base64 too), so they must produce byte-identical notifications — including the
     * payload `tag` that drives the per-event-type collapse. One implementation, one fixture
     * test, no chance of the fast path drifting from the slow one.
     */
    fun decryptPayload(
        base64Blob: String,
        subscription: PushSubscription,
    ): PushPayloadDto? {
        val keys = when (val read = keysOf(subscription)) {
            is ApiResult.Error -> return null
            is ApiResult.Success -> read.data
        }
        return decrypt(base64Blob, subscription, keys)
    }

    /** The subscription's own key triple, decoded once per call site. */
    private fun keysOf(subscription: PushSubscription): ApiResult<PushKeys> = try {
        ApiResult.Success(
            PushKeys(
                WebPushCrypto.fromBase64Url(subscription.privateKey),
                WebPushCrypto.fromBase64Url(subscription.publicKey),
                WebPushCrypto.fromBase64Url(subscription.authSecret),
            ),
        )
    } catch (e: Exception) {
        ApiResult.Error("The stored push keys could not be read.")
    }

    private fun decrypt(
        base64Blob: String,
        subscription: PushSubscription,
        keys: PushKeys,
    ): PushPayloadDto? = runCatching {
        // The relay hands the blob back as standard base64 (Go []byte JSON encoding, and the
        // same encoding it publishes to ntfy).
        val blob = Base64.getDecoder().decode(base64Blob)
        PushPayloads.decode(
            WebPushCrypto.decrypt(blob, keys.privateKey, keys.publicKey, keys.authSecret),
        )
    }.getOrNull()

    /** Named so the log-free rule stays obvious at every call site. */
    private data class PushKeys(
        val privateKey: ByteArray,
        val publicKey: ByteArray,
        val authSecret: ByteArray,
    ) {
        // Keys must never reach a log line: redact, exactly like RecipientKeys does.
        override fun toString(): String = "PushKeys(…, …, …)"
    }
}

package com.fpclient.android.data.repository

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

        val endpoint = when (val minted = mailbox.mint(mailboxBase)) {
            is ApiResult.Error -> return minted
            is ApiResult.Success -> minted.data
        }

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
            ),
        )
        return ApiResult.Success(Unit)
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
        val keys = try {
            Triple(
                WebPushCrypto.fromBase64Url(subscription.privateKey),
                WebPushCrypto.fromBase64Url(subscription.publicKey),
                WebPushCrypto.fromBase64Url(subscription.authSecret),
            )
        } catch (e: Exception) {
            return ApiResult.Error("The stored push keys could not be read.")
        }
        val (privateKey, publicKey, authSecret) = keys
        val payloads = queued.mapNotNull { message ->
            runCatching {
                // The relay hands the blob back as standard base64 (Go []byte JSON encoding).
                val blob = Base64.getDecoder().decode(message.payload)
                PushPayloads.decode(WebPushCrypto.decrypt(blob, privateKey, publicKey, authSecret))
            }.getOrNull()
        }
        return ApiResult.Success(payloads)
    }
}

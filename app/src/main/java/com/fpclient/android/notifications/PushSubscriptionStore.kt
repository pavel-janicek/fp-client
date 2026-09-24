package com.fpclient.android.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What Iteration 8h remembers between runs: the mailbox endpoint registered with the FitPub
 * instance plus the ECDH keypair + auth secret that decrypt the payloads queued there.
 *
 * Everything lives in `EncryptedSharedPreferences` — the same app-private, keystore-encrypted
 * store the session JWT uses — so the private key at rest is protected by the device lock,
 * never exported, and only ever usable to decrypt notification payloads. The store also keeps
 * the user-configured mailbox base URL across enable/disable cycles.
 *
 * [subscription] is a `StateFlow` (not a DataStore flow) because the backing
 * `EncryptedSharedPreferences` is already an in-memory snapshot: every write in this class
 * updates both the preferences and the flow, so the Settings card, the mailbox worker and the
 * 8f poll always observe the same single source of truth.
 */
data class PushSubscription(
    /** `serverUrl|username` (via [NotificationPolling.cursorOwner]) this subscription belongs to. */
    val owner: String,
    /** Normalized base URL of the 8g relay, e.g. `https://push.paveljanicek.cz`. */
    val mailboxBase: String,
    /** The endpoint handed out by `POST /mailbox` and registered with the FitPub instance. */
    val endpoint: String,
    /** base64url 65-byte uncompressed P-256 public key (`p256dh`). */
    val publicKey: String,
    /** base64url 32-byte private scalar — decrypts notification payloads, nothing else. */
    val privateKey: String,
    /** base64url 16-byte RFC 8291 auth secret. */
    val authSecret: String,
)

class PushSubscriptionStore internal constructor(private val prefs: SharedPreferences) {

    /** Production path: the keystore-encrypted app-private store (same as the session token). */
    constructor(context: Context) : this(
        EncryptedSharedPreferences.create(
            context,
            "fitpub_push_keys",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ),
    )

    private object Keys {
        const val OWNER = "owner"
        const val MAILBOX = "mailbox_base"
        const val ENDPOINT = "endpoint"
        const val PUBLIC_KEY = "public_key"
        const val PRIVATE_KEY = "private_key"
        const val AUTH_SECRET = "auth_secret"
    }

    private val _subscription = MutableStateFlow(readSubscription())
    /** The active subscription, or null when mailbox push is off for every account here. */
    val subscription: StateFlow<PushSubscription?> = _subscription.asStateFlow()

    private val _mailboxBase = MutableStateFlow(
        prefs.getString(Keys.MAILBOX, DEFAULT_MAILBOX) ?: DEFAULT_MAILBOX,
    )
    /** The mailbox URL last entered in Settings (kept across disable/enable). */
    val mailboxBase: StateFlow<String> = _mailboxBase.asStateFlow()

    private fun readSubscription(): PushSubscription? {
        val endpoint = prefs.getString(Keys.ENDPOINT, null) ?: return null
        val owner = prefs.getString(Keys.OWNER, null) ?: return null
        val publicKey = prefs.getString(Keys.PUBLIC_KEY, null) ?: return null
        val privateKey = prefs.getString(Keys.PRIVATE_KEY, null) ?: return null
        val authSecret = prefs.getString(Keys.AUTH_SECRET, null) ?: return null
        return PushSubscription(
            owner = owner,
            mailboxBase = prefs.getString(Keys.MAILBOX, DEFAULT_MAILBOX) ?: DEFAULT_MAILBOX,
            endpoint = endpoint,
            publicKey = publicKey,
            privateKey = privateKey,
            authSecret = authSecret,
        )
    }

    /** Persists a freshly enabled subscription (replacing any previous one). */
    suspend fun save(subscription: PushSubscription) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(Keys.OWNER, subscription.owner)
            .putString(Keys.MAILBOX, subscription.mailboxBase)
            .putString(Keys.ENDPOINT, subscription.endpoint)
            .putString(Keys.PUBLIC_KEY, subscription.publicKey)
            .putString(Keys.PRIVATE_KEY, subscription.privateKey)
            .putString(Keys.AUTH_SECRET, subscription.authSecret)
            .apply()
        _mailboxBase.value = subscription.mailboxBase
        _subscription.value = subscription
    }

    /** Remembers the mailbox URL the user typed, even before enabling. */
    suspend fun setMailboxBase(raw: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(Keys.MAILBOX, raw).apply()
        _mailboxBase.value = raw
    }

    /**
     * Wipes the keypair, auth secret, endpoint and owner — the local half of "disable push".
     * The configured mailbox URL survives so re-enabling starts from the same base.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(Keys.OWNER)
            .remove(Keys.ENDPOINT)
            .remove(Keys.PUBLIC_KEY)
            .remove(Keys.PRIVATE_KEY)
            .remove(Keys.AUTH_SECRET)
            .apply()
        _subscription.value = null
    }

    companion object {
        /**
         * The mailbox shipped with the dockerized-server stack (`RELAY_PUBLIC_BASE_URL`);
         * users running their own relay override it in Settings.
         */
        const val DEFAULT_MAILBOX = "https://push.paveljanicek.cz"

        /** Owner string shared with the 8f cursor so all push pieces scope to one session. */
        fun ownerOf(serverUrl: String, username: String): String =
            NotificationPolling.cursorOwner(serverUrl, username)
    }
}

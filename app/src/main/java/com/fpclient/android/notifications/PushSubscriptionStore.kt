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
import java.security.SecureRandom

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
    /**
     * Bearer token minted by `POST /mailbox` that authorises `PUT|GET|DELETE /push/<id>/forward`
     * (Iteration 8i). Null on relays predating 8i — instant delivery then simply cannot be
     * switched on, and 8h delivery is unaffected.
     */
    val manageToken: String? = null,
    /** True while the relay forwards notifications to the ntfy topic below (ciphertext only). */
    val instantEnabled: Boolean = false,
    /**
     * The private ntfy topic the relay publishes to. Shown in Settings for reference, but no
     * longer something the user has to copy anywhere: FP Client subscribes to it itself
     * (Iteration 8j).
     */
    val ntfyTopic: String? = null,
    /**
     * Newest ntfy message id this device has delivered (Iteration 8j) — the `since=` value
     * the next reconnect replays from, so a connection that dropped mid-flight does not lose
     * the events in between. Not secret, but session-scoped like the rest of the record:
     * switching accounts must not inherit another account's cursor.
     */
    val lastNtfyMessageId: String? = null,
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
        const val MANAGE_TOKEN = "manage_token"
        const val INSTANT_ENABLED = "instant_enabled"
        const val NTFY_TOPIC = "ntfy_topic"
        const val NTFY_SERVER = "ntfy_server"
        const val NTFY_LAST_ID = "ntfy_last_id"
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
            manageToken = prefs.getString(Keys.MANAGE_TOKEN, null),
            instantEnabled = prefs.getBoolean(Keys.INSTANT_ENABLED, false),
            ntfyTopic = prefs.getString(Keys.NTFY_TOPIC, null),
            lastNtfyMessageId = prefs.getString(Keys.NTFY_LAST_ID, null),
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
            .putString(Keys.MANAGE_TOKEN, subscription.manageToken)
            .putBoolean(Keys.INSTANT_ENABLED, subscription.instantEnabled)
            .putString(Keys.NTFY_TOPIC, subscription.ntfyTopic)
            .putString(Keys.NTFY_LAST_ID, subscription.lastNtfyMessageId)
            .apply()
        _mailboxBase.value = subscription.mailboxBase
        _subscription.value = subscription
    }

    /**
     * Records the 8i/8j instant-forward state on the active subscription (the relay is already
     * updated by then — this is the local mirror, so a restart keeps showing the topic).
     *
     * Switching instant delivery **off** also drops the ntfy replay cursor: the new topic (or
     * no topic at all) has its own id space, and replaying from a cursor belonging to a
     * different topic would either skip real messages or resurrect a backlog.
     */
    suspend fun setInstantForward(enabled: Boolean, topic: String?) =
        withContext(Dispatchers.IO) {
            val current = _subscription.value
            val editor = prefs.edit()
                .putBoolean(Keys.INSTANT_ENABLED, enabled)
                .putString(Keys.NTFY_TOPIC, if (enabled) topic else null)
            if (!enabled) editor.remove(Keys.NTFY_LAST_ID)
            editor.apply()
            if (current != null) {
                _subscription.value = current.copy(
                    instantEnabled = enabled,
                    ntfyTopic = if (enabled) topic else null,
                    lastNtfyMessageId = if (enabled) current.lastNtfyMessageId else null,
                )
            }
        }

    /**
     * Advances the ntfy replay cursor (Iteration 8j). Called after every delivered message so
     * a process death, a reboot or a dropped connection resumes from the right place instead
     * of replaying the whole cache window.
     */
    suspend fun setLastNtfyMessageId(id: String?) = withContext(Dispatchers.IO) {
        if (id.isNullOrBlank()) return@withContext
        val current = _subscription.value ?: return@withContext
        prefs.edit().putString(Keys.NTFY_LAST_ID, id).apply()
        _subscription.value = current.copy(lastNtfyMessageId = id)
    }

    /** Remembers the mailbox URL the user typed, even before enabling. */
    suspend fun setMailboxBase(raw: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(Keys.MAILBOX, raw).apply()
        _mailboxBase.value = raw
    }

    private val _ntfyServer = MutableStateFlow(prefs.getString(Keys.NTFY_SERVER, DEFAULT_NTFY) ?: DEFAULT_NTFY)
    /**
     * The ntfy server the user's phone subscribes to (Iteration 8i). Informational only — the
     * relay publishes to whatever `RELAY_NTFY_URL` points at, and the ntfy app is configured by
     * hand, so the card just shows (and remembers) the address the topic lives on.
     */
    val ntfyServer: StateFlow<String> = _ntfyServer.asStateFlow()

    /** Remembers the ntfy server address so it survives restarts of the Settings screen. */
    suspend fun setNtfyServer(raw: String) = withContext(Dispatchers.IO) {
        val value = raw.trim()
        prefs.edit().putString(Keys.NTFY_SERVER, value).apply()
        _ntfyServer.value = value
    }

    /**
     * Wipes the keypair, auth secret, endpoint, manage token and owner — the local half of
     * "disable push". The configured mailbox URL survives so re-enabling starts from the same
     * base. The relay's forward config is dropped first by
     * [com.fpclient.android.data.repository.PushRepository.disable].
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(Keys.OWNER)
            .remove(Keys.ENDPOINT)
            .remove(Keys.PUBLIC_KEY)
            .remove(Keys.PRIVATE_KEY)
            .remove(Keys.AUTH_SECRET)
            .remove(Keys.MANAGE_TOKEN)
            .remove(Keys.INSTANT_ENABLED)
            .remove(Keys.NTFY_TOPIC)
            .remove(Keys.NTFY_LAST_ID)
            .apply()
        _subscription.value = null
    }

    companion object {
        /**
         * The mailbox shipped with the dockerized-server stack (`RELAY_PUBLIC_BASE_URL`);
         * users running their own relay override it in Settings.
         */
        const val DEFAULT_MAILBOX = "https://push.paveljanicek.cz"

        /**
         * The ntfy server shipped with the dockerized-server stack (`NTFY_BASE_URL`); the ntfy
         * Android app has to be pointed at the same address, so the card shows it by default.
         */
        const val DEFAULT_NTFY = "https://ntfy.paveljanicek.cz"

        /** Owner string shared with the 8f cursor so all push pieces scope to one session. */
        fun ownerOf(serverUrl: String, username: String): String =
            NotificationPolling.cursorOwner(serverUrl, username)

        /** ntfy allows 1-64 chars of `[-_a-z0-9]`; the relay enforces the same pattern. */
        private val NTFY_TOPIC_PATTERN = Regex("^[-_a-z0-9]{1,64}$")

        /** True when [topic] can be sent to the relay as-is (mirrors `ntfyTopicPattern` in forward.go). */
        fun isValidNtfyTopic(topic: String): Boolean = NTFY_TOPIC_PATTERN.matches(topic)

        /**
         * Mints an unguessable private ntfy topic for this device (16 random bytes as lowercase
         * hex behind a readable `fp-` prefix — 35 chars, inside ntfy's 64-char limit).
         */
        fun generateNtfyTopic(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            return "fp-$hex"
        }
    }
}

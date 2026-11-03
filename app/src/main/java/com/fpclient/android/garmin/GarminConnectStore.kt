package com.fpclient.android.garmin

import android.content.Context
import android.content.SharedPreferences
import com.fpclient.android.util.openEncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This phone's mailbox on the Garmin Connect relay (PLAN "Garmin Connect API", step 4): the
 * mailbox id and manage token from `POST /garmin/mailbox`, and the Web Push style recipient keys
 * every queued activity is encrypted to. The private key never leaves the phone, and the FitPub
 * session never reaches the relay.
 */
data class GarminConnectMailbox(
    val relayBase: String,
    val id: String,
    val manageToken: String,
    /** base64url 65-byte uncompressed P-256 point. */
    val publicKey: String,
    /** base64url 32-byte private scalar. */
    val privateKey: String,
    /** base64url 16-byte RFC 8291 auth secret. */
    val authSecret: String,
) {
    override fun toString(): String = "GarminConnectMailbox(relayBase=$relayBase, id=…, manageToken=…, keys=…)"
}

/** What the card shows: the relay's last answer about the link, plus the last sync outcome. */
data class GarminConnectStatus(
    val linked: Boolean = false,
    val activityExport: Boolean = false,
    /** "relink", "replaced" or "deregistered" when a link ended without the phone asking. */
    val linkLost: String? = null,
    val waitingFiles: Int = 0,
    val lastSyncEpochMs: Long = 0,
    val lastError: String? = null,
)

/** Keystore-encrypted, like the push keys ([com.fpclient.android.notifications.PushSubscriptionStore]). */
class GarminConnectStore internal constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(openEncryptedPrefs(context, "fitpub_garmin_connect"))

    private object Keys {
        const val RELAY = "relay_base"
        const val ID = "mailbox_id"
        const val TOKEN = "manage_token"
        const val PUBLIC_KEY = "public_key"
        const val PRIVATE_KEY = "private_key"
        const val AUTH_SECRET = "auth_secret"
        const val LINKED = "linked"
        const val ACTIVITY_EXPORT = "activity_export"
        const val LINK_LOST = "link_lost"
        const val WAITING = "waiting_files"
        const val LAST_SYNC = "last_sync"
        const val LAST_ERROR = "last_error"
        const val OBSOLETE_TESTING = "testing"
        const val WAKE_TOPIC = "wake_topic"
    }

    private val mutableMailbox = MutableStateFlow(readMailbox())
    val mailbox: StateFlow<GarminConnectMailbox?> = mutableMailbox.asStateFlow()

    private val mutableStatus = MutableStateFlow(readStatus())
    val status: StateFlow<GarminConnectStatus> = mutableStatus.asStateFlow()

    init {
        // The tester switch of 4.1.0 (seven taps on the version); the card is open to everyone
        // since 4.2.0.
        if (prefs.contains(Keys.OBSOLETE_TESTING)) prefs.edit().remove(Keys.OBSOLETE_TESTING).apply()
    }

    private fun readMailbox(): GarminConnectMailbox? {
        return GarminConnectMailbox(
            relayBase = prefs.getString(Keys.RELAY, null) ?: return null,
            id = prefs.getString(Keys.ID, null) ?: return null,
            manageToken = prefs.getString(Keys.TOKEN, null) ?: return null,
            publicKey = prefs.getString(Keys.PUBLIC_KEY, null) ?: return null,
            privateKey = prefs.getString(Keys.PRIVATE_KEY, null) ?: return null,
            authSecret = prefs.getString(Keys.AUTH_SECRET, null) ?: return null,
        )
    }

    private fun readStatus() = GarminConnectStatus(
        linked = prefs.getBoolean(Keys.LINKED, false),
        activityExport = prefs.getBoolean(Keys.ACTIVITY_EXPORT, false),
        linkLost = prefs.getString(Keys.LINK_LOST, null),
        waitingFiles = prefs.getInt(Keys.WAITING, 0),
        lastSyncEpochMs = prefs.getLong(Keys.LAST_SYNC, 0),
        lastError = prefs.getString(Keys.LAST_ERROR, null),
    )

    fun saveMailbox(mailbox: GarminConnectMailbox) {
        prefs.edit()
            .putString(Keys.RELAY, mailbox.relayBase)
            .putString(Keys.ID, mailbox.id)
            .putString(Keys.TOKEN, mailbox.manageToken)
            .putString(Keys.PUBLIC_KEY, mailbox.publicKey)
            .putString(Keys.PRIVATE_KEY, mailbox.privateKey)
            .putString(Keys.AUTH_SECRET, mailbox.authSecret)
            .commit()
        mutableMailbox.value = mailbox
    }

    /** The ntfy topic last handed to the relay for the instant wake-up, or null (PLAN 4.2.0). */
    val wakeTopic: String? get() = prefs.getString(Keys.WAKE_TOPIC, null)

    fun setWakeTopic(topic: String?) {
        prefs.edit().apply { if (topic == null) remove(Keys.WAKE_TOPIC) else putString(Keys.WAKE_TOPIC, topic) }.apply()
    }

    /** Forgets the mailbox, its keys and the cached status. */
    fun clear() {
        prefs.edit()
            .remove(Keys.RELAY).remove(Keys.ID).remove(Keys.TOKEN)
            .remove(Keys.PUBLIC_KEY).remove(Keys.PRIVATE_KEY).remove(Keys.AUTH_SECRET)
            .remove(Keys.LINKED).remove(Keys.ACTIVITY_EXPORT).remove(Keys.LINK_LOST)
            .remove(Keys.WAITING).remove(Keys.LAST_SYNC).remove(Keys.LAST_ERROR).remove(Keys.WAKE_TOPIC)
            .commit()
        mutableMailbox.value = null
        mutableStatus.value = GarminConnectStatus()
    }

    fun updateStatus(transform: (GarminConnectStatus) -> GarminConnectStatus) {
        val next = transform(mutableStatus.value)
        prefs.edit()
            .putBoolean(Keys.LINKED, next.linked)
            .putBoolean(Keys.ACTIVITY_EXPORT, next.activityExport)
            .putString(Keys.LINK_LOST, next.linkLost)
            .putInt(Keys.WAITING, next.waitingFiles)
            .putLong(Keys.LAST_SYNC, next.lastSyncEpochMs)
            .putString(Keys.LAST_ERROR, next.lastError)
            .apply()
        mutableStatus.value = next
    }

    companion object {
        const val DEFAULT_RELAY = "https://connect.paveljanicek.cz"

        /**
         * Removes what the FP Wear (Connect IQ) link of 4.0 test builds stored: its background
         * work, preferences and workout inbox. Cheap and idempotent, so it runs on every start.
         */
        fun removeFpWearLeftovers(context: Context) {
            val app = context.applicationContext
            val work = androidx.work.WorkManager.getInstance(app)
            listOf("fitpub_garmin_pull", "fitpub_garmin_upload_now", "fitpub_garmin_upload_retry")
                .forEach { work.cancelUniqueWork(it) }
            listOf("fitpub_garmin_link", "fitpub_garmin_inbox").forEach { app.deleteSharedPreferences(it) }
            java.io.File(app.filesDir, "garmin-inbox").deleteRecursively()
        }
    }
}

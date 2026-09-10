package com.fpclient.android.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Immutable snapshot of the session the app currently holds. The [serverUrl] is the
 * instance base URL (with trailing slash stripped) that every API call is routed to,
 * which is what lets a single app talk to any FitPub instance (federation friendly).
 */
data class Session(
    val serverUrl: String = "",
    val token: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    /** True when the user skipped login and is browsing the default instance anonymously. */
    val guest: Boolean = false,
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
    val isConfigured: Boolean get() = serverUrl.isNotBlank()
}

private val Context.fitPubDataStore: DataStore<Preferences> by preferencesDataStore(name = "fitpub_session")

/**
  * Persists the currently selected FitPub instance and (if logged in) the session JWT plus
 * cached identity fields. Non-sensitive data is stored in DataStore, while the auth token
 * (the `JWT_TOKEN` cookie value the server sets on login) is stored in EncryptedSharedPreferences.
 */
class SessionStore(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "fitpub_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        // Token is now in EncryptedSharedPreferences
        val USERNAME = stringPreferencesKey("username")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")
        val GUEST = booleanPreferencesKey("guest")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
    }

    private val tokenFlow: Flow<String> = context.fitPubDataStore.data.map {
        encryptedPrefs.getString("token", "") ?: ""
    }

    val session: Flow<Session> = combine(
        context.fitPubDataStore.data,
        tokenFlow
    ) { prefs, token ->
        Session(
            serverUrl = prefs[Keys.SERVER_URL] ?: "",
            token = token,
            username = prefs[Keys.USERNAME] ?: "",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            email = prefs[Keys.EMAIL] ?: "",
            guest = prefs[Keys.GUEST] ?: false,
        )
    }

    /** Persisted unit-system choice ("METRIC"/"IMPERIAL"); blank until the user picks
     *  one in Settings, so the unit system saved on the server profile can still seed it. */
    val unitSystem: Flow<String> = context.fitPubDataStore.data.map { prefs ->
        prefs[Keys.UNIT_SYSTEM] ?: ""
    }

    /** Persists the unit system the user picked in Settings (device-level preference). */
    suspend fun setUnitSystem(system: String) {
        context.fitPubDataStore.edit { it[Keys.UNIT_SYSTEM] = system }
    }

    suspend fun setServerUrl(rawUrl: String) {
        val normalized = normalizeServerUrl(rawUrl)
        context.fitPubDataStore.edit { it[Keys.SERVER_URL] = normalized }
    }

    suspend fun saveAuth(
        token: String,
        username: String,
        displayName: String?,
        email: String?,
    ) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString("token", token).apply()
        }
        context.fitPubDataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.DISPLAY_NAME] = displayName.orEmpty()
            it[Keys.EMAIL] = email.orEmpty()
            it[Keys.GUEST] = false
        }
    }

    /**
     * Skips account setup: points the app at the official instance so public timelines
     * stay browsable without an account.
     */
    suspend fun continueAsGuest() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().remove("token").apply()
        }
        context.fitPubDataStore.edit {
            it[Keys.SERVER_URL] = DEFAULT_SERVER_URL
            it[Keys.GUEST] = true
        }
    }

    /**
     * Enables anonymous browsing of the currently configured instance without touching
     * its URL (used by "Browse without an account" on the login screen).
     */
    suspend fun startGuestBrowsing() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().remove("token").apply()
        }
        context.fitPubDataStore.edit {
            it[Keys.GUEST] = true
        }
    }

    suspend fun clearGuest() {
        context.fitPubDataStore.edit { it.remove(Keys.GUEST) }
    }

    suspend fun updateDisplayName(displayName: String) {
        context.fitPubDataStore.edit { it[Keys.DISPLAY_NAME] = displayName }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().remove("token").apply()
        }
        context.fitPubDataStore.edit {
            it.remove(Keys.USERNAME)
            it.remove(Keys.DISPLAY_NAME)
            it.remove(Keys.EMAIL)
            it.remove(Keys.GUEST)
        }
    }

    companion object {
        /** The official FitPub instance used for "skip" / guest browsing. */
        const val DEFAULT_SERVER_URL = "https://fitpub.social"

        /** Normalizes a user-entered instance URL to a canonical base (no trailing slash). */
        fun normalizeServerUrl(raw: String): String {
            var url = raw.trim()
            if (url.isBlank()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            // Strip a well-known path suffix so "https://fitpub.social/" -> "https://fitpub.social"
            while (url.endsWith("/")) url = url.dropLast(1)
            return url
        }
    }
}
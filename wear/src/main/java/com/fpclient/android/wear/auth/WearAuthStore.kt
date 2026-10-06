package com.fpclient.android.wear.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearAuthDataStore by preferencesDataStore(name = "fitpub_wear_auth")

data class WearAuthState(
    val serverUrl: String = "",
    val token: String = "",
    val username: String = "",
    val displayName: String = "",
    val expired: Boolean = false,
) {
    val isSignedIn: Boolean get() = token.isNotBlank()
}

class WearAuthStore(context: Context) {
    private val dataStore = context.applicationContext.wearAuthDataStore

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
        val USERNAME = stringPreferencesKey("username")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EXPIRED = booleanPreferencesKey("expired")
    }

    val state: Flow<WearAuthState> = dataStore.data.map { prefs ->
        WearAuthState(
            serverUrl = prefs[Keys.SERVER_URL].orEmpty(),
            token = prefs[Keys.TOKEN].orEmpty(),
            username = prefs[Keys.USERNAME].orEmpty(),
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            expired = prefs[Keys.EXPIRED] ?: false,
        )
    }

    internal suspend fun apply(message: WearAuthMessage) {
        when (message.type) {
            "credentials" -> {
                val serverUrl = message.serverUrl.orEmpty()
                val token = message.token.orEmpty()
                if (serverUrl.isBlank() || token.isBlank()) {
                    clear(expired = true)
                    return
                }
                dataStore.edit { prefs ->
                    prefs[Keys.SERVER_URL] = serverUrl
                    prefs[Keys.TOKEN] = token
                    prefs[Keys.USERNAME] = message.username.orEmpty()
                    prefs[Keys.DISPLAY_NAME] = message.displayName.orEmpty()
                    prefs[Keys.EXPIRED] = false
                }
            }
            "expired" -> clear(expired = true)
            "signed_out" -> clear(expired = false)
        }
    }

    suspend fun clear(expired: Boolean) {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs[Keys.EXPIRED] = expired
        }
    }
}
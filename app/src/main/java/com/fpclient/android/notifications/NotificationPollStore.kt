package com.fpclient.android.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What the background poller remembers between runs (Iteration 8f): how far it has already
 * delivered, which session that cursor belongs to, and whether the user was already offered
 * the push permission once.
 *
 * The cursor is scoped to the session it was written for ([owner]) because a single device can
 * point at and sign into any FitPub instance: signing into a different account must not
 * swallow that account's first notifications as "already seen", and it must not replay the
 * previous account's rows either.
 */
data class NotificationCursor(
    val lastSeenId: String? = null,
    val lastPollAt: Long = 0L,
    val owner: String = "",
)

private val Context.pushDataStore: DataStore<Preferences> by preferencesDataStore(name = "fitpub_push")

class NotificationPollStore(private val context: Context) {

    private object Keys {
        val LAST_SEEN_ID = stringPreferencesKey("last_seen_notification_id")
        val LAST_POLL_AT = longPreferencesKey("last_poll_at")
        val OWNER = stringPreferencesKey("cursor_owner")
        /** Set once the user has been shown the push prompt, so it is never nagged twice. */
        val PERMISSION_PROMPTED = booleanPreferencesKey("post_notifications_prompted")
    }

    val cursor: Flow<NotificationCursor> = context.pushDataStore.data.map { prefs ->
        NotificationCursor(
            lastSeenId = prefs[Keys.LAST_SEEN_ID],
            lastPollAt = prefs[Keys.LAST_POLL_AT] ?: 0L,
            owner = prefs[Keys.OWNER] ?: "",
        )
    }

    /** True once the push-permission prompt has been shown (granted or dismissed). */
    val permissionPrompted: Flow<Boolean> = context.pushDataStore.data.map { prefs ->
        prefs[Keys.PERMISSION_PROMPTED] ?: false
    }

    /**
     * The cursor for [owner], or null when it belongs to a different session (never
     * delivered for the current account) — a null cursor means "start tracking from now".
     */
    suspend fun cursorFor(owner: String): NotificationCursor? {
        val current = cursor.first()
        return current.takeIf { it.owner == owner }
    }

    /**
     * Records a finished poll: [lastSeenId] is the newest notification id on the fetched page
     * (null when the page was empty, which leaves the previous cursor untouched so an empty
     * page cannot make the poller forget what it has seen).
     */
    suspend fun recordPoll(owner: String, lastSeenId: String?, at: Long = System.currentTimeMillis()) {
        context.pushDataStore.edit { prefs ->
            prefs[Keys.OWNER] = owner
            if (lastSeenId != null) prefs[Keys.LAST_SEEN_ID] = lastSeenId
            prefs[Keys.LAST_POLL_AT] = at
        }
    }

    suspend fun markPermissionPrompted() {
        context.pushDataStore.edit { it[Keys.PERMISSION_PROMPTED] = true }
    }

    /** Forgets the cursor entirely (used by tests and by a manual "reset" of the feature). */
    suspend fun clear() {
        context.pushDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_SEEN_ID)
            prefs.remove(Keys.LAST_POLL_AT)
            prefs.remove(Keys.OWNER)
        }
    }
}

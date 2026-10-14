package com.fpclient.android.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mailboxNudgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fitpub_mailbox_nudge",
)

/**
 * Remembers that the user chose to "dismiss forever" the mailbox-push-disabled nudge banner shown
 * on the Activity (Notifications) tab (mirrors [NotificationPollStore]'s DataStore approach).
 *
 * The flag is **device-global, not per-account**: the banner's job is a one-time prompt ("you have
 * no push set up, want to enable it?"), and once the user has seen and actively dismissed it they
 * should never be nagged again — signing into another account on the same device does not resurrect
 * it. Enabling mailbox push removes the banner anyway because it is gated on the subscription being
 * absent (see `bannerVisible`), so no per-owner reset is needed here.
 */
class MailboxNudgeStore(context: Context) {

    private object Keys {
        val DISMISSED = booleanPreferencesKey("mailbox_disabled_dismissed")
    }

    private val store = context.mailboxNudgeDataStore

    /** True once the user has dismissed the banner forever. */
    val dismissed: Flow<Boolean> = store.data.map { it[Keys.DISMISSED] ?: false }

    /** One-shot read for callers outside Compose (tests, workers). */
    suspend fun isDismissed(): Boolean = dismissed.first()

    /** Records the user's "dismiss forever" choice. */
    suspend fun dismiss() {
        store.edit { it[Keys.DISMISSED] = true }
    }
}

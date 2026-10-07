package com.fpclient.android.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.handshakeDataStore: DataStore<Preferences> by preferencesDataStore(name = "fitpub_phone_handshake")

/** Persisted phone-side snapshot of the watch handshake (shown in Settings). */
data class PhoneHandshakeDiagnostics(
    val lastRequestNodeId: String = "",
    val lastRequestPath: String = "",
    val lastRequestTimestampMs: Long = 0L,
    val lastReplySent: Boolean = false,
    val lastReplyType: String = "",
    val lastReplyTimestampMs: Long = 0L,
    val lastReplyFailed: Boolean = false,
    val reachableNodeIds: List<String> = emptyList(),
)

class PhoneHandshakeDiagnosticsStore(private val context: Context) {

    private object Keys {
        val LAST_REQUEST_NODE_ID = stringPreferencesKey("last_request_node_id")
        val LAST_REQUEST_PATH = stringPreferencesKey("last_request_path")
        val LAST_REQUEST_TIMESTAMP_MS = longPreferencesKey("last_request_timestamp_ms")
        val LAST_REPLY_SENT = booleanPreferencesKey("last_reply_sent")
        val LAST_REPLY_TYPE = stringPreferencesKey("last_reply_type")
        val LAST_REPLY_TIMESTAMP_MS = longPreferencesKey("last_reply_timestamp_ms")
        val LAST_REPLY_FAILED = booleanPreferencesKey("last_reply_failed")
        val REACHABLE_NODE_IDS = stringSetPreferencesKey("reachable_node_ids")
    }

    val diagnostics: Flow<PhoneHandshakeDiagnostics> = context.handshakeDataStore.data.map { prefs ->
        PhoneHandshakeDiagnostics(
            lastRequestNodeId = prefs[Keys.LAST_REQUEST_NODE_ID].orEmpty(),
            lastRequestPath = prefs[Keys.LAST_REQUEST_PATH].orEmpty(),
            lastRequestTimestampMs = prefs[Keys.LAST_REQUEST_TIMESTAMP_MS] ?: 0L,
            lastReplySent = prefs[Keys.LAST_REPLY_SENT] ?: false,
            lastReplyType = prefs[Keys.LAST_REPLY_TYPE].orEmpty(),
            lastReplyTimestampMs = prefs[Keys.LAST_REPLY_TIMESTAMP_MS] ?: 0L,
            lastReplyFailed = prefs[Keys.LAST_REPLY_FAILED] ?: false,
            reachableNodeIds = prefs[Keys.REACHABLE_NODE_IDS]?.toList().orEmpty(),
        )
    }

    suspend fun read(): PhoneHandshakeDiagnostics = diagnostics.first()

    suspend fun write(diagnostics: PhoneHandshakeDiagnostics) {
        context.handshakeDataStore.edit { prefs ->
            prefs[Keys.LAST_REQUEST_NODE_ID] = diagnostics.lastRequestNodeId
            prefs[Keys.LAST_REQUEST_PATH] = diagnostics.lastRequestPath
            prefs[Keys.LAST_REQUEST_TIMESTAMP_MS] = diagnostics.lastRequestTimestampMs
            prefs[Keys.LAST_REPLY_SENT] = diagnostics.lastReplySent
            prefs[Keys.LAST_REPLY_TYPE] = diagnostics.lastReplyType
            prefs[Keys.LAST_REPLY_TIMESTAMP_MS] = diagnostics.lastReplyTimestampMs
            prefs[Keys.LAST_REPLY_FAILED] = diagnostics.lastReplyFailed
            prefs[Keys.REACHABLE_NODE_IDS] = diagnostics.reachableNodeIds.toSet()
        }
    }
}

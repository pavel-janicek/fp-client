package com.fpclient.android.wear.auth

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Result of a credential request, surfaced to the UI so a failed handshake is
 * visible instead of silently doing nothing (Iteration 9b diagnostics).
 */
sealed interface CredentialRequestResult {
    data object SentAwaitingReply : CredentialRequestResult
    data object NoPhoneFound : CredentialRequestResult
    data class SendFailed(val reason: String) : CredentialRequestResult
}

class WatchWearAuthRelay(context: Context, private val authStore: WearAuthStore) {
    private val appContext = context.applicationContext

    suspend fun requestCredentials(): CredentialRequestResult = withContext(Dispatchers.IO) {
        val nodes = runCatching {
            Wearable.getCapabilityClient(appContext)
                .getCapability(WearAuthProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrElse { e ->
            Log.w(TAG, "getCapability(${WearAuthProtocol.CAPABILITY}) failed", e)
            return@withContext CredentialRequestResult.SendFailed("lookup failed: ${e.message}")
        }
        Log.i(
            TAG,
            "requestCredentials: ${nodes.size} reachable node(s) for ${WearAuthProtocol.CAPABILITY}: " +
                nodes.joinToString { "${it.id} displayName=${it.displayName} nearby=${it.isNearby}" },
        )
        if (nodes.isEmpty()) return@withContext CredentialRequestResult.NoPhoneFound

        var sent = false
        var lastError: String? = null
        nodes.forEach { node ->
            runCatching {
                val requestId = Wearable.getMessageClient(appContext)
                    .sendMessage(node.id, WearAuthProtocol.REQUEST_PATH, byteArrayOf())
                    .await()
                Log.i(TAG, "request sent to ${node.id}, msgId=$requestId")
                sent = true
            }.onFailure { e ->
                lastError = e.message
                Log.w(TAG, "sendMessage to ${node.id} failed", e)
            }
        }
        if (sent) CredentialRequestResult.SentAwaitingReply
        else CredentialRequestResult.SendFailed(lastError ?: "send failed")
    }

    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        authStore.clear(expired = false)
        val nodes = runCatching {
            Wearable.getCapabilityClient(appContext)
                .getCapability(WearAuthProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())
        if (nodes.isEmpty()) return@withContext false

        var sent = false
        nodes.forEach { node ->
            sent = runCatching {
                Wearable.getMessageClient(appContext)
                    .sendMessage(node.id, WearAuthProtocol.REVOKE_PATH, byteArrayOf())
                    .await()
                true
            }.getOrDefault(false) || sent
        }
        sent
    }

    private companion object {
        const val TAG = "WatchWearAuth"
    }
}
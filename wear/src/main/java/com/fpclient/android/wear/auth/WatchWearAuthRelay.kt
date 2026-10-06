package com.fpclient.android.wear.auth

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class WatchWearAuthRelay(context: Context, private val authStore: WearAuthStore) {
    private val appContext = context.applicationContext

    suspend fun requestCredentials(): Boolean = withContext(Dispatchers.IO) {
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
                    .sendMessage(node.id, WearAuthProtocol.REQUEST_PATH, byteArrayOf())
                    .await()
                true
            }.getOrDefault(false) || sent
        }
        sent
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
}
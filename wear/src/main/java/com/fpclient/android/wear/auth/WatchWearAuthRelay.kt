package com.fpclient.android.wear.auth

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class WatchWearAuthRelay(context: Context, private val authStore: WearAuthStore) {
    private val appContext = context.applicationContext

    suspend fun requestCredentials(): Boolean {
        val nodes = runCatching {
            Wearable.getCapabilityClient(appContext)
                .getCapability(WearAuthProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())
        if (nodes.isEmpty()) return false

        var sent = false
        nodes.forEach { node ->
            sent = runCatching {
                Wearable.getMessageClient(appContext)
                    .sendMessage(node.id, WearAuthProtocol.REQUEST_PATH, byteArrayOf())
                    .await()
                true
            }.getOrDefault(false) || sent
        }
        return sent
    }

    suspend fun signOut(): Boolean {
        authStore.clear(expired = false)
        val nodes = runCatching {
            Wearable.getCapabilityClient(appContext)
                .getCapability(WearAuthProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())
        if (nodes.isEmpty()) return false

        var sent = false
        nodes.forEach { node ->
            sent = runCatching {
                Wearable.getMessageClient(appContext)
                    .sendMessage(node.id, WearAuthProtocol.REVOKE_PATH, byteArrayOf())
                    .await()
                true
            }.getOrDefault(false) || sent
        }
        return sent
    }
}
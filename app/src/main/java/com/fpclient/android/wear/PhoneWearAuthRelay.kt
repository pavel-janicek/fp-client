package com.fpclient.android.wear

import android.content.Context
import android.util.Log
import com.fpclient.android.data.session.Session
import com.fpclient.android.data.session.SessionStore
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

internal object PhoneWearAuthRelay {
    fun start(context: Context, sessionStore: SessionStore, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch {
            sessionStore.session.distinctUntilChanged().collect { session ->
                broadcastState(appContext, session)
            }
        }
    }

    suspend fun broadcastState(context: Context, session: Session) {
        val message = PhoneWearAuthProtocol.stateFor(session)
        val path = when (message.type) {
            "credentials" -> PhoneWearAuthProtocol.CREDENTIALS_PATH
            "expired" -> PhoneWearAuthProtocol.EXPIRED_PATH
            else -> PhoneWearAuthProtocol.SIGNED_OUT_PATH
        }
        val nodes = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(PhoneWearAuthProtocol.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        }.getOrDefault(emptySet())
        nodes.forEach { node ->
            runCatching {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, PhoneWearAuthProtocol.encode(message))
                    .await()
            }
        }
    }

    suspend fun respondToRequest(context: Context, nodeId: String, session: Session) {
        val message = PhoneWearAuthProtocol.stateFor(session)
        val path = when (message.type) {
            "credentials" -> PhoneWearAuthProtocol.CREDENTIALS_PATH
            "expired" -> PhoneWearAuthProtocol.EXPIRED_PATH
            else -> PhoneWearAuthProtocol.SIGNED_OUT_PATH
        }
        Log.i(TAG, "respondToRequest node=$nodeId type=${message.type}")
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, path, PhoneWearAuthProtocol.encode(message))
                .await()
        }.onFailure { e -> Log.w(TAG, "reply to $nodeId failed", e) }
    }

    private const val TAG = "PhoneWearAuth"
}
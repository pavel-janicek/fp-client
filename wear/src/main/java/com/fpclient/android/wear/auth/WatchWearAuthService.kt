package com.fpclient.android.wear.auth

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WatchWearAuthService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        android.util.Log.i("WatchWearAuthService", "onMessageReceived path=$path from=${event.sourceNodeId}")
        if (path == com.fpclient.android.wear.recording.WorkoutSyncProtocol.ACK_PATH) {
            event.data.toString(Charsets.UTF_8).toLongOrNull()?.let { sessionId ->
                serviceScope.launch {
                    com.fpclient.android.wear.recording.WatchWorkoutSyncStore(this@WatchWearAuthService)
                        .remove(sessionId)
                }
            }
            return
        }
        if (path != WearAuthProtocol.CREDENTIALS_PATH &&
            path != WearAuthProtocol.SIGNED_OUT_PATH &&
            path != WearAuthProtocol.EXPIRED_PATH
        ) {
            super.onMessageReceived(event)
            return
        }

        serviceScope.launch {
            runCatching {
                val state = WearAuthProtocol.decode(event.data)
                WearAuthStore(this@WatchWearAuthService).apply(state)
                android.util.Log.i("WatchWearAuthService", "Applied auth state: username=${state.username} server=${state.serverUrl}")
            }.onFailure { e ->
                android.util.Log.w("WatchWearAuthService", "Failed to apply auth state from $path", e)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_DELETED) return@forEach
            val path = event.dataItem.uri.path.orEmpty()
            if (!path.startsWith(com.fpclient.android.wear.recording.WorkoutSyncProtocol.DATA_PATH_PREFIX)) return@forEach
            path.removePrefix(com.fpclient.android.wear.recording.WorkoutSyncProtocol.DATA_PATH_PREFIX)
                .toLongOrNull()
                ?.let { sessionId ->
                    serviceScope.launch {
                        com.fpclient.android.wear.recording.WatchWorkoutSyncStore(this@WatchWearAuthService)
                            .remove(sessionId)
                    }
                }
        }
        dataEvents.release()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
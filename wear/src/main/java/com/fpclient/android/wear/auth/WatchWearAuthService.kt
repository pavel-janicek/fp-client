package com.fpclient.android.wear.auth

import com.google.android.gms.wearable.MessageEvent
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
        if (path != WearAuthProtocol.CREDENTIALS_PATH &&
            path != WearAuthProtocol.SIGNED_OUT_PATH &&
            path != WearAuthProtocol.EXPIRED_PATH
        ) {
            super.onMessageReceived(event)
            return
        }

        serviceScope.launch {
            runCatching {
                WearAuthStore(this@WatchWearAuthService)
                    .apply(WearAuthProtocol.decode(event.data))
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
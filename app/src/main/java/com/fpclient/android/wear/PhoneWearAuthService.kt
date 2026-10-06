package com.fpclient.android.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PhoneWearAuthService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        val app = com.fpclient.android.FitPubApplication.from(this)
        when (event.path) {
            PhoneWearAuthProtocol.REQUEST_PATH -> serviceScope.launch {
                PhoneWearAuthRelay.respondToRequest(
                    this@PhoneWearAuthService,
                    event.sourceNodeId,
                    app.container.sessionStore.currentSession(),
                )
            }
            PhoneWearAuthProtocol.REVOKE_PATH -> serviceScope.launch {
                app.container.sessionStore.logout()
            }
            else -> super.onMessageReceived(event)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
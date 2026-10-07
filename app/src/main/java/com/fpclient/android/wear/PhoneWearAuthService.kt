package com.fpclient.android.wear

import android.util.Log
import com.fpclient.android.FitPubApplication
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class PhoneWearAuthService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diagnosticsStore by lazy { PhoneHandshakeDiagnosticsStore(this) }
    companion object {
        private const val TAG = "PhoneWearAuth"
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.i(TAG, "onMessageReceived path=${event.path} from=${event.sourceNodeId}")
        val app = FitPubApplication.from(this)
        when (event.path) {
            PhoneWearAuthProtocol.REQUEST_PATH -> handleAuthRequest(event, app)
            PhoneWearAuthProtocol.REVOKE_PATH -> serviceScope.launch {
                app.container.sessionStore.logout()
            }
            else -> super.onMessageReceived(event)
        }
    }

    private fun handleAuthRequest(event: MessageEvent, app: FitPubApplication) {
        val nodeId = event.sourceNodeId
        val diagnostics = PhoneHandshakeDiagnostics(
            lastRequestNodeId = nodeId,
            lastRequestPath = event.path,
            lastRequestTimestampMs = System.currentTimeMillis(),
        )

        serviceScope.launch {
            diagnosticsStore.write(diagnostics)
            val session = app.container.sessionStore.currentSession()
            val finalState = when (PhoneWearAuthRelay.sendReplyTo(this@PhoneWearAuthService, nodeId, session)) {
                true -> diagnostics.copy(
                    lastReplySent = true,
                    lastReplyType = PhoneWearAuthProtocol.stateFor(session).type,
                    lastReplyTimestampMs = System.currentTimeMillis(),
                )
                false -> diagnostics.copy(
                    lastReplySent = false,
                    lastReplyFailed = true,
                    lastReplyTimestampMs = System.currentTimeMillis(),
                )
            }
            diagnosticsStore.write(finalState)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val incoming = mutableListOf<IncomingAssets>()
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            val path = item.uri.path.orEmpty()
            if (!path.startsWith(PhoneWorkoutSyncProtocol.DATA_PATH_PREFIX)) return@forEach
            val map = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull() ?: return@forEach
            val sessionId = map.getLong(PhoneWorkoutSyncProtocol.KEY_ID, -1L)
            val gpx = map.getAsset(PhoneWorkoutSyncProtocol.ASSET_GPX) ?: return@forEach
            val sidecar = map.getAsset(PhoneWorkoutSyncProtocol.ASSET_SIDECAR) ?: return@forEach
            val nodeId = item.uri.host ?: return@forEach
            incoming += IncomingAssets(
                sessionId = sessionId,
                activityType = map.getString(PhoneWorkoutSyncProtocol.KEY_ACTIVITY_TYPE).orEmpty(),
                title = map.getString(PhoneWorkoutSyncProtocol.KEY_TITLE).orEmpty(),
                description = map.getString(PhoneWorkoutSyncProtocol.KEY_DESCRIPTION).orEmpty(),
                visibility = map.getString(PhoneWorkoutSyncProtocol.KEY_VISIBILITY).orEmpty(),
                ownerServerUrl = map.getString(PhoneWorkoutSyncProtocol.KEY_OWNER_SERVER).orEmpty(),
                ownerUsername = map.getString(PhoneWorkoutSyncProtocol.KEY_OWNER_USERNAME).orEmpty(),
                sourceNodeId = nodeId,
                dataItemUri = item.uri.toString(),
                gpx = gpx,
                sidecar = sidecar,
            )
        }
        dataEvents.release()
        incoming.forEach { assets ->
            serviceScope.launch(Dispatchers.IO) { receiveWorkout(assets) }
        }
    }

    private suspend fun receiveWorkout(assets: IncomingAssets) {
        if (assets.sessionId < 0L) return
        val app = com.fpclient.android.FitPubApplication.from(this)
        val store = app.container.wearWorkoutInboxStore
        val previous = store.get(assets.sessionId)
        if (previous?.uploadedActivityId != null) {
            PhoneWorkoutSyncScheduler.enqueue(this)
            return
        }
        if (previous == null) {
            val dataClient = Wearable.getDataClient(this)
            val gpxBytes = loadAsset(dataClient, assets.gpx) ?: return
            val sidecarBytes = loadAsset(dataClient, assets.sidecar) ?: return
            val entry = WearWorkoutInboxEntry(
                sessionId = assets.sessionId,
                gpxFileName = "workout-${assets.sessionId}.gpx",
                sidecarFileName = "workout-${assets.sessionId}.json",
                activityType = assets.activityType,
                title = assets.title,
                description = assets.description,
                visibility = assets.visibility,
                ownerServerUrl = assets.ownerServerUrl,
                ownerUsername = assets.ownerUsername,
                sourceNodeId = assets.sourceNodeId,
                dataItemUri = assets.dataItemUri,
                receivedAtEpochMs = System.currentTimeMillis(),
            )
            runCatching { store.stage(entry, gpxBytes, sidecarBytes) }.getOrElse { return }
        }
        PhoneWorkoutSyncScheduler.enqueue(this)
    }

    private suspend fun loadAsset(client: com.google.android.gms.wearable.DataClient, asset: Asset): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching { client.getFdForAsset(asset).await()?.inputStream?.use { it.readBytes() } }.getOrNull()
        }

    private data class IncomingAssets(
        val sessionId: Long,
        val activityType: String,
        val title: String,
        val description: String,
        val visibility: String,
        val ownerServerUrl: String,
        val ownerUsername: String,
        val sourceNodeId: String,
        val dataItemUri: String,
        val gpx: Asset,
        val sidecar: Asset,
    )

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
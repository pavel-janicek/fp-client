package com.fpclient.android.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Phone → watch mirror of the default visibility (Settings → Privacy). The watch has no setting
 * of its own: it stamps the mirrored value on every workout it records, for its direct upload as
 * well as for the relay through the phone.
 *
 * Delivered like the body profile ([WearBodyProfileSync]), as a Wear message: in reply to every
 * watch auth request, on every change, and on phone app start.
 */
object WearUploadDefaultsSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget [broadcast]; the next watch auth request catches up a missed watch. */
    fun push(context: Context, visibility: String) {
        val appContext = context.applicationContext
        scope.launch { broadcast(appContext, visibility) }
    }

    /** Sends [visibility] to every reachable watch. */
    suspend fun broadcast(context: Context, visibility: String) {
        PhoneWearAuthRelay.getReachableWatchNodeIds(context).forEach { nodeId ->
            sendTo(context, nodeId, visibility)
        }
    }

    /** Sends [visibility] to one watch node; false when the Data Layer refused it. */
    suspend fun sendTo(context: Context, nodeId: String, visibility: String): Boolean {
        val payload = DataMap().apply { putString(PhoneUploadDefaultsProtocol.KEY_VISIBILITY, visibility) }.toByteArray()
        val ok = runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, PhoneUploadDefaultsProtocol.MESSAGE_PATH, payload)
                .await()
            true
        }.getOrDefault(false)
        Log.i(TAG, "upload defaults to node=$nodeId visibility=$visibility sent=$ok")
        return ok
    }

    private const val TAG = "WearUploadDefaultsSync"
}

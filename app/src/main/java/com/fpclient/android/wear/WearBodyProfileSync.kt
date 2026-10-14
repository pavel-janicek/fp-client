package com.fpclient.android.wear

import android.content.Context
import android.util.Log
import com.fpclient.android.data.body.BodyProfile
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Phone → watch mirror of the opt-in [BodyProfile]. The phone amends relayed watch workouts
 * with an estimated calorie total itself, but a watch that uploads directly has to do it alone —
 * it needs the profile locally to stamp `totalCalories` into the FIT file before
 * `POST api/web/activities/upload`.
 *
 * Sent as a Wear **message** (the same transport as the auth handshake), not a DataItem: the
 * DataItem mirror never reached the watch in practice, while auth replies always do. The watch
 * persists what it receives, so the profile is delivered
 *  - in reply to every watch auth request (sent each time the watch app opens — see
 *    `PhoneWearAuthService`), which also catches up a freshly installed or re-paired watch,
 *  - on every save (opt-out sends an empty profile, wiping the watch's copy), and
 *  - on phone app start.
 *
 * Health data, so the boundary matters: the message travels only between the user's own paired
 * devices (Play-services device-to-device transport) and is never sent to the server.
 */
object WearBodyProfileSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fire-and-forget [broadcast]: a missing Data Layer (no Play services / no paired watch) must
     * never break saving the profile — the next watch auth request catches the watch up.
     */
    fun push(context: Context, profile: BodyProfile) {
        val appContext = context.applicationContext
        scope.launch { broadcast(appContext, profile) }
    }

    /** Sends [profile] to every reachable watch. */
    suspend fun broadcast(context: Context, profile: BodyProfile) {
        PhoneWearAuthRelay.getReachableWatchNodeIds(context).forEach { nodeId ->
            sendTo(context, nodeId, profile)
        }
    }

    /** Sends [profile] to one watch node; false when the Data Layer refused it. */
    suspend fun sendTo(context: Context, nodeId: String, profile: BodyProfile): Boolean {
        val ok = runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, PhoneBodyProfileProtocol.MESSAGE_PATH, encode(profile))
                .await()
            true
        }.getOrDefault(false)
        Log.i(TAG, "body profile to node=$nodeId optIn=${profile.optIn} sent=$ok")
        return ok
    }

    private fun encode(profile: BodyProfile): ByteArray = DataMap().apply {
        putBoolean(PhoneBodyProfileProtocol.KEY_OPT_IN, profile.optIn)
        putString(PhoneBodyProfileProtocol.KEY_SEX, profile.sex.storageValue)
        putString(PhoneBodyProfileProtocol.KEY_HEIGHT_CM, profile.heightCm?.toString() ?: "")
        putString(PhoneBodyProfileProtocol.KEY_WEIGHT_KG, profile.weightKg?.toString() ?: "")
        putString(PhoneBodyProfileProtocol.KEY_AGE_YEARS, profile.ageYears?.toString() ?: "")
    }.toByteArray()

    private const val TAG = "WearBodyProfileSync"
}

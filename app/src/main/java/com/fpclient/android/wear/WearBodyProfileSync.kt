package com.fpclient.android.wear

import android.content.Context
import com.fpclient.android.data.body.BodyProfile
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Phone → watch mirror of the opt-in [BodyProfile] over the Wear Data Layer. The phone amends
 * relayed watch workouts with an estimated calorie total itself, but a watch that uploads
 * directly has to do it alone — it needs the profile locally to stamp `totalCalories` into the
 * FIT file before `POST api/web/activities/upload`.
 *
 * Health data, so the boundary matters: the DataItem travels only between the user's own paired
 * devices (Play-services device-to-device transport) and is never sent to the server. Pushing a
 * cleared profile (opt-out) overwrites the watch's copy with an empty one.
 */
object WearBodyProfileSync {

    /**
     * Best-effort mirror of [profile] to every connected watch. Fire-and-forget: a missing Data
     * Layer (no Play services / no paired watch) must never break saving the profile — the phone
     * side keeps working and the next push after a pairing catches the watch up.
     */
    fun push(context: Context, profile: BodyProfile) {
        runCatching {
            val request = PutDataMapRequest.create(PhoneBodyProfileProtocol.DATA_PATH).apply {
                dataMap.putBoolean(PhoneBodyProfileProtocol.KEY_OPT_IN, profile.optIn)
                dataMap.putString(PhoneBodyProfileProtocol.KEY_SEX, profile.sex.storageValue)
                dataMap.putString(
                    PhoneBodyProfileProtocol.KEY_HEIGHT_CM,
                    profile.heightCm?.toString() ?: "",
                )
                dataMap.putString(
                    PhoneBodyProfileProtocol.KEY_WEIGHT_KG,
                    profile.weightKg?.toString() ?: "",
                )
                dataMap.putString(
                    PhoneBodyProfileProtocol.KEY_AGE_YEARS,
                    profile.ageYears?.toString() ?: "",
                )
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(request)
        }
    }
}

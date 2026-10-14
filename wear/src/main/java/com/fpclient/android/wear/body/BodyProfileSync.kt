package com.fpclient.android.wear.body

import android.content.Context
import androidx.core.net.toUri
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Watch-side read of the body profile the phone mirrors over the Wear Data Layer (the phone's
 * writer is `WearBodyProfileSync`). Queried right before a direct upload so the watch can amend
 * the FIT file with an estimated calorie total on its own — the phone-side relay path applies
 * the same estimate from its own copy of the profile.
 *
 * Everything here is best-effort: Data Layer problems must never block an upload, so a failure
 * just means the original, calorie-less FIT goes out (exactly the pre-mirror behaviour).
 */
object BodyProfileSync {

    private val PROFILE_URI = BodyProfileProtocol.readUriString().toUri()

    /** The mirrored profile, or null when none is mirrored yet / the query failed. */
    suspend fun read(context: Context): BodyProfile? {
        val buffer = runCatching {
            Wearable.getDataClient(context).getDataItems(PROFILE_URI).await()
        }.getOrNull() ?: return null
        return try {
            var profile: BodyProfile? = null
            for (item in buffer) {
                val map = runCatching { DataMapItem.fromDataItem(item).dataMap }.getOrNull()
                if (map != null) {
                    profile = BodyProfileProtocol.from(map)
                    break
                }
            }
            profile
        } finally {
            runCatching { buffer.release() }
        }
    }
}

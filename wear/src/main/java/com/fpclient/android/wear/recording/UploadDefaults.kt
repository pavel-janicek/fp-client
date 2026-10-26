package com.fpclient.android.wear.recording

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.flow.first

private val Context.uploadDefaultsDataStore by preferencesDataStore(name = "fitpub_wear_upload_defaults")

/**
 * Read side of the phone → watch upload-defaults message. Mirrors
 * `com.fpclient.android.wear.PhoneUploadDefaultsProtocol` in `:app`; the wire strings are pinned
 * by unit tests on both sides, so keep them synchronized.
 */
internal object UploadDefaultsProtocol {
    const val MESSAGE_PATH = "/fitpub/upload-defaults"
    const val KEY_VISIBILITY = "visibility"

    private val VISIBILITIES = setOf("PUBLIC", "FOLLOWERS", "LOCAL", "PRIVATE")

    /** The visibility in a message payload (a serialized [DataMap]), or null when it is not a known value. */
    fun decode(bytes: ByteArray): String? = valid(DataMap.fromByteArray(bytes).getString(KEY_VISIBILITY))

    fun valid(visibility: String?): String? = visibility?.uppercase()?.takeIf { it in VISIBILITIES }
}

/**
 * Watch-side copy of the phone's default visibility (Settings → Privacy on the phone; the
 * phone's writer is `WearUploadDefaultsSync`, `WatchWearAuthService` hands each message to
 * [apply]). The watch has no visibility setting of its own: every workout it records is stamped
 * with this value, both for its direct upload and for the relay through the phone.
 */
object UploadDefaultsSync {

    private val KEY_VISIBILITY = stringPreferencesKey(UploadDefaultsProtocol.KEY_VISIBILITY)

    suspend fun apply(context: Context, visibility: String) {
        context.applicationContext.uploadDefaultsDataStore.edit { it[KEY_VISIBILITY] = visibility }
    }

    /**
     * The mirrored default, or [WatchWorkoutSyncStore.DEFAULT_VISIBILITY] ("Only me") while the
     * phone has not sent one, e.g. an older phone app.
     */
    suspend fun visibility(context: Context): String = runCatching {
        UploadDefaultsProtocol.valid(context.applicationContext.uploadDefaultsDataStore.data.first()[KEY_VISIBILITY])
    }.getOrNull() ?: WatchWorkoutSyncStore.DEFAULT_VISIBILITY
}

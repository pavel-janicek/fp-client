package com.fpclient.android.wear

/**
 * Phone → watch message contract for the upload defaults (see [WearUploadDefaultsSync]).
 * Mirrored in `:wear` as `com.fpclient.android.wear.recording.UploadDefaultsProtocol`; the wire
 * strings are pinned by unit tests on both sides, so keep them synchronized.
 */
internal object PhoneUploadDefaultsProtocol {
    const val MESSAGE_PATH = "/fitpub/upload-defaults"
    const val KEY_VISIBILITY = "visibility"
}

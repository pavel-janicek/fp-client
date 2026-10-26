package com.fpclient.android.wear

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the phone → watch upload-defaults wire contract, mirroring the watch-side assertions in
 * `com.fpclient.android.wear.recording.UploadDefaultsProtocolTest`.
 */
class PhoneUploadDefaultsProtocolTest {

    @Test
    fun uploadDefaultsProtocolConstantsMatchDataLayerContract() {
        assertEquals("/fitpub/upload-defaults", PhoneUploadDefaultsProtocol.MESSAGE_PATH)
        assertEquals("visibility", PhoneUploadDefaultsProtocol.KEY_VISIBILITY)
    }
}

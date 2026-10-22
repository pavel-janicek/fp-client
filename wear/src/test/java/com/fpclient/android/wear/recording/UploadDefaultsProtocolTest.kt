package com.fpclient.android.wear.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the phone → watch upload-defaults wire contract, mirroring the phone-side assertions in
 * `com.fpclient.android.wear.PhoneUploadDefaultsProtocolTest`.
 */
class UploadDefaultsProtocolTest {

    @Test
    fun uploadDefaultsProtocolConstantsMatchDataLayerContract() {
        assertEquals("/fitpub/upload-defaults", UploadDefaultsProtocol.MESSAGE_PATH)
        assertEquals("visibility", UploadDefaultsProtocol.KEY_VISIBILITY)
    }

    @Test
    fun onlyKnownVisibilitiesAreAccepted() {
        assertEquals("FOLLOWERS", UploadDefaultsProtocol.valid("followers"))
        assertEquals("PRIVATE", UploadDefaultsProtocol.valid("PRIVATE"))
        assertNull(UploadDefaultsProtocol.valid("EVERYONE"))
        assertNull(UploadDefaultsProtocol.valid(null))
    }
}

package com.fpclient.android.wear

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the phone → watch body-profile wire contract, mirroring the watch-side assertions in
 * `com.fpclient.android.wear.body.BodyProfileProtocolTest` — a changed string on either side
 * breaks one of the two tests before it breaks a paired device.
 */
class PhoneBodyProfileProtocolTest {

    @Test
    fun bodyProfileProtocolConstantsMatchDataLayerContract() {
        assertEquals("/fitpub/body-profile", PhoneBodyProfileProtocol.MESSAGE_PATH)
        assertEquals("opt_in", PhoneBodyProfileProtocol.KEY_OPT_IN)
        assertEquals("sex", PhoneBodyProfileProtocol.KEY_SEX)
        assertEquals("height_cm", PhoneBodyProfileProtocol.KEY_HEIGHT_CM)
        assertEquals("weight_kg", PhoneBodyProfileProtocol.KEY_WEIGHT_KG)
        assertEquals("age_years", PhoneBodyProfileProtocol.KEY_AGE_YEARS)
    }
}

package com.fpclient.android.wear.body

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyProfileProtocolTest {

    @Test
    fun bodyProfileProtocolConstantsMatchDataLayerContract() {
        assertEquals("/fitpub/body-profile", BodyProfileProtocol.DATA_PATH)
        assertEquals("opt_in", BodyProfileProtocol.KEY_OPT_IN)
        assertEquals("sex", BodyProfileProtocol.KEY_SEX)
        assertEquals("height_cm", BodyProfileProtocol.KEY_HEIGHT_CM)
        assertEquals("weight_kg", BodyProfileProtocol.KEY_WEIGHT_KG)
        assertEquals("age_years", BodyProfileProtocol.KEY_AGE_YEARS)
    }

    @Test
    fun theWildcardReadUriIsWearablePathWithoutADoubleSlash() {
        assertEquals("wear://*/fitpub/body-profile", BodyProfileProtocol.readUriString())
    }

    @Test
    fun aCompleteOptedInProfileRoundTripsFromWireStrings() {
        val profile = BodyProfileProtocol.fromValues(
            optIn = true,
            sex = "male",
            heightCm = "180.0",
            weightKg = "75.0",
            ageYears = "30",
        )

        assertEquals(
            BodyProfile(
                optIn = true,
                sex = Sex.MALE,
                heightCm = 180.0,
                weightKg = 75.0,
                ageYears = 30,
            ),
            profile,
        )
        assertEquals(true, profile.isComplete)
    }

    @Test
    fun anOptOutCollapsesToTheEmptyProfile() {
        assertEquals(
            BodyProfile(),
            BodyProfileProtocol.fromValues(
                optIn = false,
                sex = "female",
                heightCm = "165.0",
                weightKg = "60.0",
                ageYears = "28",
            ),
        )
    }

    @Test
    fun blankOrGarbageNumbersStayNullSoTheProfileIsIncomplete() {
        val profile = BodyProfileProtocol.fromValues(
            optIn = true,
            sex = null,
            heightCm = "",
            weightKg = "not-a-number",
            ageYears = "",
        )

        assertEquals(true, profile.optIn)
        assertEquals(Sex.UNSPECIFIED, profile.sex)
        assertEquals(null, profile.heightCm)
        assertEquals(null, profile.weightKg)
        assertEquals(null, profile.ageYears)
        assertEquals(false, profile.isComplete)
    }
}

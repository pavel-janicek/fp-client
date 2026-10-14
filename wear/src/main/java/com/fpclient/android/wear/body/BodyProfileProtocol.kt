package com.fpclient.android.wear.body

import com.google.android.gms.wearable.DataMap

/**
 * Read side of the phone → watch message contract for the opt-in body profile. Mirrors
 * `com.fpclient.android.wear.PhoneBodyProfileProtocol` in `:app`; the wire strings are pinned by
 * unit tests on both sides, so keep them synchronized.
 */
internal object BodyProfileProtocol {
    const val MESSAGE_PATH = "/fitpub/body-profile"
    const val KEY_OPT_IN = "opt_in"
    const val KEY_SEX = "sex"
    const val KEY_HEIGHT_CM = "height_cm"
    const val KEY_WEIGHT_KG = "weight_kg"
    const val KEY_AGE_YEARS = "age_years"

    /** Decodes a body-profile message payload (a serialized [DataMap]). */
    fun decode(bytes: ByteArray): BodyProfile = from(DataMap.fromByteArray(bytes))

    /** Reads the profile from a decoded message payload. */
    fun from(map: DataMap): BodyProfile = fromValues(
        optIn = map.getBoolean(KEY_OPT_IN, false),
        sex = map.getString(KEY_SEX),
        heightCm = map.getString(KEY_HEIGHT_CM),
        weightKg = map.getString(KEY_WEIGHT_KG),
        ageYears = map.getString(KEY_AGE_YEARS),
    )

    /**
     * Pure mapping from the raw wire strings, mirroring `BodyProfileStore.read()` on the phone:
     * an opt-out collapses to the empty profile, and blank/absent numbers stay null.
     */
    fun fromValues(
        optIn: Boolean,
        sex: String?,
        heightCm: String?,
        weightKg: String?,
        ageYears: String?,
    ): BodyProfile {
        if (!optIn) return BodyProfile()
        return BodyProfile(
            optIn = true,
            sex = Sex.fromStorage(sex),
            heightCm = heightCm?.toDoubleOrNull(),
            weightKg = weightKg?.toDoubleOrNull(),
            ageYears = ageYears?.toIntOrNull(),
        )
    }
}

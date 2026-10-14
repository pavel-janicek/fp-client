package com.fpclient.android.wear.body

import com.google.android.gms.wearable.DataMap

/**
 * Read side of the phone → watch contract for the opt-in body profile. Mirrors
 * `com.fpclient.android.wear.PhoneBodyProfileProtocol` in `:app`; the wire strings are pinned by
 * unit tests on both sides, so keep them synchronized.
 */
internal object BodyProfileProtocol {
    const val DATA_PATH = "/fitpub/body-profile"
    const val KEY_OPT_IN = "opt_in"
    const val KEY_SEX = "sex"
    const val KEY_HEIGHT_CM = "height_cm"
    const val KEY_WEIGHT_KG = "weight_kg"
    const val KEY_AGE_YEARS = "age_years"

    /**
     * Wildcard DataItem URI used to read the mirrored profile from any connected node: the
     * wearable scheme with a wildcard host, followed by [DATA_PATH] (which already carries the
     * leading slash). The pinned test guards against concatenating a second slash after the
     * wildcard, which would produce a URI that silently matches nothing.
     */
    fun readUriString(): String = "wear://*" + DATA_PATH

    /** Reads the profile from a mirrored DataItem payload. */
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

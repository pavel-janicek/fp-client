package com.fpclient.android.wear

/**
 * Phone → watch message contract for the opt-in body profile (see [WearBodyProfileSync]).
 * Mirrored in `:wear` as `com.fpclient.android.wear.body.BodyProfileProtocol` — the wire strings
 * are pinned by unit tests on both sides (same convention as `PhoneWorkoutSyncProtocol`), so keep
 * them synchronized.
 */
internal object PhoneBodyProfileProtocol {
    const val MESSAGE_PATH = "/fitpub/body-profile"
    const val KEY_OPT_IN = "opt_in"
    const val KEY_SEX = "sex"
    const val KEY_HEIGHT_CM = "height_cm"
    const val KEY_WEIGHT_KG = "weight_kg"
    const val KEY_AGE_YEARS = "age_years"
}

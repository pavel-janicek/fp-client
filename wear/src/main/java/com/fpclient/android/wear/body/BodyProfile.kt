package com.fpclient.android.wear.body

/**
 * Sex, as used by calorie models. [UNSPECIFIED] lets a user opt in and enter height/weight/age
 * without committing to a value; the estimate falls back to a sex-neutral constant then.
 *
 * Mirror of `com.fpclient.android.data.body.Sex` in `:app` — `:wear` deliberately duplicates
 * shared types instead of depending on `:app` (docs/WEAR.md, decision 1), so keep the storage
 * values synchronized with the phone copy.
 */
enum class Sex(val storageValue: String) {
    UNSPECIFIED(""),
    FEMALE("female"),
    MALE("male"),
    ;

    companion object {
        /** Parses a wire value back to a [Sex]; unknown/blank -> [UNSPECIFIED]. */
        fun fromStorage(value: String?): Sex {
            val normalized = value?.trim()?.lowercase() ?: return UNSPECIFIED
            return entries.firstOrNull { it.storageValue == normalized } ?: UNSPECIFIED
        }
    }
}

/**
 * Body data a user opts in for calorie recording, as mirrored from the phone's
 * `com.fpclient.android.data.body.BodyProfile` over the Wear Data Layer (see
 * [BodyProfileProtocol]). It never leaves the user's own devices and is never sent to the
 * server — the watch only uses it to stamp an estimated calorie total into locally recorded
 * FIT files before uploading them.
 *
 * [isComplete] matches the phone's rule: an estimate fundamentally needs height and weight;
 * age and sex only sharpen it.
 */
data class BodyProfile(
    val optIn: Boolean = false,
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val ageYears: Int? = null,
) {
    /** True once both fields a calorie estimate fundamentally needs are present. */
    val isComplete: Boolean get() = heightCm != null && weightKg != null
}

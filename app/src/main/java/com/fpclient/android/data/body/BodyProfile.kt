package com.fpclient.android.data.body

/**
 * Sex, as used by calorie models. [UNSPECIFIED] lets a user opt in and enter height/weight/age
 * without committing to a value; a future calorie estimate falls back to a sex-neutral constant
 * when it is [UNSPECIFIED].
 */
enum class Sex(val storageValue: String) {
    UNSPECIFIED(""),
    FEMALE("female"),
    MALE("male"),
    ;

    companion object {
        /** Parses a persisted value back to a [Sex]; unknown/blank -> [UNSPECIFIED]. */
        fun fromStorage(value: String?): Sex {
            val normalized = value?.trim()?.lowercase() ?: return UNSPECIFIED
            return entries.firstOrNull { it.storageValue == normalized } ?: UNSPECIFIED
        }
    }
}

/**
 * Body data a user opts in for calorie recording. Persisted only on the phone (see
 * [BodyProfileStore]); it is never uploaded. Values are stored canonically in metric units so a
 * later calorie estimate has a single representation to work with.
 *
 * The numeric fields are nullable because a user may fill them across several visits;
 * [isComplete] reports whether the set holds the two fields a calorie estimate fundamentally
 * cannot be computed without (height + weight). Age and sex only sharpen the estimate.
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

    companion object {
        /** Generous plausible bounds for human height (cm). */
        val HEIGHT_RANGE_CM = 50.0..272.0

        /** Generous plausible bounds for human weight (kg). */
        val WEIGHT_RANGE_KG = 20.0..500.0

        /** Generous plausible bounds for age (years). */
        val AGE_RANGE_YEARS = 5..120

        /**
         * Parses a decimal from free text, tolerating a comma separator and surrounding
         * whitespace. The [Double.isFinite] guard drops NaN/Infinity, which [String.toDoubleOrNull]
         * is otherwise willing to accept.
         */
        fun parseDecimal(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

        /** Parses a whole number from free text; blank or fractional input -> null. */
        fun parseInt(text: String): Int? = text.trim().toIntOrNull()

        /** Height in cm if [text] parses to a plausible value, else null. */
        fun sanitizeHeightCm(text: String): Double? = parseDecimal(text)?.takeIf { it in HEIGHT_RANGE_CM }

        /** Weight in kg if [text] parses to a plausible value, else null. */
        fun sanitizeWeightKg(text: String): Double? = parseDecimal(text)?.takeIf { it in WEIGHT_RANGE_KG }

        /** Age in years if [text] parses to a plausible value, else null. */
        fun sanitizeAgeYears(text: String): Int? = parseInt(text)?.takeIf { it in AGE_RANGE_YEARS }

        /**
         * Formats a stored number back into a text field: whole values drop the trailing ".0" so
         * a saved 70.0 kg shows as "70", while a real fraction (70.5) is preserved. Null -> "".
         */
        fun inputText(value: Double?): String {
            if (value == null) return ""
            val asLong = value.toLong()
            return if (value == asLong.toDouble()) asLong.toString() else value.toString()
        }
    }
}

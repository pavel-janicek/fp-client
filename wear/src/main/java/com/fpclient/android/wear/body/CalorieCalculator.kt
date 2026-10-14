package com.fpclient.android.wear.body

import kotlin.math.roundToInt

/**
 * Calculates total calories (kcal) for a workout based on heart rate samples / duration,
 * activity type, and athlete's body profile (weight, height, age, sex).
 *
 * Mirror of `com.fpclient.android.data.body.CalorieCalculator` in `:app` — `:wear` duplicates
 * shared logic instead of depending on `:app` (docs/WEAR.md, decision 1), so keep the formulas
 * byte-identical: the phone's relay path and the watch's direct-upload path must estimate the
 * same workout the same way.
 */
object CalorieCalculator {

    /**
     * Calculates total calories (kcal) for a workout based on heart rate samples / duration,
     * activity type, and athlete's body profile (weight, height, age, sex).
     */
    fun calculateCalories(
        avgHr: Double?,
        durationSeconds: Double,
        activityType: String,
        bodyProfile: BodyProfile,
    ): Int {
        val weightKg = bodyProfile.weightKg ?: return 0
        val age = bodyProfile.ageYears ?: 30

        if (durationSeconds <= 0) return 0

        val durationMinutes = durationSeconds / 60.0

        // If Heart Rate is available and valid (> 40 bpm)
        if (avgHr != null && avgHr in 40.0..220.0) {
            val caloriesPerMin = when (bodyProfile.sex) {
                Sex.MALE -> {
                    (-55.0969 + (0.6309 * avgHr) + (0.1988 * weightKg) + (0.2017 * age)) / 4.184
                }
                Sex.FEMALE -> {
                    (-20.4022 + (0.4472 * avgHr) - (0.1263 * weightKg) + (0.074 * age)) / 4.184
                }
                Sex.UNSPECIFIED -> {
                    val male = (-55.0969 + (0.6309 * avgHr) + (0.1988 * weightKg) + (0.2017 * age)) / 4.184
                    val female = (-20.4022 + (0.4472 * avgHr) - (0.1263 * weightKg) + (0.074 * age)) / 4.184
                    (male + female) / 2.0
                }
            }
            val totalKcal = caloriesPerMin * durationMinutes
            return totalKcal.coerceAtLeast(1.0).roundToInt()
        }

        // Fallback: MET-based calculation if HR is missing or invalid
        val met = when (activityType.uppercase()) {
            "RUN", "RUNNING" -> 9.8
            "BIKE", "CYCLING" -> 7.5
            "HIKE", "HIKING" -> 6.0
            "WALK", "WALKING" -> 3.8
            else -> 5.0
        }
        val totalKcal = met * 3.5 * weightKg / 200.0 * durationMinutes
        return totalKcal.coerceAtLeast(1.0).roundToInt()
    }
}

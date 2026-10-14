package com.fpclient.android.wear.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirror of `com.fpclient.android.data.body.CalorieCalculatorTest` in `:app` — pins the same
 * numbers so the duplicated formulas cannot drift apart unnoticed.
 */
class CalorieCalculatorTest {

    @Test
    fun calculateCaloriesWithMaleBodyProfileAndHeartRate() {
        val profile = BodyProfile(
            optIn = true,
            sex = Sex.MALE,
            heightCm = 180.0,
            weightKg = 75.0,
            ageYears = 30,
        )

        // 1 hour (3600 sec) at avg HR 140 bpm
        val calories = CalorieCalculator.calculateCalories(
            avgHr = 140.0,
            durationSeconds = 3600.0,
            activityType = "RUN",
            bodyProfile = profile,
        )

        // Keytel Male formula for HR 140, Weight 75kg, Age 30:
        // (-55.0969 + 0.6309*140 + 0.1988*75 + 0.2017*30) / 4.184 = 12.937 kcal/min * 60 min = ~776 kcal
        assertTrue("Calories should be positive and reasonable", calories in 700..850)
    }

    @Test
    fun calculateCaloriesWithFemaleBodyProfileAndHeartRate() {
        val profile = BodyProfile(
            optIn = true,
            sex = Sex.FEMALE,
            heightCm = 165.0,
            weightKg = 60.0,
            ageYears = 28,
        )

        // 30 min (1800 sec) at avg HR 130 bpm
        val calories = CalorieCalculator.calculateCalories(
            avgHr = 130.0,
            durationSeconds = 1800.0,
            activityType = "RUN",
            bodyProfile = profile,
        )

        assertTrue("Calories should be positive and reasonable", calories in 200..400)
    }

    @Test
    fun calculateCaloriesFallbackToMetWhenHeartRateMissing() {
        val profile = BodyProfile(
            optIn = true,
            sex = Sex.UNSPECIFIED,
            heightCm = 175.0,
            weightKg = 70.0,
            ageYears = 35,
        )

        // 1 hour RUN without HR
        val calories = CalorieCalculator.calculateCalories(
            avgHr = null,
            durationSeconds = 3600.0,
            activityType = "RUN",
            bodyProfile = profile,
        )

        // MET 9.8 * 3.5 * 70 / 200 * 60 = 720.3 kcal
        assertEquals(720, calories)
    }
}

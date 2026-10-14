package com.fpclient.android.data.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure body-profile model (PLAN 9h): sex parsing, free-text sanitizing of
 * height/weight/age with plausible-range guards, round-tripping numbers back into text fields,
 * and the completeness check a future calorie estimate relies on.
 */
class BodyProfileTest {

    // ------------------------------------------------------------- Sex.fromStorage

    @Test
    fun `sex parses its own storage value`() {
        assertEquals(Sex.FEMALE, Sex.fromStorage("female"))
        assertEquals(Sex.MALE, Sex.fromStorage("male"))
        assertEquals(Sex.UNSPECIFIED, Sex.fromStorage(""))
    }

    @Test
    fun `sex parsing is case- and whitespace-insensitive`() {
        assertEquals(Sex.FEMALE, Sex.fromStorage("  Female "))
        assertEquals(Sex.MALE, Sex.fromStorage("MALE"))
    }

    @Test
    fun `sex parsing falls back to UNSPECIFIED for null and unknown values`() {
        assertEquals(Sex.UNSPECIFIED, Sex.fromStorage(null))
        assertEquals(Sex.UNSPECIFIED, Sex.fromStorage("other"))
        assertEquals(Sex.UNSPECIFIED, Sex.fromStorage("f"))
    }

    // ------------------------------------------------------------- parseDecimal

    @Test
    fun `parseDecimal accepts integers and fractions`() {
        assertEquals(70.0, BodyProfile.parseDecimal("70")!!, 1e-9)
        assertEquals(70.5, BodyProfile.parseDecimal("70.5")!!, 1e-9)
    }

    @Test
    fun `parseDecimal accepts a comma separator and trims whitespace`() {
        assertEquals(70.5, BodyProfile.parseDecimal("  70,5 ")!!, 1e-9)
    }

    @Test
    fun `parseDecimal returns null for blank and non-numeric text`() {
        assertNull(BodyProfile.parseDecimal(""))
        assertNull(BodyProfile.parseDecimal("   "))
        assertNull(BodyProfile.parseDecimal("abc"))
    }

    @Test
    fun `parseDecimal rejects NaN and Infinity`() {
        // toDoubleOrNull happily accepts these; the finite guard must drop them.
        assertNull(BodyProfile.parseDecimal("NaN"))
        assertNull(BodyProfile.parseDecimal("Infinity"))
        assertNull(BodyProfile.parseDecimal("-Infinity"))
    }

    // ------------------------------------------------------------- parseInt

    @Test
    fun `parseInt accepts whole numbers only`() {
        assertEquals(30, BodyProfile.parseInt("30"))
        assertNull(BodyProfile.parseInt("30.5"))
        assertNull(BodyProfile.parseInt(""))
    }

    // ------------------------------------------------------------- sanitizeHeightCm

    @Test
    fun `height is accepted within the plausible range`() {
        assertEquals(180.0, BodyProfile.sanitizeHeightCm("180")!!, 1e-9)
        assertEquals(152.4, BodyProfile.sanitizeHeightCm("152.4")!!, 1e-9)
    }

    @Test
    fun `height is rejected at and beyond the range bounds`() {
        assertNull(BodyProfile.sanitizeHeightCm("49")) // just below min
        assertNull(BodyProfile.sanitizeHeightCm("273")) // just above max
        assertNull(BodyProfile.sanitizeHeightCm("0"))
    }

    @Test
    fun `height range boundaries are inclusive`() {
        assertEquals(50.0, BodyProfile.sanitizeHeightCm("50")!!, 1e-9)
        assertEquals(272.0, BodyProfile.sanitizeHeightCm("272")!!, 1e-9)
    }

    // ------------------------------------------------------------- sanitizeWeightKg

    @Test
    fun `weight is accepted within the plausible range`() {
        assertEquals(70.0, BodyProfile.sanitizeWeightKg("70")!!, 1e-9)
    }

    @Test
    fun `weight is rejected outside the plausible range`() {
        assertNull(BodyProfile.sanitizeWeightKg("19"))
        assertNull(BodyProfile.sanitizeWeightKg("501"))
    }

    // ------------------------------------------------------------- sanitizeAgeYears

    @Test
    fun `age is accepted within the plausible range`() {
        assertEquals(30, BodyProfile.sanitizeAgeYears("30"))
    }

    @Test
    fun `age is rejected outside the range or when fractional`() {
        assertNull(BodyProfile.sanitizeAgeYears("4")) // below min
        assertNull(BodyProfile.sanitizeAgeYears("121")) // above max
        assertNull(BodyProfile.sanitizeAgeYears("30.5")) // not a whole number
    }

    // ------------------------------------------------------------- inputText

    @Test
    fun `inputText drops the trailing zero of whole values`() {
        assertEquals("70", BodyProfile.inputText(70.0))
        assertEquals("180", BodyProfile.inputText(180.0))
    }

    @Test
    fun `inputText preserves real fractions`() {
        assertEquals("70.5", BodyProfile.inputText(70.5))
    }

    @Test
    fun `inputText renders null as blank`() {
        assertEquals("", BodyProfile.inputText(null))
    }

    @Test
    fun `inputText round-trips through sanitize for a whole value`() {
        val parsed = BodyProfile.sanitizeHeightCm(BodyProfile.inputText(178.0))
        assertEquals(178.0, parsed!!, 1e-9)
    }

    // ------------------------------------------------------------- isComplete

    @Test
    fun `a fresh profile is incomplete`() {
        assertFalse(BodyProfile().isComplete)
        assertFalse(BodyProfile(optIn = true).isComplete)
        assertFalse(BodyProfile(heightCm = 180.0).isComplete)
        assertFalse(BodyProfile(weightKg = 70.0).isComplete)
    }

    @Test
    fun `a profile is complete once both height and weight are present`() {
        assertTrue(BodyProfile(heightCm = 180.0, weightKg = 70.0).isComplete)
        // Age and sex only sharpen an estimate; they are not required for completeness.
        assertTrue(BodyProfile(heightCm = 180.0, weightKg = 70.0, ageYears = 30, sex = Sex.FEMALE).isComplete)
    }
}

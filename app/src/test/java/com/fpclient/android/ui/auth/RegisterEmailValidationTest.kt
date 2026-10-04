package com.fpclient.android.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These pin the client-side e-mail check that gates the register form's submit button: the
 * field used to accept any free text and only the server noticed the difference.
 */
class RegisterEmailValidationTest {

    @Test
    fun acceptsOrdinaryAddresses() {
        assertTrue(isValidEmail("athlete@example.com"))
        assertTrue(isValidEmail("first.last+tag@sub.example.org"))
        assertTrue(isValidEmail("athlete@example.museum"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertTrue(isValidEmail("  athlete@example.com  "))
        assertFalse(isValidEmail("   "))
    }

    @Test
    fun rejectsNonAddresses() {
        assertFalse(isValidEmail(""))
        assertFalse(isValidEmail("not-an-email"))
        assertFalse(isValidEmail("missing-at-sign.example"))
        assertFalse(isValidEmail("missing-tld@example"))
        assertFalse(isValidEmail("@no-local.example.com"))
        assertFalse(isValidEmail("two@@example.com"))
        assertFalse(isValidEmail("spaces in@example.com"))
        assertFalse(isValidEmail("trailing-dot@example."))
        assertFalse(isValidEmail("user@.com"))
    }
}
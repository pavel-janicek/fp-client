package com.fpclient.android.util

import android.content.SharedPreferences
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

/**
 * The crash-loop guard behind the session and push stores: a keyset restored without its
 * AndroidKeyStore master key must cost one wiped file, never the app's startup.
 */
class EncryptedPrefsTest {

    private val store: SharedPreferences = Mockito.mock(SharedPreferences::class.java)

    @Test
    fun openEncryptedPrefsOrReset_returnsTheStoreWhenTheKeysetDecrypts() {
        var wipes = 0

        val opened = openEncryptedPrefsOrReset(
            fileName = "fitpub_push_keys",
            wipe = { wipes++ },
            open = { store },
        )

        assertSame(store, opened)
        assertEquals(0, wipes)
    }

    @Test
    fun openEncryptedPrefsOrReset_wipesBeforeRecreatingWhenTheKeysetIsUndecryptable() {
        // The failure the app actually shipped with: a restored keyset the freshly (re)built
        // keystore master key cannot authenticate.
        val events = mutableListOf<String>()
        var opens = 0

        val opened = openEncryptedPrefsOrReset(
            fileName = "fitpub_push_keys",
            wipe = { events += "wipe" },
            open = {
                events += "open"
                if (opens++ == 0) throw AEADBadTagException("mac check failed")
                store
            },
        )

        assertSame(store, opened)
        assertEquals(listOf("open", "wipe", "open"), events)
    }

    @Test
    fun openEncryptedPrefsOrReset_wipesWhenTheKeysetFileIsUnreadable() {
        var wipes = 0
        var opens = 0

        val opened = openEncryptedPrefsOrReset(
            fileName = "fitpub_secure_session",
            wipe = { wipes++ },
            open = {
                if (opens++ == 0) throw IOException("keyset file truncated")
                store
            },
        )

        assertSame(store, opened)
        assertEquals(1, wipes)
    }

    @Test
    fun openEncryptedPrefsOrReset_wipesWhenTheKeysetIsMangled() {
        // Not every mangled keyset arrives as a checked exception — Tink's JSON reader can
        // surface parse failures as plain RuntimeExceptions.
        var wipes = 0
        var opens = 0

        val opened = openEncryptedPrefsOrReset(
            fileName = "fitpub_push_keys",
            wipe = { wipes++ },
            open = {
                if (opens++ == 0) throw IllegalStateException("malformed keyset")
                store
            },
        )

        assertSame(store, opened)
        assertEquals(1, wipes)
    }

    @Test
    fun openEncryptedPrefsOrReset_propagatesAFailureThatSurvivesTheReset() {
        var wipes = 0

        try {
            openEncryptedPrefsOrReset(
                fileName = "fitpub_push_keys",
                wipe = { wipes++ },
                open = { throw GeneralSecurityException("still undecryptable") },
            )
            fail("the second failure must reach the caller instead of looping")
        } catch (expected: GeneralSecurityException) {
            // Nothing left to reset: the caller decides what a permanently broken store means.
        }

        assertEquals(1, wipes)
    }
}
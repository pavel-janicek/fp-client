package com.fpclient.android.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedUriArgTest {

    @Test
    fun `content uri shared from another app is accepted`() {
        assertTrue(
            SharedUriArg.isFileUri(
                "content://com.android.providers.documents/document/primary%3AFitPub%2Fmorning.gpx",
            ),
        )
    }

    @Test
    fun `file uri is accepted`() {
        assertTrue(SharedUriArg.isFileUri("file:///storage/emulated/0/ride.gpx"))
    }

    @Test
    fun `unfilled route placeholder is rejected`() {
        // Regression: navigating to the raw create?sharedUri={sharedUri} pattern passed this
        // literal through as the argument, and the upload form showed it as the chosen file
        // name (with Upload enabled for a bogus uri).
        assertFalse(SharedUriArg.isFileUri("{sharedUri}"))
    }

    @Test
    fun `missing or empty argument means no file`() {
        assertFalse(SharedUriArg.isFileUri(null))
        assertFalse(SharedUriArg.isFileUri(""))
    }

    @Test
    fun `path without a scheme is rejected`() {
        assertFalse(SharedUriArg.isFileUri("morning.gpx"))
        // A scheme must start with a letter, so this is not one either.
        assertFalse(SharedUriArg.isFileUri("123:morning.gpx"))
    }
}
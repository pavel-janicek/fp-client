package com.fpclient.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the activity share URL and the social share text. */
class ShareLinksTest {

    @Test
    fun publicActivityUrl_joinsBaseAndActivityPath() {
        assertEquals(
            "https://fitpub.social/activities/8f2b6f9e-1",
            ShareLinks.publicActivityUrl("https://fitpub.social", "8f2b6f9e-1"),
        )
    }

    @Test
    fun publicActivityUrl_toleratesTrailingSlashAndInstanceSubPath() {
        assertEquals(
            "https://example.test/fitpub/activities/abc",
            ShareLinks.publicActivityUrl("https://example.test/fitpub/", "abc"),
        )
    }

    @Test
    fun activityShareText_matchesTheWordingAndContainsTheFullLink() {
        val text = ShareLinks.activityShareText(
            "Morning run",
            "https://fitpub.social",
            "8f2b6f9e-1",
        )
        assertEquals(
            "I just finished: Morning run check it out at: https://fitpub.social/activities/8f2b6f9e-1",
            text,
        )
    }
}

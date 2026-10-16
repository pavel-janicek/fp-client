package com.fpclient.android.data.dto

import com.fpclient.android.util.TextLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackMessageTest {
    @Test
    fun `the client note is appended after the trimmed message`() {
        val sent = FeedbackMessage.withClientNote("  The heatmap does not load.\n")
        assertTrue(sent.startsWith("The heatmap does not load.\n\n---\n"))
        assertTrue(sent.endsWith(FeedbackMessage.CLIENT_NOTE))
        assertTrue(sent.contains("https://github.com/pavel-janicek/fp-client/issues"))
    }

    @Test
    fun `a message at the user limit still fits the server limit with the note`() {
        val longest = "x".repeat(FeedbackMessage.MAX_USER_LENGTH)
        assertTrue(FeedbackMessage.withClientNote(longest).length <= TextLimits.FEEDBACK_MESSAGE)
    }

    @Test
    fun `the user limit is a whole hundred`() {
        assertEquals(0, FeedbackMessage.MAX_USER_LENGTH % 100)
    }
}

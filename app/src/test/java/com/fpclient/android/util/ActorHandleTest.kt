package com.fpclient.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActorHandleTest {

    @Test
    fun `plain username passes through unchanged`() {
        assertEquals("alice", ActorHandle.normalizeToUsername("alice", "https://fitpub.example"))
    }

    @Test
    fun `leading at sign is stripped`() {
        assertEquals("alice", ActorHandle.normalizeToUsername("@alice", "https://fitpub.example"))
    }

    @Test
    fun `full handle on the same instance collapses to the local username`() {
        // The server routes any user@host path segment into WebFinger discovery and
        // answers "user not found" — even when the account lives on our own instance.
        assertEquals(
            "alice",
            ActorHandle.normalizeToUsername("@alice@fitpub.example", "https://fitpub.example"),
        )
    }

    @Test
    fun `host comparison is case-insensitive`() {
        assertEquals(
            "alice",
            ActorHandle.normalizeToUsername("@alice@FitPub.Example", "https://fitpub.example"),
        )
    }

    @Test
    fun `full handle on another instance is kept as a handle`() {
        assertEquals(
            "@alice@other.example",
            ActorHandle.normalizeToUsername("@alice@other.example", "https://fitpub.example"),
        )
    }

    @Test
    fun `full handle without a configured server is kept as a handle`() {
        assertEquals(
            "@alice@other.example",
            ActorHandle.normalizeToUsername("@alice@other.example", null),
        )
    }

    @Test
    fun `blank input yields null`() {
        assertNull(ActorHandle.normalizeToUsername(null, "https://fitpub.example"))
        assertNull(ActorHandle.normalizeToUsername("", "https://fitpub.example"))
        assertNull(ActorHandle.normalizeToUsername("   ", "https://fitpub.example"))
    }
}

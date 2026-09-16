package com.fpclient.android.data.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server's GET /{username}/follow-status answers with
 * `{"isFollowing": bool, "status": "NONE|PENDING|ACCEPTED|REJECTED"}` — the boolean
 * convenience flags (canUnfollow, isFollowRequestPending, …) it does NOT send. These
 * tests pin the parsing and the derived [FollowStatusDto.isAccepted]/[FollowStatusDto.isPending]
 * flags that the Follow / Request-to-follow buttons branch on.
 */
class FollowStatusDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `server payload with status PENDING yields isPending`() {
        val dto = json.decodeFromString<FollowStatusDto>(
            """{"isFollowing": false, "status": "PENDING"}""",
        )
        assertTrue(dto.isPending)
        assertFalse(dto.isAccepted)
    }

    @Test
    fun `server payload with status ACCEPTED yields isAccepted`() {
        val dto = json.decodeFromString<FollowStatusDto>(
            """{"isFollowing": true, "status": "ACCEPTED"}""",
        )
        assertTrue(dto.isAccepted)
        assertFalse(dto.isPending)
    }

    @Test
    fun `isFollowing true counts as accepted even without the status string`() {
        val dto = json.decodeFromString<FollowStatusDto>("""{"isFollowing": true}""")
        assertTrue(dto.isAccepted)
        assertFalse(dto.isPending)
    }

    @Test
    fun `server payload with status NONE means neither`() {
        val dto = json.decodeFromString<FollowStatusDto>(
            """{"isFollowing": false, "status": "NONE"}""",
        )
        assertFalse(dto.isAccepted)
        assertFalse(dto.isPending)
    }

    @Test
    fun `empty payload means neither`() {
        val dto = json.decodeFromString<FollowStatusDto>("{}")
        assertFalse(dto.isAccepted)
        assertFalse(dto.isPending)
    }

    @Test
    fun `richer payloads with the legacy boolean fields still parse`() {
        val dto = json.decodeFromString<FollowStatusDto>(
            """{"isFollowing": false, "isFollowRequestPending": true, "canUnfollow": false}""",
        )
        assertTrue(dto.isPending)
        assertFalse(dto.isAccepted)
    }

    @Test
    fun `status comparison is case-insensitive`() {
        val dto = json.decodeFromString<FollowStatusDto>(
            """{"isFollowing": false, "status": "accepted"}""",
        )
        assertTrue(dto.isAccepted)
    }

    @Test
    fun `round trips the known server shape`() {
        assertEquals(
            FollowStatusDto(isFollowing = false, status = "PENDING"),
            json.decodeFromString<FollowStatusDto>("""{"isFollowing": false, "status": "PENDING"}"""),
        )
    }
}

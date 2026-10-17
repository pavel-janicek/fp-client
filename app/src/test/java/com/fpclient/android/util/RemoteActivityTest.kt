package com.fpclient.android.util

import com.fpclient.android.data.dto.ActivityDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteActivityTest {

    @Test
    fun origin_parsesFitPubActivityUri() {
        val origin = RemoteActivity.origin("https://makni.cz/activities/cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7")!!
        assertEquals("https://makni.cz", origin.serverUrl)
        assertEquals("cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7", origin.activityId)
        assertEquals("https://makni.cz/activities/cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7", origin.pageUrl)
        assertEquals("makni.cz", origin.host)
    }

    @Test
    fun origin_keepsNonDefaultPort() {
        val origin = RemoteActivity.origin("https://fit.example:8443/activities/cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7")!!
        assertEquals("https://fit.example:8443", origin.serverUrl)
    }

    @Test
    fun origin_rejectsWhatIsNotAFitPubActivity() {
        assertNull(RemoteActivity.origin(null))
        assertNull(RemoteActivity.origin(""))
        // Plain http would leak nothing, but FitPub federation is https-only.
        assertNull(RemoteActivity.origin("http://makni.cz/activities/cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7"))
        // Other fediverse software / other object kinds keep the Custom Tab.
        assertNull(RemoteActivity.origin("https://mastodon.social/@someone/113000000000000000"))
        assertNull(RemoteActivity.origin("https://makni.cz/activities/like/cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7"))
        assertNull(RemoteActivity.origin("https://makni.cz/activities/not-a-uuid"))
        assertNull(RemoteActivity.origin("https://user:pw@makni.cz/activities/cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7"))
    }

    @Test
    fun isActor_matchesOnlyTheSignedInUserOnTheHomeInstance() {
        assertTrue(RemoteActivity.isActor("https://fitpub.social/users/pavel", "https://fitpub.social", "pavel"))
        assertTrue(RemoteActivity.isActor("https://fitpub.social/users/pavel", "https://fitpub.social/", "pavel"))
        // Same username on the origin instance is somebody else.
        assertFalse(RemoteActivity.isActor("https://makni.cz/users/pavel", "https://fitpub.social", "pavel"))
        assertFalse(RemoteActivity.isActor("https://fitpub.social/users/pavel2", "https://fitpub.social", "pavel"))
        assertFalse(RemoteActivity.isActor(null, "https://fitpub.social", "pavel"))
        assertFalse(RemoteActivity.isActor("https://fitpub.social/users/pavel", "https://fitpub.social", ""))
    }

    @Test
    fun withReaction_movesTheViewersReaction() {
        val counts = mapOf("👍" to 2L, "❤️" to 1L)
        assertEquals(mapOf("👍" to 3L, "❤️" to 1L), RemoteActivity.withReaction(counts, null, "👍"))
        assertEquals(mapOf("👍" to 1L, "❤️" to 2L), RemoteActivity.withReaction(counts, "👍", "❤️"))
        assertEquals(mapOf("👍" to 2L), RemoteActivity.withReaction(counts, "❤️", null))
        assertEquals(counts, RemoteActivity.withReaction(counts, "👍", "👍"))
    }

    @Test
    fun withReaction_neverGoesNegative() {
        // The origin may not have received the earlier reaction yet.
        assertEquals(emptyMap<String, Long>(), RemoteActivity.withReaction(null, "👍", null))
    }

    @Test
    fun originDetail_authorIsNotMistakenForTheSameUsernameAtHome() {
        // Trimmed GET https://makni.cz/api/web/activities/{id}: the origin reports its own
        // local username, which may also exist on the home instance.
        val activity = Json { ignoreUnknownKeys = true }.decodeFromString<ActivityDto>(
            """
            {
              "id": "cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7",
              "visibility": "PUBLIC",
              "username": "paveljanicek",
              "actorUri": "https://makni.cz/users/paveljanicek",
              "simplifiedTrack": {"type": "LineString", "coordinates": [[17.29, 50.22], [17.30, 50.23]]},
              "reactionCounts": {"👍": 1},
              "boostEligible": false,
              "isLocal": true
            }
            """.trimIndent(),
        )
        assertEquals(
            "@paveljanicek@makni.cz",
            ActorHandle.normalizeToUsername(activity.fullHandle, "https://fitpub.social"),
        )
        // ...while the same handle seen from makni.cz itself collapses to the local name.
        assertEquals("paveljanicek", ActorHandle.normalizeToUsername(activity.fullHandle, "https://makni.cz"))
    }
}

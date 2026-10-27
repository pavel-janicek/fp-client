package com.fpclient.android.util

import com.fpclient.android.data.dto.ActorDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteProfileTest {

    private fun actor(uri: String?, type: String? = "FITPUB", local: Boolean = false) =
        ActorDto(actorUri = uri, instanceType = type, local = local)

    @Test
    fun origin_readsAFitPubActor() {
        val origin = RemoteProfile.origin(actor("https://fitpub.example/users/runner_1"))!!
        assertEquals("https://fitpub.example", origin.serverUrl)
        assertEquals("runner_1", origin.username)
        assertEquals("fitpub.example", origin.host)
        assertEquals(
            "https://fitpub.example/activities/cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7",
            origin.activityUri("cb00a40c-b23e-4a00-ba9e-6b2d384fb9e7"),
        )
    }

    @Test
    fun origin_keepsNonDefaultPort() {
        assertEquals("https://fit.example:8443", RemoteProfile.origin(actor("https://fit.example:8443/users/a"))!!.serverUrl)
    }

    @Test
    fun origin_onlyForFitPubServers() {
        // Mastodon actors look the same but their server has no FitPub API.
        assertNull(RemoteProfile.origin(actor("https://mastodon.social/users/someone", type = "NON_FITPUB")))
        assertNull(RemoteProfile.origin(actor("https://fitpub.example/users/a", type = "UNKNOWN")))
        assertNull(RemoteProfile.origin(actor("https://fitpub.example/users/a", type = null)))
    }

    @Test
    fun origin_rejectsLocalAndMalformedActors() {
        assertNull(RemoteProfile.origin(null))
        assertNull(RemoteProfile.origin(actor("https://fitpub.example/users/a", local = true)))
        assertNull(RemoteProfile.origin(actor(null)))
        assertNull(RemoteProfile.origin(actor("http://fitpub.example/users/a")))
        assertNull(RemoteProfile.origin(actor("https://fitpub.example/@a")))
        assertNull(RemoteProfile.origin(actor("https://fitpub.example/users/a/outbox")))
        assertNull(RemoteProfile.origin(actor("https://user:pw@fitpub.example/users/a")))
        assertNull(RemoteProfile.origin(actor("https://fitpub.example/users/..%2Fadmin")))
    }
}

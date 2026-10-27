package com.fpclient.android.ui.profile

import com.fpclient.android.data.dto.UserDto
import org.junit.Assert.assertEquals
import org.junit.Test

class FollowListTargetTest {

    private val home = "https://makni.cz"

    @Test
    fun aLocalFollowerIsFollowedByUsername() {
        val local = UserDto(username = "pavel")
        assertEquals("pavel", FollowListViewModel.followTarget(local, home))
    }

    @Test
    fun aRemoteFollowerKeepsTheirServer() {
        // Used to send just "runner" — "User not found" on makni.cz, or a local namesake.
        val remote = UserDto(username = "runner", actorUri = "https://fitpub.example/users/runner", domain = "fitpub.example")
        assertEquals("runner@fitpub.example", FollowListViewModel.followTarget(remote, home))
        val mastodon = UserDto(username = "someone", handle = "someone@mastodon.social")
        assertEquals("someone@mastodon.social", FollowListViewModel.followTarget(mastodon, home))
    }

    @Test
    fun aFullHandleOnThisInstanceIsTheLocalUser() {
        val sameHost = UserDto(username = "pavel", domain = "makni.cz")
        assertEquals("pavel", FollowListViewModel.followTarget(sameHost, home))
    }
}

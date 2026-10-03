package com.fpclient.android.ui.notifications

import com.fpclient.android.data.dto.NotificationTypes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the FOLLOW_REQUEST row action logic against the server contract: the notification
 * is never deleted — accepting/rejecting only flips `followRequestPending` — so the row
 * must drop its buttons once the request is no longer pending, while a decision made in
 * this session wins over the flag so the label survives the post-action refresh.
 */
class FollowRequestActionTest {

    private fun action(
        type: String? = NotificationTypes.FOLLOW_REQUEST,
        actorUsername: String? = "sam",
        followRequestPending: Boolean? = true,
        outcome: FollowRequestOutcome? = null,
    ) = followRequestAction(type, actorUsername, followRequestPending, outcome)

    @Test
    fun `pending request shows accept and reject buttons`() {
        assertEquals(FollowRequestAction.BUTTONS, action())
    }

    @Test
    fun `request already handled elsewhere shows no buttons`() {
        assertEquals(
            FollowRequestAction.NONE,
            action(followRequestPending = false),
        )
    }

    @Test
    fun `accepted this session shows the accepted label even right after the refresh`() {
        assertEquals(
            FollowRequestAction.ACCEPTED,
            action(followRequestPending = false, outcome = FollowRequestOutcome.ACCEPTED),
        )
    }

    @Test
    fun `rejected this session shows the rejected label even right after the refresh`() {
        assertEquals(
            FollowRequestAction.REJECTED,
            action(followRequestPending = false, outcome = FollowRequestOutcome.REJECTED),
        )
    }

    @Test
    fun `session outcome wins while the server still reports pending`() {
        assertEquals(
            FollowRequestAction.ACCEPTED,
            action(followRequestPending = true, outcome = FollowRequestOutcome.ACCEPTED),
        )
    }

    @Test
    fun `missing actor username hides the buttons because there is nothing to post to`() {
        assertEquals(
            FollowRequestAction.NONE,
            action(actorUsername = null, followRequestPending = true),
        )
    }

    @Test
    fun `instance without the pending flag keeps the buttons`() {
        assertEquals(
            FollowRequestAction.BUTTONS,
            action(followRequestPending = null),
        )
    }

    @Test
    fun `other notification types never get an action area`() {
        assertEquals(
            FollowRequestAction.NONE,
            action(type = NotificationTypes.USER_FOLLOWED, followRequestPending = null),
        )
    }
}

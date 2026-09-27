package com.fpclient.android.notifications

import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.dto.NotificationTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording used for the in-app notification rows is reused verbatim by the background
 * push notification (Iteration 8f), so it is pinned here per server type.
 */
class NotificationTextTest {

    @Test
    fun describe_phrasesEveryDeliverableType() {
        assertEquals(
            "Sam reacted ❤️ to your activity",
            NotificationText.describe(NotificationDto(type = NotificationTypes.ACTIVITY_LIKED, actorDisplayName = "Sam")),
        )
        assertEquals(
            "Sam reacted 👍 to your activity",
            NotificationText.describe(
                NotificationDto(
                    type = NotificationTypes.ACTIVITY_LIKED,
                    actorDisplayName = "Sam",
                    reactionEmoji = "👍",
                ),
            ),
        )
        assertEquals(
            "Sam commented: \"nice one\"",
            NotificationText.describe(
                NotificationDto(
                    type = NotificationTypes.ACTIVITY_COMMENTED,
                    actorDisplayName = "Sam",
                    commentText = "nice one",
                ),
            ),
        )
        // The server has used both enum names for the comment event.
        assertEquals(
            "Sam commented: \"nice one\"",
            NotificationText.describe(
                NotificationDto(
                    type = NotificationTypes.COMMENT_ADDED,
                    actorDisplayName = "Sam",
                    commentText = "nice one",
                ),
            ),
        )
        assertEquals(
            "Sam shared your activity",
            NotificationText.describe(NotificationDto(type = NotificationTypes.ACTIVITY_SHARED, actorDisplayName = "Sam")),
        )
        assertEquals(
            "Sam started following you",
            NotificationText.describe(NotificationDto(type = NotificationTypes.USER_FOLLOWED, actorDisplayName = "Sam")),
        )
        assertEquals(
            "Sam requested to follow you",
            NotificationText.describe(NotificationDto(type = NotificationTypes.FOLLOW_REQUEST, actorDisplayName = "Sam")),
        )
        assertEquals(
            "Sam accepted your follow request",
            NotificationText.describe(
                NotificationDto(type = NotificationTypes.FOLLOW_REQUEST_ACCEPTED, actorDisplayName = "Sam"),
            ),
        )
    }

    @Test
    fun describe_fallsBackToAGenericPhraseForTypesWithoutWording() {
        assertEquals(
            "Sam interacted with you",
            NotificationText.describe(NotificationDto(type = NotificationTypes.SYSTEM_ANNOUNCEMENT, actorDisplayName = "Sam")),
        )
    }

    /**
     * The server's enum is `FOLLOW_ACCEPTED`. The app only knew
     * `FOLLOW_REQUEST_ACCEPTED`, so a real "accepted your follow request" notification fell
     * through to the generic sentence.
     */
    @Test
    fun describe_understandsTheServersFollowAcceptedName() {
        assertEquals(
            "Sam accepted your follow request",
            NotificationText.describe(
                NotificationDto(type = NotificationTypes.FOLLOW_ACCEPTED, actorDisplayName = "Sam"),
            ),
        )
    }

    /** The instance's own "something finished" notices carry no actor at all. */
    @Test
    fun describe_phrasesTheActorsOwnCompletionNotices() {
        assertEquals(
            "Your data export is ready to download",
            NotificationText.describe(NotificationDto(type = NotificationTypes.DATA_EXPORT_READY)),
        )
        assertEquals(
            "Your batch import has finished",
            NotificationText.describe(NotificationDto(type = NotificationTypes.BATCH_IMPORT_COMPLETED)),
        )
        assertEquals(
            "There is a reply to your feedback",
            NotificationText.describe(NotificationDto(type = NotificationTypes.FEEDBACK_RECEIVED)),
        )
    }

    @Test
    fun describe_phrasesAMention() {
        assertEquals(
            "Sam mentioned you: \"ping\"",
            NotificationText.describe(
                NotificationDto(
                    type = NotificationTypes.MENTIONED_IN_COMMENT,
                    actorDisplayName = "Sam",
                    commentText = "ping",
                ),
            ),
        )
    }

    @Test
    fun actorLabel_prefersTheDisplayNameThenTheHandleThenSomeone() {
        assertEquals(
            "Sam",
            NotificationText.actorLabel(NotificationDto(actorDisplayName = "Sam", actorUsername = "sam")),
        )
        assertEquals("sam", NotificationText.actorLabel(NotificationDto(actorUsername = "sam")))
        assertEquals(NotificationText.UNKNOWN_ACTOR, NotificationText.actorLabel(NotificationDto()))
    }

    @Test
    fun describe_handlesAFederatedActorWithoutADisplayName() {
        assertEquals(
            "@sam@instance.test started following you",
            NotificationText.describe(
                NotificationDto(
                    type = NotificationTypes.USER_FOLLOWED,
                    actorUsername = "@sam@instance.test",
                ),
            ),
        )
    }

    // ---------------------------------------------------------------- federation
    //
    // Regression: a federated follower was announced as a bare local username and, when
    // tapped, navigated to a local profile that does not exist ("User not found"). The server
    // always sends `actorUri`, so the instance is recoverable - it just was not being used.

    private fun remoteFollower(displayName: String? = null) = NotificationDto(
        type = NotificationTypes.USER_FOLLOWED,
        actorUsername = "sam",
        actorUri = "https://makni.cz/users/sam",
        actorDisplayName = displayName,
        actorLocal = false,
    )

    @Test
    fun aFederatedFollowerKeepsTheirInstanceInTheText() {
        val text = NotificationText.describe(remoteFollower())
        assertTrue("must not read as a local user: $text", text.contains("sam@makni.cz"))
        assertTrue(text.endsWith("started following you"))
    }

    @Test
    fun aFederatedFollowerWithADisplayNameStillShowsWhichInstance() {
        // "Alice started following you" hides the one fact federation exists for.
        val text = NotificationText.describe(remoteFollower(displayName = "Alice"))
        assertTrue("display name should still be used: $text", text.contains("Alice"))
        assertTrue("but the instance must be visible: $text", text.contains("sam@makni.cz"))
    }

    @Test
    fun aLocalFollowerIsNotBuriedUnderItsOwnHostname() {
        val local = NotificationDto(
            type = NotificationTypes.USER_FOLLOWED,
            actorUsername = "sam",
            actorUri = "https://fitpub.example/users/sam",
            actorLocal = true,
        )
        // With a display name: just the name.
        assertEquals(
            "Sam started following you",
            NotificationText.describe(local.copy(actorDisplayName = "Sam")),
        )
        // Without one: the plain handle, but not `@sam@<the reader's own host>`.
        assertEquals("@sam started following you", NotificationText.describe(local))
    }

    @Test
    fun actorHandleIsTheFullFederatedHandleTheProfileScreenCanResolve() {
        // This is the value the row navigates with. It must be @user@host, because the local
        // profile endpoint resolves local usernames only.
        assertEquals("@sam@makni.cz", NotificationText.actorHandle(remoteFollower()))
        assertEquals(
            "@sam@fitpub.example",
            NotificationText.actorHandle(
                NotificationDto(actorUsername = "sam", actorUri = "https://fitpub.example/users/sam"),
            ),
        )
    }

    @Test
    fun actorHandleNeverProducesABareLocalPartForARemoteActor() {
        // The exact failure: a remote actor rendered/navigated as "@sam", which lands on a
        // non-existent local profile.
        val handle = NotificationText.actorHandle(remoteFollower())
        assertNotNull(handle)
        // A second '@' is what separates a real handle from a bare local name.
        assertTrue("must be a full handle, was $handle", handle!!.count { it == '@' } == 2)
        assertEquals("@sam@makni.cz", handle)
    }

    @Test
    fun actorHandleDegradesGracefullyWhenTheServerSendsNoActorUri() {
        assertEquals("@sam", NotificationText.actorHandle(NotificationDto(actorUsername = "sam")))
        assertEquals("@sam", NotificationText.actorHandle(NotificationDto(actorUsername = "@sam")))
        assertNull(NotificationText.actorHandle(NotificationDto()))
    }

    @Test
    fun aRowWithNoActorAtAllStillRenders() {
        val orphan = NotificationDto(type = NotificationTypes.USER_FOLLOWED)
        assertNull("there is no actor to navigate to", NotificationText.actorHandle(orphan))
        assertEquals("Someone started following you", NotificationText.describe(orphan))
    }

    @Test
    fun everyDeliverableTypeQualifiesAFederatedActor() {
        val remote = mapOf(
            NotificationTypes.ACTIVITY_LIKED to "reacted",
            NotificationTypes.ACTIVITY_COMMENTED to "commented",
            NotificationTypes.ACTIVITY_SHARED to "shared",
            NotificationTypes.USER_FOLLOWED to "started following you",
            NotificationTypes.FOLLOW_REQUEST to "requested to follow you",
        )
        remote.forEach { (type, tail) ->
            val text = NotificationText.describe(remoteFollower().copy(type = type))
            assertTrue("$type lost the instance: $text", text.contains("sam@makni.cz"))
            assertTrue("$type lost its wording: $text", text.contains(tail))
        }
    }
}

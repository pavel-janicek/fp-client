package com.fpclient.android.notifications

import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.dto.NotificationTypes
import org.junit.Assert.assertEquals
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
}

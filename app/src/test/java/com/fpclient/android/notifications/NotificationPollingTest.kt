package com.fpclient.android.notifications

import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.dto.NotificationTypes
import com.fpclient.android.data.network.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two rules the background poll depends on (Iteration 8f): which rows of a fetched
 * page count as *new*, and how a burst of them is coalesced into the single summary
 * notification. Both are plain functions precisely so they can be tested without WorkManager.
 *
 * Pages are newest-first, exactly as `GET api/web/notifications` returns them; the stored
 * cursor is the id of the newest row the previous poll already delivered.
 */
class NotificationPollingTest {

    private fun row(
        id: String?,
        type: String,
        read: Boolean = false,
        activityTitle: String? = null,
        actor: String? = "Sam",
    ) = NotificationDto(
        id = id,
        type = type,
        read = read,
        activityTitle = activityTitle,
        actorDisplayName = actor,
    )

    private fun like(id: String, read: Boolean = false, title: String? = null) =
        row(id, NotificationTypes.ACTIVITY_LIKED, read, title)

    // ------------------------------------------------------------------ new-row diffing

    @Test
    fun newRows_returnsOnlyTheDeliverableUnreadRowsAboveTheCursor() {
        val page = listOf(
            like("n5", title = "Evening run"),                        // new
            row("n4", NotificationTypes.ACTIVITY_SHARED, read = true), // new but already read
            like("n3"),                                               // the cursor itself
            row("n2", NotificationTypes.USER_FOLLOWED),               // older than the cursor
        )

        val fresh = NotificationPolling.newRows(page, lastSeenId = "n3")

        assertEquals(listOf("n5"), fresh.map { it.id })
    }

    @Test
    fun newRows_theCursorRowItselfIsNeverDelivered() {
        // The cursor is the newest delivered row: it is excluded, so it can never be announced
        // twice, while the rows below it are excluded as already seen.
        val page = listOf(row("n4", NotificationTypes.FOLLOW_REQUEST), like("n3"), like("n2"))

        assertTrue(NotificationPolling.newRows(page, lastSeenId = "n4").isEmpty())
    }

    @Test
    fun newRows_treatsTheWholePageAsNewWhenTheCursorIsNoLongerOnIt() {
        // The cursor row was deleted (or read elsewhere), or more than a page arrived since
        // the last poll — everything still unread on the page is new.
        val page = listOf(
            row("n9", NotificationTypes.FOLLOW_REQUEST),
            like("n8"),
            row("n7", NotificationTypes.COMMENT_ADDED),
        )

        val fresh = NotificationPolling.newRows(page, lastSeenId = "gone")

        assertEquals(listOf("n9", "n8", "n7"), fresh.map { it.id })
    }

    @Test
    fun newRows_withoutAKnownBoundaryTreatsEveryRowAsNew() {
        // The caller (the worker) only passes a null cursor when it deliberately adopted a
        // session's first page already; for an account whose first poll found nothing, this is
        // what makes the very first notification that arrives afterwards show up.
        val page = listOf(like("n3"), like("n2"), like("n1"))

        assertEquals(listOf("n3", "n2", "n1"), NotificationPolling.newRows(page, lastSeenId = null).map { it.id })
        assertEquals(listOf("n3", "n2", "n1"), NotificationPolling.newRows(page, lastSeenId = "  ").map { it.id })
    }

    @Test
    fun newRows_ignoresTypesThatStayInAppOnly() {
        val page = listOf(
            row("n6", NotificationTypes.FOLLOW_REQUEST_ACCEPTED),
            row("n5", NotificationTypes.ACTIVITY_MENTION),
            row("n4", NotificationTypes.PRIVACY_ZONE_TRIGGERED),
            row("n3", NotificationTypes.SYSTEM_ANNOUNCEMENT),
            row("n2", NotificationTypes.QUOTE_CREATED),
            row("n1", NotificationTypes.USER_FOLLOWED),
        )

        val fresh = NotificationPolling.newRows(page, lastSeenId = "cursor")

        assertEquals(listOf("n1"), fresh.map { it.id })
    }

    @Test
    fun newRows_coversEveryDeliverableTypeIncludingBothCommentNames() {
        val page = listOf(
            like("n6"),
            row("n5", NotificationTypes.ACTIVITY_COMMENTED),
            row("n4", NotificationTypes.COMMENT_ADDED),
            row("n3", NotificationTypes.ACTIVITY_SHARED),
            row("n2", NotificationTypes.USER_FOLLOWED),
            row("n1", NotificationTypes.FOLLOW_REQUEST),
        )

        assertEquals(6, NotificationPolling.newRows(page, lastSeenId = "cursor").size)
    }

    @Test
    fun newRows_dropsRowsWithoutAnIdAndDuplicates() {
        val page = listOf(
            row(null, NotificationTypes.ACTIVITY_LIKED),
            like("n4"),
            like("n4"),
            row("  ", NotificationTypes.ACTIVITY_LIKED),
        )

        val fresh = NotificationPolling.newRows(page, lastSeenId = "cursor")

        assertEquals(listOf("n4"), fresh.map { it.id })
    }

    @Test
    fun newRows_handlesARowWithoutAType() {
        val page = listOf(row("n1", type = ""), like("n0"))

        assertEquals(listOf("n0"), NotificationPolling.newRows(page, lastSeenId = "cursor").map { it.id })
    }

    @Test
    fun newRows_keepsThePageOrderNewestFirst() {
        val page = listOf(like("n3"), like("n2"), like("n1"))

        assertEquals(listOf("n3", "n2"), NotificationPolling.newRows(page, lastSeenId = "n1").map { it.id })
    }

    // ------------------------------------------------------------------ cursor bookkeeping

    @Test
    fun newestId_isTheFirstIdentifiedRowAndNullForAnEmptyPage() {
        assertEquals("n5", NotificationPolling.newestId(listOf(row(null, "X"), like("n5"), like("n4"))))
        assertNull(NotificationPolling.newestId(emptyList()))
        assertNull(NotificationPolling.newestId(listOf(row(null, "X"))))
    }

    // ------------------------------------------------------------------ summary coalescing

    @Test
    fun content_singleRowReadsExactlyLikeTheInAppRow() {
        val item = like("n1", title = "Evening run")

        val content = NotificationPolling.content(listOf(item), unreadCount = 1)

        assertEquals("Sam reacted ❤️ to your activity", content.title)
        assertEquals("Evening run", content.text)
    }

    @Test
    fun content_singleRowWithoutAnActivityTitleFallsBackToTheGenericHint() {
        val content = NotificationPolling.content(
            listOf(row("n1", NotificationTypes.USER_FOLLOWED)),
            unreadCount = 1,
        )

        assertEquals("Sam started following you", content.title)
        assertEquals("Tap to open your notifications", content.text)
    }

    @Test
    fun content_coalescesABurstIntoOneSummaryCarryingTheUnreadCount() {
        val items = listOf(
            like("n3", title = "Evening run"),
            row("n2", NotificationTypes.ACTIVITY_SHARED),
            row("n1", NotificationTypes.USER_FOLLOWED),
        )

        val content = NotificationPolling.content(items, unreadCount = 7)

        // One notification for the whole burst; the server's count also covers rows the user
        // has not seen, so it wins over the burst size.
        assertEquals("7 unread notifications", content.title)
        assertEquals("Sam reacted ❤️ to your activity and 2 more", content.text)
    }

    @Test
    fun content_neverReportsFewerThanTheBurstItself() {
        // unreadCount can lag (count endpoint stale, or the repository fallback) — the summary
        // must never claim fewer items than it just delivered.
        val items = listOf(like("n2"), like("n1"))

        val content = NotificationPolling.content(items, unreadCount = 0)

        assertEquals("2 unread notifications", content.title)
    }

    @Test
    fun content_withoutAnyRowStillProducesSomethingPostable() {
        val content = NotificationPolling.content(emptyList(), unreadCount = 0)

        assertEquals("New notification", content.title)
        assertEquals("Tap to open your notifications", content.text)
    }

    @Test
    fun summaryText_appendsOneMoreForTheSecondItemAndTheRestForBiggerBursts() {
        val two = listOf(like("n2"), like("n1"))
        val four = listOf(like("n4"), like("n3"), like("n2"), like("n1"))

        assertEquals("Sam reacted ❤️ to your activity and 1 more", NotificationPolling.summaryText(two))
        assertEquals("Sam reacted ❤️ to your activity and 3 more", NotificationPolling.summaryText(four))
        assertEquals("", NotificationPolling.summaryText(emptyList()))
    }

    // ------------------------------------------------------------------ retry policy

    @Test
    fun shouldRetry_neverRetriesAnUnauthorizedPoll() {
        // A 401 means the interceptor already cleared the stored session; retrying would loop
        // on "Unauthorized" until the user signs in again.
        assertFalse(NotificationPolling.shouldRetry(ApiResult.Error("Unauthorized", 401)))
    }

    @Test
    fun shouldRetry_doesNotRetryOtherClientErrorsButDoesRetryTheServer() {
        assertFalse(NotificationPolling.shouldRetry(ApiResult.Error("Forbidden", 403)))
        assertFalse(NotificationPolling.shouldRetry(ApiResult.Error("Not found", 404)))
        assertTrue(NotificationPolling.shouldRetry(ApiResult.Error("Boom", 500)))
        assertTrue(NotificationPolling.shouldRetry(ApiResult.Error("Gateway", 503)))
    }

    @Test
    fun shouldRetry_retriesTransportFailuresWithoutAStatus() {
        assertTrue(NotificationPolling.shouldRetry(ApiResult.Error("Network error")))
    }
}

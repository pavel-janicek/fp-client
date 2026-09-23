package com.fpclient.android.notifications

import com.fpclient.android.data.dto.NotificationDto
import com.fpclient.android.data.dto.NotificationTypes
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.session.SessionStore

/** The text of the local notification a poll produces. */
data class PushContent(val title: String, val text: String)

/**
 * Pure, Android-free core of background notification delivery (Iteration 8f): which rows of a
 * fetched page are "new" relative to the stored cursor, and how a burst of them is coalesced
 * into the single summary notification.
 *
 * Deliberately free of framework types (no WorkManager, no NotificationCompat) so the diffing
 * and coalescing rules — the error-prone part — are unit-tested on the JVM.
 */
object NotificationPolling {

    /** Server types worth waking the user for. Everything else stays in-app only. */
    val DELIVERABLE_TYPES: Set<String> = setOf(
        NotificationTypes.ACTIVITY_LIKED,
        NotificationTypes.ACTIVITY_COMMENTED,
        // The server has used both names for the comment event (see the in-app list, which
        // phrases them identically), so a comment is announced whichever one arrives.
        NotificationTypes.COMMENT_ADDED,
        NotificationTypes.ACTIVITY_SHARED,
        NotificationTypes.USER_FOLLOWED,
        NotificationTypes.FOLLOW_REQUEST,
    )

    /**
     * The rows of [page] (newest-first, as the API returns them) that deserve a push:
     * everything newer than [lastSeenId], limited to [DELIVERABLE_TYPES], still unread and
     * carrying an id.
     *
     * [lastSeenId] is the newest id the previous poll already delivered. A null/blank one means
     * "no known boundary" and every row on the page counts as new — that is the right answer
     * for an account whose first poll legitimately found nothing (so the first notification to
     * arrive afterwards is still announced). Skipping the *backlog* of a session that has never
     * been polled before is the caller's decision, because only the caller knows whether the
     * cursor is missing or merely empty (see `NotificationPollWorker`). When the cursor is no
     * longer on the page at all (the row was read or deleted elsewhere, or more than a page
     * arrived) the whole page counts as new, which still collapses into one summary.
     */
    fun newRows(page: List<NotificationDto>, lastSeenId: String?): List<NotificationDto> {
        val cut = if (lastSeenId.isNullOrBlank()) -1 else page.indexOfFirst { it.id == lastSeenId }
        val fresh = if (cut >= 0) page.take(cut) else page
        return fresh
            .filter { !it.id.isNullOrBlank() }
            .filter { it.type in DELIVERABLE_TYPES }
            .filter { !it.read }
            .distinctBy { it.id }
    }

    /** Cursor value to store after a successful poll: the newest id on the page, if any. */
    fun newestId(page: List<NotificationDto>): String? =
        page.firstOrNull { !it.id.isNullOrBlank() }?.id

    /**
     * Identity of the session a cursor belongs to. A single device can point at and sign into
     * any FitPub instance, so the cursor must not leak across accounts: switching account must
     * neither replay the previous account's rows nor swallow the new account's first page.
     * The URL is normalized so `https://x/` and `x` are the same instance.
     */
    fun cursorOwner(serverUrl: String, username: String): String =
        "${SessionStore.normalizeServerUrl(serverUrl)}|${username.trim().lowercase()}"

    /**
     * Title + body of the delivered notification.
     *
     * A single new row is announced with its own wording (identical to the list row), so the
     * user sees who did what without opening the app. A burst is coalesced into one summary
     * carrying the unread count — the server's count when it is larger than the burst, since
     * it also covers rows the user never saw.
     */
    fun content(items: List<NotificationDto>, unreadCount: Long): PushContent {
        val head = items.firstOrNull()
        if (items.size <= 1) {
            val title = head?.let(NotificationText::describe) ?: "New notification"
            val text = head?.activityTitle?.takeIf { it.isNotBlank() }
                ?: "Tap to open your notifications"
            return PushContent(title, text)
        }
        val count = maxOf(unreadCount, items.size.toLong())
        return PushContent("$count unread notifications", summaryText(items))
    }

    /** The newest row's wording plus how many more the burst contained. */
    fun summaryText(items: List<NotificationDto>): String {
        if (items.isEmpty()) return ""
        val head = NotificationText.describe(items.first())
        val more = items.size - 1
        return when {
            more == 0 -> head
            more == 1 -> "$head and 1 more"
            else -> "$head and $more more"
        }
    }

    /**
     * Whether a failed poll is worth another attempt.
     *
     * A 401 is never retried: the session interceptor already cleared the stored token, so
     * retrying would only loop on "Unauthorized" until the user signs in again. Client errors
     * (400/403/404 …) are the server's final answer for this poll and are not retried either;
     * transport failures and 5xx are.
     */
    fun shouldRetry(error: ApiResult.Error): Boolean =
        error.statusCode != 401 && (error.statusCode == null || error.statusCode >= 500)
}

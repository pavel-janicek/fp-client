package com.fpclient.android.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.fpclient.android.FitPubApplication
import com.fpclient.android.data.network.ApiResult
import java.util.concurrent.TimeUnit

/**
 * Background notification polling (Iteration 8f) — the universal delivery path that needs no
 * extra server infrastructure: any FitPub instance that serves `GET api/web/notifications`
 * gets push-like delivery on the device.
 *
 * Every run fetches the first page of notifications for the signed-in session, diffs it
 * against the cursor stored in DataStore and posts a local notification for the new
 * deliverable rows (coalesced into one summary). Guests and signed-out users are skipped
 * outright, and a 401 is never retried — the API interceptor already cleared the stored
 * token at that point, so looping would only repeat "Unauthorized" until the user signs in.
 *
 * Delivery is *eventual*, not instant: WorkManager (and Doze) decide when a run actually
 * happens, which the Settings → Push section states plainly.
 */
class NotificationPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? FitPubApplication)?.container ?: return Result.success()
        val store = container.notificationPollStore

        val session = container.sessionStore.currentSession()
        // Signed-in only: guests and signed-out users have no personal notification feed.
        if (!session.isLoggedIn) return Result.success()

        val owner = NotificationPolling.cursorOwner(session.serverUrl, session.username)
        val cursor = store.cursorFor(owner)

        // The app's paging is 0-based (Spring `Pageable`), so the first page is page 0 —
        // the same call the in-app list makes.
        val result = container.notificationRepository.list(page = 0)
        when (result) {
            is ApiResult.Success -> {
                val page = result.data
                // A null cursor means this session has never been polled: adopt the whole page
                // as "already seen" instead of pushing the backlog of a freshly signed-in
                // account at once. A *non-null* cursor with a null lastSeenId, by contrast, is
                // an account whose earlier polls legitimately found nothing — its first
                // arriving notification must be announced (newRows treats a null boundary as
                // "everything on the page is new").
                if (cursor == null) {
                    store.recordPoll(owner, NotificationPolling.newestId(page))
                    return Result.success()
                }
                val fresh = NotificationPolling.newRows(page, cursor.lastSeenId)
                if (fresh.isNotEmpty()) {
                    val unread = (container.notificationRepository.unreadCount() as? ApiResult.Success)?.data
                        ?: fresh.size.toLong()
                    PushNotifications.post(applicationContext, NotificationPolling.content(fresh, unread))
                }
                store.recordPoll(owner, NotificationPolling.newestId(page) ?: cursor.lastSeenId)
                return Result.success()
            }

            is ApiResult.Error -> return if (NotificationPolling.shouldRetry(result)) Result.retry()
            else Result.success()
        }
    }

    companion object {

        /** Unique name of the periodic poll; re-enqueuing with KEEP never resets the schedule. */
        const val WORK_NAME = "fitpub_notification_poll"

        /**
         * Poll interval. WorkManager's floor is 15 minutes; 30 is chosen because the feature is
         * explicitly "eventual" — halving the request rate costs the user nothing noticeable
         * while being kinder to small self-hosted instances, and Doze defers runs past this
         * anyway on an idle device.
         */
        private const val INTERVAL_MINUTES = 30L

        /**
         * Ensures the periodic poll is scheduled (idempotent). Called at app start and from
         * Settings; [ExistingPeriodicWorkPolicy.KEEP] leaves an already-scheduled poll alone,
         * so opening the app never shifts the next run farther away.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        // No point waking the radio without a connection; WorkManager will run
                        // the poll as soon as one is available.
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Stops the poll (used when the feature is not applicable, e.g. never signed in). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

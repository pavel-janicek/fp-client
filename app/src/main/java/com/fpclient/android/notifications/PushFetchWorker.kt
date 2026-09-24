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
 * The Iteration 8h mailbox check — fetches queued push messages from the user's RFC 8030
 * relay, decrypts them on-device (RFC 8291) and posts a collapsed local notification per
 * payload tag.
 *
 * Runs every 15 minutes (WorkManager's floor — the ~15 min latency PLAN 8h promises),
 * constrained to a live connection with exponential backoff. It is scheduled unconditionally
 * like the 8f poll and simply does nothing until a subscription for the *current* session
 * exists, so signing out or switching accounts can never fetch another account's mailbox.
 *
 * Delivery is at-most-once: the relay clears its queue when the blobs are handed over, so a
 * message that fails to decrypt is dropped rather than retried (never logged either).
 */
class PushFetchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? FitPubApplication)?.container ?: return Result.success()
        val session = container.sessionStore.currentSession()
        if (!session.isLoggedIn) return Result.success()

        val owner = NotificationPolling.cursorOwner(session.serverUrl, session.username)
        val subscription = container.pushSubscriptionStore.subscription.value
        // No subscription for this session: mailbox push is off (or belongs to another
        // account on this device) — the 8f poll remains the delivery path.
        if (subscription == null || subscription.owner != owner) return Result.success()

        return when (val result = container.pushRepository.fetchPayloads(subscription)) {
            is ApiResult.Success -> {
                result.data.forEach { PushNotifications.postItem(applicationContext, it) }
                Result.success()
            }

            is ApiResult.Error -> when (result.statusCode) {
                // The relay expired or removed the mailbox: tear the whole subscription down
                // (server-side unsubscribe + local key wipe) so Settings reflects reality and
                // the instance stops pushing into the void.
                404, 410 -> {
                    container.pushRepository.disable()
                    Result.success()
                }
                // Same retry policy as the 8f poll: transport/5xx failures come back with
                // backoff, terminal answers do not.
                else -> if (NotificationPolling.shouldRetry(result)) Result.retry() else Result.success()
            }
        }
    }

    companion object {

        /** Unique name of the periodic mailbox check; KEEP never shifts an existing schedule. */
        const val WORK_NAME = "fitpub_push_mailbox_check"

        /**
         * 15 minutes — WorkManager's minimum periodic interval and PLAN 8h's promised
         * latency. Doze may defer a run on an idle device, exactly as for the 8f poll.
         */
        private const val INTERVAL_MINUTES = 15L

        /** Ensures the mailbox check is scheduled (idempotent; call at app start and on enable). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PushFetchWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
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

        /** Stops the check (used when mailbox push is disabled everywhere on the device). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

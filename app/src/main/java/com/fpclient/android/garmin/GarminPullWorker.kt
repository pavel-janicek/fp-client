package com.fpclient.android.garmin

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * The pull half of the Garmin link (see [GarminLink]): every 15 minutes, ask the watch to resend
 * anything it still queues, then keep the process alive briefly so the answers land while the
 * SDK is ready. Scheduled only while Garmin Connect is installed; [schedule] runs at every app
 * start, so installing or removing it later is picked up then.
 */
class GarminPullWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        GarminLink.start(applicationContext)
        // A cold process needs a moment for the SDK to bind; it pulls by itself once ready.
        delay(STARTUP_GRACE_MS)
        if (!GarminLink.isReady) return Result.success()
        GarminLink.pullAll()
        delay(ANSWER_WINDOW_MS)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK = "fitpub_garmin_pull"
        private const val STARTUP_GRACE_MS = 5_000L
        private const val ANSWER_WINDOW_MS = 20_000L

        fun schedule(context: Context) {
            if (!GarminLink.isAvailable(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<GarminPullWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

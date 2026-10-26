package com.fpclient.android.garmin

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fpclient.android.FitPubApplication
import java.util.concurrent.TimeUnit

/**
 * Moves Garmin Connect activities from the relay to FitPub ([GarminConnectSync]): every 15
 * minutes while a mailbox exists, and right away after linking or "Sync now".
 */
class GarminConnectWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = FitPubApplication.container(applicationContext)
        val outcome = container.garminConnectSync.sync { text ->
            GarminNotifications.postImported(applicationContext, text)
            container.activitiesVersion.value += 1
        }
        return if (outcome == GarminConnectSync.Outcome.RETRY) Result.retry() else Result.success()
    }

    companion object {
        private const val ONE_TIME_WORK = "fitpub_garmin_connect_now"
        private const val PERIODIC_WORK = "fitpub_garmin_connect_periodic"
        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<GarminConnectWorker>()
                .setConstraints(connected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        /** Runs every 15 minutes while this phone has a relay mailbox; cancelled otherwise. */
        fun schedulePeriodic(context: Context) {
            val manager = WorkManager.getInstance(context)
            if (FitPubApplication.container(context).garminConnectStore.mailbox.value == null) {
                manager.cancelUniqueWork(PERIODIC_WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<GarminConnectWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build()
            manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

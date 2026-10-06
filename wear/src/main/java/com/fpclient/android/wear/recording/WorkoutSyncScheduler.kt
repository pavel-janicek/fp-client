package com.fpclient.android.wear.recording

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkoutSyncScheduler {
    private const val ONE_TIME_WORK = "fitpub_watch_workout_sync_now"
    private const val PERIODIC_WORK = "fitpub_watch_workout_sync_retry"

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WatchWorkoutSyncWorker>().build(),
        )
    }

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchWorkoutSyncWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
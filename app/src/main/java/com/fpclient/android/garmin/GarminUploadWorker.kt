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
import com.fpclient.android.data.body.CalorieFitAmender
import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.isPermanentUploadRejection
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Uploads complete Garmin workouts to FitPub (Iteration 10d): builds the FIT from the samples
 * ([GarminFitWriter]), adds calories when the user opted in (the same [CalorieFitAmender] as Wear
 * OS workouts) and uploads it like a Wear OS workout — public by default, the activity type fixed
 * up when the server guessed another one, transient failures retried, server rejections parked
 * with their message.
 */
class GarminUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = FitPubApplication.container(applicationContext)
        val inbox = container.garminInboxStore
        inbox.retryBlocked()
        val session = container.sessionStore.currentSession()
        if (!session.isLoggedIn) {
            for (entry in inbox.all().filter { it.isPendingUpload }) {
                inbox.update(entry.id) { it.copy(lastError = REASON_SIGNED_OUT) }
            }
            return Result.success()
        }

        for (entry in inbox.all().filter { it.isPendingUpload && !it.blocked }.sortedBy { it.receivedAtEpochMs }) {
            val title = entry.label.ifBlank { "Workout" }
            val fit = GarminFitWriter.write(inbox.fitFile(entry), entry, inbox.samples(entry), title)
            val bodyProfile = container.bodyProfileStore.current()
            val uploadFile = if (bodyProfile.optIn && bodyProfile.isComplete) {
                val amended = File(fit.parentFile, "amended-${fit.name}")
                if (CalorieFitAmender.amendFitFileWithCalories(fit, amended, bodyProfile)) amended else fit
            } else {
                fit
            }

            when (val result = container.activityRepository.uploadFile(
                file = uploadFile,
                title = title,
                description = "",
                visibility = ActivityVisibilities.PUBLIC,
            )) {
                is ApiResult.Success -> {
                    val activity = result.data
                    val sport = entry.sport
                    if (sport != null && !activity.activityType.equals(sport, ignoreCase = true)) {
                        container.activityRepository.update(
                            activity.id,
                            ActivityUpdateRequest(
                                title = activity.title ?: title,
                                description = activity.description ?: "",
                                visibility = activity.visibility ?: ActivityVisibilities.PUBLIC,
                                activityType = sport,
                            ),
                        )
                    }
                    inbox.markUploaded(entry.id, activity.id)
                    container.activitiesVersion.value += 1
                    GarminNotifications.postUploaded(applicationContext, entry)
                }
                is ApiResult.Error -> {
                    val statusCode = result.statusCode
                    if (statusCode == 401) {
                        inbox.update(entry.id) { it.copy(attempts = it.attempts + 1, lastError = REASON_SESSION_EXPIRED) }
                        return Result.success()
                    }
                    val retryable = statusCode == null || statusCode == 408 || statusCode == 429 || statusCode >= 500
                    inbox.update(entry.id) {
                        it.copy(
                            attempts = it.attempts + 1,
                            lastError = result.message ?: "Workout upload failed",
                            blocked = !retryable,
                            rejectedByServer = isPermanentUploadRejection(statusCode),
                        )
                    }
                    if (retryable) return Result.retry()
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val ONE_TIME_WORK = "fitpub_garmin_upload_now"
        private const val PERIODIC_WORK = "fitpub_garmin_upload_retry"
        private const val REASON_SIGNED_OUT = "Sign in on the phone to upload Garmin workouts"
        private const val REASON_SESSION_EXPIRED = "Session expired — sign in again on the phone"
        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Uploads what is complete as soon as there is a network. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<GarminUploadWorker>()
                .setConstraints(connected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** Retries parked uploads (offline, signed out, server errors) every 15 minutes. */
        fun schedulePeriodic(context: Context) {
            if (!GarminLink.isAvailable(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<GarminUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

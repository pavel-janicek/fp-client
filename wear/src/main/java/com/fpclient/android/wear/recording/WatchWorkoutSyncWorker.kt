package com.fpclient.android.wear.recording

import com.fpclient.android.wear.WearLog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.fpclient.android.wear.auth.WearAuthStore
import com.fpclient.android.wear.body.BodyProfileSync
import com.fpclient.android.wear.body.CalorieFitAmender
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File

class WatchWorkoutSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val store = WatchWorkoutSyncStore(applicationContext)
        val pending = store.all()
        if (pending.isEmpty()) return Result.success()
        val auth = WearAuthStore(applicationContext).state.first()
        val uploader = WatchActivityUploader()

        for (original in pending) {
            var workout = original
            if (workout.ownerServerUrl.isBlank() || workout.ownerUsername.isBlank()) {
                if (auth.isSignedIn) {
                    workout = workout.copy(ownerServerUrl = auth.serverUrl, ownerUsername = auth.username)
                    store.upsert(workout)
                }
            }

            val matchesCurrentAccount = auth.isSignedIn &&
                auth.serverUrl.equals(workout.ownerServerUrl, ignoreCase = true) &&
                auth.username.equals(workout.ownerUsername, ignoreCase = true)

            if (!workout.directUploadAttempted && matchesCurrentAccount && hasValidatedInternet()) {
                store.update(workout.sessionId) { it.copy(directUploadAttempted = true) }
                workout = workout.copy(directUploadAttempted = true)
                val gpx = File(applicationContext.filesDir, "workouts/${workout.gpxFileName}")
                val fit = workout.fitFileName?.let { File(applicationContext.filesDir, "workouts/$it") }
                // A direct upload never passes through the phone, so the watch has to stamp the
                // calorie estimate into the FIT itself (from the mirrored body profile).
                val amended = amendFitForCalories(fit)
                val uploaded = uploader.upload(workout, gpx, auth, amended ?: fit)
                amended?.let { stale -> runCatching { stale.delete() } }
                if (uploaded) {
                    store.remove(workout.sessionId)
                    continue
                }
                // Leave a visible reason in the queue instead of failing silently — the watch
                // Home/Workout screens show the pending count and this explains the stuck state.
                val reason = "Direct upload failed; waiting for phone to upload"
                store.update(workout.sessionId) { it.copy(lastError = reason) }
                workout = workout.copy(lastError = reason)
            }

            if (!workout.relayRequested) {
                if (!relayToPhone(workout)) return Result.retry()
                store.update(workout.sessionId) { it.copy(relayRequested = true) }
            }
        }
        return Result.success()
    }

    /**
     * Best-effort calorie amendment before a direct upload: the opt-in body profile is mirrored
     * from the phone (see `WearBodyProfileSync`), so the watch can stamp the estimated
     * `totalCalories` into the FIT the same way the phone's relay path does. Returns the amended
     * file to send, or null to upload the original untouched — the estimate must never block an
     * upload.
     */
    private suspend fun amendFitForCalories(fit: File?): File? {
        if (fit == null || !fit.isFile) return null
        val profile = BodyProfileSync.read(applicationContext)
        if (profile == null || !profile.optIn || !profile.isComplete) {
            WearLog.i(TAG, "No calorie estimate: profile=${profile?.let { "optIn=${it.optIn} complete=${it.isComplete}" } ?: "never received from phone"}")
            return null
        }
        val amended = File(fit.parentFile, "amended-${fit.name}")
        val success = runCatching {
            CalorieFitAmender.amendFitFileWithCalories(fit, amended, profile)
        }.getOrDefault(false)
        WearLog.i(TAG, "Calorie amendment of ${fit.name} success=$success")
        if (!success) runCatching { amended.delete() }
        return amended.takeIf { success }
    }

    private suspend fun relayToPhone(workout: PendingWatchWorkout): Boolean {
        val gpx = File(applicationContext.filesDir, "workouts/${workout.gpxFileName}")
        val sidecar = File(applicationContext.filesDir, "workouts/${workout.sidecarFileName}")
        if (!gpx.isFile || !sidecar.isFile) return false
        return runCatching {
            val request = PutDataMapRequest.create("${WorkoutSyncProtocol.DATA_PATH_PREFIX}${workout.sessionId}").apply {
                dataMap.putLong(WorkoutSyncProtocol.KEY_ID, workout.sessionId)
                dataMap.putString(WorkoutSyncProtocol.KEY_ACTIVITY_TYPE, workout.activityType)
                dataMap.putString(WorkoutSyncProtocol.KEY_TITLE, workout.title)
                dataMap.putString(WorkoutSyncProtocol.KEY_DESCRIPTION, workout.description.orEmpty())
                dataMap.putString(WorkoutSyncProtocol.KEY_VISIBILITY, workout.visibility)
                dataMap.putString(WorkoutSyncProtocol.KEY_OWNER_SERVER, workout.ownerServerUrl)
                dataMap.putString(WorkoutSyncProtocol.KEY_OWNER_USERNAME, workout.ownerUsername)
                // Freshness stamp: identical DataItem puts fire no TYPE_CHANGED event on the
                // phone, so a re-relay ("Sync pending now") must change the payload to re-wake
                // the phone-side inbox processor.
                dataMap.putLong(WorkoutSyncProtocol.KEY_SYNC_ATTEMPT, System.currentTimeMillis())
                dataMap.putAsset(WorkoutSyncProtocol.ASSET_GPX, Asset.createFromBytes(gpx.readBytes()))
                dataMap.putAsset(WorkoutSyncProtocol.ASSET_SIDECAR, Asset.createFromBytes(sidecar.readBytes()))
                workout.fitFileName?.let { name ->
                    val fit = File(applicationContext.filesDir, "workouts/$name")
                    if (fit.isFile) {
                        dataMap.putAsset(WorkoutSyncProtocol.ASSET_FIT, Asset.createFromBytes(fit.readBytes()))
                        dataMap.putString(WorkoutSyncProtocol.KEY_FIT_FILENAME, name)
                    }
                }
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(applicationContext).putDataItem(request).await()
            true
        }.getOrDefault(false)
    }

    private fun hasValidatedInternet(): Boolean {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

private const val TAG = "WatchWorkoutSync"

package com.fpclient.android.wear

import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fpclient.android.FitPubApplication
import com.fpclient.android.data.dto.ActivityUpdateRequest
import com.fpclient.android.data.dto.ActivityVisibilities
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.session.SessionStore
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

class PhoneWorkoutSyncWorker(
    appContext: android.content.Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val container = FitPubApplication.container(applicationContext)
        val inbox = container.wearWorkoutInboxStore
        inbox.retryBlocked()
        val session = container.sessionStore.currentSession()
        if (!session.isLoggedIn) return Result.success()

        for (entry in inbox.all().sortedBy { it.receivedAtEpochMs }) {
            if (entry.blocked) continue
            val matchesOwner = entry.ownerServerUrl.isBlank() ||
                (SessionStore.normalizeServerUrl(entry.ownerServerUrl)
                    .equals(SessionStore.normalizeServerUrl(session.serverUrl), ignoreCase = true) &&
                (entry.ownerUsername.isBlank() || entry.ownerUsername.equals(session.username, ignoreCase = true)))

            if (!matchesOwner) continue

            if (entry.uploadedActivityId != null) {
                if (acknowledge(entry)) inbox.remove(entry.sessionId) else return Result.retry()
                continue
            }

            val gpx = inbox.gpxFile(entry)
            if (!gpx.isFile) {
                inbox.update(entry.sessionId) { it.copy(lastError = "Workout GPX is missing", blocked = true) }
                continue
            }

            when (val result = container.activityRepository.uploadFile(
                file = gpx,
                title = entry.title,
                description = entry.description,
                visibility = entry.visibility,
            )) {
                is ApiResult.Success -> {
                    val activity = result.data
                    if (!activity.activityType.equals(entry.activityType, ignoreCase = true)) {
                        container.activityRepository.update(
                            activity.id,
                            ActivityUpdateRequest(
                                title = activity.title ?: entry.title,
                                description = activity.description ?: entry.description,
                                visibility = activity.visibility ?: entry.visibility.ifBlank { ActivityVisibilities.PRIVATE },
                                activityType = entry.activityType,
                            ),
                        )
                    }
                    inbox.archiveSidecar(entry)
                    inbox.update(entry.sessionId) { it.copy(uploadedActivityId = activity.id, lastError = null) }
                    if (acknowledge(entry.copy(uploadedActivityId = activity.id))) {
                        inbox.remove(entry.sessionId)
                        container.activitiesVersion.value += 1
                    } else {
                        return Result.retry()
                    }
                }
                is ApiResult.Error -> {
                    val statusCode = result.statusCode
                    if (statusCode == 401) return Result.success()
                    val retryable = statusCode == null || statusCode == 408 || statusCode == 429 || statusCode >= 500
                    inbox.update(entry.sessionId) {
                        it.copy(
                            attempts = it.attempts + 1,
                            lastError = result.message ?: "Workout upload failed",
                            blocked = !retryable,
                        )
                    }
                    if (retryable) return Result.retry()
                }
            }
        }
        return Result.success()
    }

    private suspend fun acknowledge(entry: WearWorkoutInboxEntry): Boolean {
        val messageSent = runCatching {
            Wearable.getMessageClient(applicationContext)
                .sendMessage(
                    entry.sourceNodeId,
                    PhoneWorkoutSyncProtocol.ACK_PATH,
                    entry.sessionId.toString().toByteArray(Charsets.UTF_8),
                )
                .await()
        }.isSuccess
        val dataItemDeleted = runCatching {
            Wearable.getDataClient(applicationContext).deleteDataItems(Uri.parse(entry.dataItemUri)).await()
        }.isSuccess
        return messageSent || dataItemDeleted
    }
}

package com.fpclient.android.wear.recording

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class PendingWatchWorkout(
    val sessionId: Long,
    val gpxFileName: String,
    val sidecarFileName: String,
    val activityType: String,
    val title: String,
    val description: String? = null,
    /**
     * "PUBLIC" literal: matches the phone app's upload default rather than Private — kept as a
     * literal (not WatchWorkoutSyncStore.DEFAULT_VISIBILITY) because constructor defaults
     * cannot reference companion members — see the 3.0.0-beta plan entry.
     */
    val visibility: String = "PUBLIC",
    val ownerServerUrl: String = "",
    val ownerUsername: String = "",
    val createdAtEpochMs: Long,
    val directUploadAttempted: Boolean = false,
    val relayRequested: Boolean = false,
    val lastError: String? = null,
)

class WatchWorkoutSyncStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun all(): List<PendingWatchWorkout> = synchronized(lock) { read() }

    fun get(sessionId: Long): PendingWatchWorkout? = all().firstOrNull { it.sessionId == sessionId }

    fun upsert(workout: PendingWatchWorkout) = synchronized(lock) {
        write(read().filterNot { it.sessionId == workout.sessionId } + workout)
    }

    fun update(sessionId: Long, transform: (PendingWatchWorkout) -> PendingWatchWorkout) = synchronized(lock) {
        write(read().map { if (it.sessionId == sessionId) transform(it) else it })
    }

    fun remove(sessionId: Long) = synchronized(lock) {
        val existing = read().firstOrNull { it.sessionId == sessionId }
        if (existing != null) {
            val gpx = File(appContext.filesDir, "workouts/${existing.gpxFileName}")
            val sidecar = File(appContext.filesDir, "workouts/${existing.sidecarFileName}")
            val archive = File(appContext.filesDir, "workout-sync-archive").apply { mkdirs() }
            if (sidecar.isFile) runCatching { sidecar.copyTo(File(archive, sidecar.name), overwrite = true) }
            gpx.delete()
            sidecar.delete()
        }
        write(read().filterNot { it.sessionId == sessionId })
    }

    private fun read(): List<PendingWatchWorkout> = decode(preferences.getString(KEY_QUEUE, null))

    private fun write(workouts: List<PendingWatchWorkout>) {
        preferences.edit().putString(KEY_QUEUE, encode(workouts)).commit()
        WorkoutRecordingBus.publishPendingCount(workouts.size)
    }

    companion object {
        private const val PREFERENCES_NAME = "fitpub_watch_workout_queue"
        private const val KEY_QUEUE = "pending"
        /** Watch auto-share default visibility (matches the phone app's upload default). */
        const val DEFAULT_VISIBILITY = "PUBLIC"
        private val serializer = ListSerializer(PendingWatchWorkout.serializer())
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(workouts: List<PendingWatchWorkout>): String = json.encodeToString(serializer, workouts)

        fun decode(value: String?): List<PendingWatchWorkout> = value?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrNull()
        }.orEmpty()
    }
}
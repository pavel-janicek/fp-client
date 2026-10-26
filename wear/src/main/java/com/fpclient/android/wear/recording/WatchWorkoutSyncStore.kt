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
    /** Blank = let the server name it from the start time ("Morning Walk"), as the phone app does. */
    val title: String = "",
    val description: String? = null,
    /**
     * Stamped at the end of recording from the phone's default visibility ([UploadDefaultsSync]).
     * The "PUBLIC" literal only decodes entries queued by older watch versions, which always
     * meant Public (a constructor default cannot reference the companion's DEFAULT_VISIBILITY).
     */
    val visibility: String = "PUBLIC",
    val ownerServerUrl: String = "",
    val ownerUsername: String = "",
    val createdAtEpochMs: Long,
    val fitFileName: String? = null,
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

    fun resetRelayFlags() = synchronized(lock) {
        write(read().map { it.copy(relayRequested = false, directUploadAttempted = false) })
    }

    fun remove(sessionId: Long) = synchronized(lock) {
        val existing = read().firstOrNull { it.sessionId == sessionId }
        if (existing != null) {
            val gpx = File(appContext.filesDir, "workouts/${existing.gpxFileName}")
            val sidecar = File(appContext.filesDir, "workouts/${existing.sidecarFileName}")
            val fit = existing.fitFileName?.let { File(appContext.filesDir, "workouts/$it") }
            val archive = File(appContext.filesDir, "workout-sync-archive").apply { mkdirs() }
            if (sidecar.isFile) runCatching { sidecar.copyTo(File(archive, sidecar.name), overwrite = true) }
            gpx.delete()
            sidecar.delete()
            fit?.delete()
        }
        write(read().filterNot { it.sessionId == sessionId })
    }

    fun removeAll() = synchronized(lock) {
        val existingList = read()
        for (existing in existingList) {
            val gpx = File(appContext.filesDir, "workouts/${existing.gpxFileName}")
            val sidecar = File(appContext.filesDir, "workouts/${existing.sidecarFileName}")
            val fit = existing.fitFileName?.let { File(appContext.filesDir, "workouts/$it") }
            gpx.delete()
            sidecar.delete()
            fit?.delete()
        }
        write(emptyList())
    }

    private fun read(): List<PendingWatchWorkout> = decode(preferences.getString(KEY_QUEUE, null))

    private fun write(workouts: List<PendingWatchWorkout>) {
        preferences.edit().putString(KEY_QUEUE, encode(workouts)).commit()
        WorkoutRecordingBus.publishPendingCount(workouts.size)
    }

    companion object {
        private const val PREFERENCES_NAME = "fitpub_watch_workout_queue"
        private const val KEY_QUEUE = "pending"
        /**
         * Visibility while the phone has not sent its default (Settings → Privacy, see
         * [UploadDefaultsSync]): "Only me", like the phone's own default.
         */
        const val DEFAULT_VISIBILITY = "PRIVATE"
        private val serializer = ListSerializer(PendingWatchWorkout.serializer())
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(workouts: List<PendingWatchWorkout>): String = json.encodeToString(serializer, workouts)

        fun decode(value: String?): List<PendingWatchWorkout> = value?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrNull()
        }.orEmpty()
    }
}
package com.fpclient.android.garmin

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class GarminInboxEntry(
    val id: String,
    val label: String,
    val createdEpochSeconds: Long,
    val deviceName: String,
    val receivedAtEpochMs: Long,
    val sport: String? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Long? = null,
    /** The watch's Connect IQ device id, so missing chunks can be requested from the right watch. */
    val deviceId: Long? = null,
    val expectedChunks: Int = 0,
    val receivedChunks: List<Int> = emptyList(),
    val uploadedActivityId: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
    /** Not retried by the periodic upload until a user-initiated retry (like the Wear inbox). */
    val blocked: Boolean = false,
    /** The server refused the file itself (permanent 4xx). */
    val rejectedByServer: Boolean = false,
) {
    /** Every sample chunk is on the phone — the watch may be acked, the FIT can be built. */
    val isComplete: Boolean get() = (0 until expectedChunks).all { it in receivedChunks }

    val missingChunks: List<Int> get() = (0 until expectedChunks).filterNot { it in receivedChunks }

    val isPendingUpload: Boolean get() = isComplete && uploadedActivityId == null
}

/**
 * Workouts received from the Garmin watch app (Iteration 10): the summary as an entry, each
 * sample chunk as a file under `garmin-inbox/<id>/`. The upload queue for [GarminUploadWorker],
 * like `WearWorkoutInboxStore` is for Wear OS.
 *
 * The watch resends until acked, so the same summary and chunks can arrive repeatedly: every
 * write is idempotent. An uploaded entry stays (files deleted) as a tombstone, so a summary the
 * watch resends after a lost ack is recognised instead of uploaded twice.
 */
class GarminInboxStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    private val mutablePendingCount = MutableStateFlow(readEntries().count { it.uploadedActivityId == null })
    /** Workouts not yet on FitPub (still arriving or waiting to upload). */
    val pendingCount: StateFlow<Int> = mutablePendingCount

    private val mutableLatestError = MutableStateFlow(readEntries().latestError())
    /** Why the newest unfinished workout is stuck, for the banner and Settings. */
    val latestError: StateFlow<String?> = mutableLatestError

    fun all(): List<GarminInboxEntry> = synchronized(lock) { readEntries() }

    fun get(id: String): GarminInboxEntry? = all().firstOrNull { it.id == id }

    /** Stores a summary unless its id is already known; true when it was new. */
    fun recordSummary(entry: GarminInboxEntry): Boolean = synchronized(lock) {
        val current = readEntries()
        if (current.any { it.id == entry.id }) return false
        writeEntries(current + entry)
        true
    }

    /**
     * Stores one chunk of a known workout; returns the updated entry, or null when the workout
     * is unknown (its summary has not arrived — the watch resends both) or [index] is out of range.
     */
    fun storeChunk(id: String, index: Int, values: List<Long?>): GarminInboxEntry? = synchronized(lock) {
        val entry = readEntries().firstOrNull { it.id == id } ?: return null
        if (index !in 0 until entry.expectedChunks || entry.uploadedActivityId != null) return entry
        if (index !in entry.receivedChunks) {
            writeAtomically(chunkFile(id, index), json.encodeToString(valuesSerializer, values))
            val updated = entry.copy(receivedChunks = (entry.receivedChunks + index).sorted())
            writeEntries(readEntries().map { if (it.id == id) updated else it })
            return updated
        }
        entry
    }

    /** All samples of a complete workout, in time order. */
    internal fun samples(entry: GarminInboxEntry): List<GarminProtocol.Sample> =
        (0 until entry.expectedChunks).flatMap { index ->
            val file = chunkFile(entry.id, index)
            if (!file.isFile) return@flatMap emptyList()
            GarminProtocol.samples(json.decodeFromString(valuesSerializer, file.readText()))
        }.sortedBy { it.secondsSinceStart }

    fun update(id: String, transform: (GarminInboxEntry) -> GarminInboxEntry) = synchronized(lock) {
        writeEntries(readEntries().map { if (it.id == id) transform(it) else it })
    }

    /** Uploaded: keep the entry as a tombstone, drop its sample files. */
    fun markUploaded(id: String, activityId: String) = synchronized(lock) {
        writeEntries(readEntries().map { if (it.id == id) it.copy(uploadedActivityId = activityId, lastError = null) else it })
        entryDirectory(id).deleteRecursively()
    }

    /** User-initiated retry: every unfinished entry gets another try, server rejections included. */
    fun retryManually() = synchronized(lock) {
        writeEntries(readEntries().map { if (it.uploadedActivityId == null) it.copy(blocked = false, rejectedByServer = false, lastError = null) else it })
    }

    /** Automatic pass: unblocks transient failures; server-rejected entries stay blocked. */
    fun retryBlocked() = synchronized(lock) {
        writeEntries(readEntries().map { if (it.rejectedByServer) it else it.copy(blocked = false, lastError = null) })
    }

    fun fitFile(entry: GarminInboxEntry): File = File(entryDirectory(entry.id), "workout.fit")

    private fun chunkFile(id: String, index: Int) = File(entryDirectory(id), "chunk-$index.json")

    // Ids are "<epoch>-<random>" from the watch; anything else is neutralised for the file system.
    private fun entryDirectory(id: String) = File(File(appContext.filesDir, INBOX_DIRECTORY), id.replace(Regex("[^A-Za-z0-9-]"), "_"))

    private fun readEntries(): List<GarminInboxEntry> = runCatching {
        preferences.getString(KEY_ENTRIES, null)?.let { json.decodeFromString(serializer, it) }.orEmpty()
    }.getOrDefault(emptyList())

    private fun writeEntries(entries: List<GarminInboxEntry>) {
        // Prune the oldest uploaded tombstones first; unfinished workouts are never dropped.
        val excess = entries.size - MAX_ENTRIES
        val kept = if (excess > 0) {
            val drop = entries.filter { it.uploadedActivityId != null }.sortedBy { it.receivedAtEpochMs }.take(excess).map { it.id }.toSet()
            entries.filterNot { it.id in drop }
        } else {
            entries
        }
        val saved = preferences.edit().putString(KEY_ENTRIES, json.encodeToString(serializer, kept)).commit()
        check(saved) { "Could not persist Garmin workout inbox" }
        mutablePendingCount.value = kept.count { it.uploadedActivityId == null }
        mutableLatestError.value = kept.latestError()
    }

    private fun List<GarminInboxEntry>.latestError(): String? =
        filter { it.uploadedActivityId == null }.sortedByDescending { it.receivedAtEpochMs }.firstNotNullOfOrNull { it.lastError }

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text)
        if (file.exists() && !file.delete()) error("Could not replace ${file.name}")
        if (!temporary.renameTo(file)) error("Could not finalize ${file.name}")
    }

    companion object {
        private const val PREFERENCES_NAME = "fitpub_garmin_inbox"
        private const val KEY_ENTRIES = "entries"
        private const val INBOX_DIRECTORY = "garmin-inbox"

        /** Also the dedupe window for resent summaries. */
        private const val MAX_ENTRIES = 100
        private val serializer = ListSerializer(GarminInboxEntry.serializer())
        private val valuesSerializer = ListSerializer(Long.serializer().nullable)
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

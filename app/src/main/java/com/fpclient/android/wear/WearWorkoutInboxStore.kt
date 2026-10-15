package com.fpclient.android.wear

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class WearWorkoutInboxEntry(
    val sessionId: Long,
    val gpxFileName: String,
    val sidecarFileName: String,
    val activityType: String,
    val title: String,
    val description: String = "",
    val visibility: String = "PRIVATE",
    val ownerServerUrl: String,
    val ownerUsername: String,
    val sourceNodeId: String,
    val dataItemUri: String,
    val receivedAtEpochMs: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val blocked: Boolean = false,
    val uploadedActivityId: String? = null,
    val fitFileName: String? = null,
    /**
     * The server refused this file (permanent 4xx, see `isPermanentUploadRejection`). It stays
     * blocked with the server's message: the periodic worker no longer resends it every 15
     * minutes; only a user-initiated sync ([WearWorkoutInboxStore.retryManually]) tries again.
     */
    val rejectedByServer: Boolean = false,
)

class WearWorkoutInboxStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutableEntries = MutableStateFlow(readEntries())
    val entries: StateFlow<List<WearWorkoutInboxEntry>> = mutableEntries

    private val mutablePendingCount = MutableStateFlow(readEntries().size)
    val pendingCount: StateFlow<Int> = mutablePendingCount

    /** Newest stored failure reason, so the UI can explain *why* workouts are not resolving. */
    private val mutableLatestError = MutableStateFlow(readEntries().firstNotNullOfOrNull { it.lastError })
    val latestError: StateFlow<String?> = mutableLatestError

    fun all(): List<WearWorkoutInboxEntry> = synchronized(lock) { readEntries() }

    fun get(sessionId: Long): WearWorkoutInboxEntry? = all().firstOrNull { it.sessionId == sessionId }

    fun gpxFile(entry: WearWorkoutInboxEntry) = File(inboxDirectory(), entry.gpxFileName)

    fun sidecarFile(entry: WearWorkoutInboxEntry) = File(inboxDirectory(), entry.sidecarFileName)

    fun fitFile(entry: WearWorkoutInboxEntry) = entry.fitFileName?.let { File(inboxDirectory(), it) }

    fun stage(
        entry: WearWorkoutInboxEntry,
        gpxBytes: ByteArray,
        sidecarBytes: ByteArray,
        fitBytes: ByteArray? = null,
    ) = synchronized(lock) {
        val directory = inboxDirectory().apply { mkdirs() }
        writeAtomically(File(directory, entry.gpxFileName), gpxBytes)
        writeAtomically(File(directory, entry.sidecarFileName), sidecarBytes)
        if (fitBytes != null && entry.fitFileName != null) {
            writeAtomically(File(directory, entry.fitFileName), fitBytes)
        }
        writeEntries(readEntries().filterNot { it.sessionId == entry.sessionId } + entry)
    }

    fun update(sessionId: Long, transform: (WearWorkoutInboxEntry) -> WearWorkoutInboxEntry) = synchronized(lock) {
        writeEntries(readEntries().map { if (it.sessionId == sessionId) transform(it) else it })
    }

    /**
     * Automatic pass (start of every sync run): unblocks transient failures. Entries the server
     * rejected stay blocked, keeping their message visible — resending them unchanged would only
     * be rejected again.
     */
    fun retryBlocked() = synchronized(lock) {
        writeEntries(readEntries().afterAutomaticRetry())
    }

    /**
     * User-initiated sync: gives the entry with [sessionId] (or, when null, every entry) another
     * try — server-rejected ones included.
     */
    fun retryManually(sessionId: Long? = null) = synchronized(lock) {
        writeEntries(readEntries().afterManualRetry(sessionId))
    }

    fun archiveSidecar(entry: WearWorkoutInboxEntry) {
        val source = sidecarFile(entry)
        if (!source.isFile) return
        val targetDirectory = File(appContext.filesDir, ARCHIVE_DIRECTORY).apply { mkdirs() }
        runCatching { source.copyTo(File(targetDirectory, source.name), overwrite = true) }
    }

    /**
     * Tombstone: [remove] archives the sidecar as `workout-<sessionId>.json`, so its presence
     * means this session was already uploaded by an earlier ACK cycle. Used to re-acknowledge a
     * re-relayed workout instead of uploading a duplicate activity.
     */
    fun wasUploadedBefore(sessionId: Long): Boolean =
        File(File(appContext.filesDir, ARCHIVE_DIRECTORY), "workout-$sessionId.json").isFile

    fun remove(sessionId: Long) = synchronized(lock) {
        val entry = readEntries().firstOrNull { it.sessionId == sessionId }
        if (entry != null) {
            archiveSidecar(entry)
            gpxFile(entry).delete()
            sidecarFile(entry).delete()
            fitFile(entry)?.delete()
        }
        writeEntries(readEntries().filterNot { it.sessionId == sessionId })
    }

    fun clearAll() = synchronized(lock) {
        val current = readEntries()
        for (entry in current) {
            archiveSidecar(entry)
            gpxFile(entry).delete()
            sidecarFile(entry).delete()
            fitFile(entry)?.delete()
        }
        writeEntries(emptyList())
    }

    private fun inboxDirectory() = File(appContext.filesDir, INBOX_DIRECTORY)

    private fun readEntries(): List<WearWorkoutInboxEntry> = runCatching {
        preferences.getString(KEY_ENTRIES, null)?.let { json.decodeFromString(serializer, it) }.orEmpty()
    }.getOrDefault(emptyList())

    private fun writeEntries(entries: List<WearWorkoutInboxEntry>) {
        val saved = preferences.edit().putString(KEY_ENTRIES, json.encodeToString(serializer, entries)).commit()
        check(saved) { "Could not persist watch workout inbox" }
        mutableEntries.value = entries
        mutablePendingCount.value = entries.count { it.uploadedActivityId == null }
        mutableLatestError.value = entries.firstNotNullOfOrNull { it.lastError }
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(bytes)
        if (file.exists() && !file.delete()) error("Could not replace ${file.name}")
        if (!temporary.renameTo(file)) error("Could not finalize ${file.name}")
    }

    companion object {
        private const val PREFERENCES_NAME = "fitpub_wear_workout_inbox"
        private const val KEY_ENTRIES = "entries"
        private const val INBOX_DIRECTORY = "wear-workout-inbox"
        private const val ARCHIVE_DIRECTORY = "wear-workout-archive"
        private val serializer = ListSerializer(WearWorkoutInboxEntry.serializer())
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    }
}

/** [WearWorkoutInboxStore.retryBlocked] as a pure transform: server-rejected entries stay blocked. */
internal fun List<WearWorkoutInboxEntry>.afterAutomaticRetry(): List<WearWorkoutInboxEntry> =
    map { if (it.rejectedByServer) it else it.copy(blocked = false, lastError = null) }

/** [WearWorkoutInboxStore.retryManually] as a pure transform; [sessionId] null means every entry. */
internal fun List<WearWorkoutInboxEntry>.afterManualRetry(sessionId: Long?): List<WearWorkoutInboxEntry> =
    map {
        if (sessionId == null || it.sessionId == sessionId) {
            it.copy(blocked = false, lastError = null, rejectedByServer = false)
        } else {
            it
        }
    }

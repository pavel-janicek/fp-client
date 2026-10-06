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
)

class WearWorkoutInboxStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutablePendingCount = MutableStateFlow(readEntries().size)
    val pendingCount: StateFlow<Int> = mutablePendingCount

    fun all(): List<WearWorkoutInboxEntry> = synchronized(lock) { readEntries() }

    fun get(sessionId: Long): WearWorkoutInboxEntry? = all().firstOrNull { it.sessionId == sessionId }

    fun gpxFile(entry: WearWorkoutInboxEntry) = File(inboxDirectory(), entry.gpxFileName)

    fun sidecarFile(entry: WearWorkoutInboxEntry) = File(inboxDirectory(), entry.sidecarFileName)

    fun stage(entry: WearWorkoutInboxEntry, gpxBytes: ByteArray, sidecarBytes: ByteArray) = synchronized(lock) {
        val directory = inboxDirectory().apply { mkdirs() }
        writeAtomically(File(directory, entry.gpxFileName), gpxBytes)
        writeAtomically(File(directory, entry.sidecarFileName), sidecarBytes)
        writeEntries(readEntries().filterNot { it.sessionId == entry.sessionId } + entry)
    }

    fun update(sessionId: Long, transform: (WearWorkoutInboxEntry) -> WearWorkoutInboxEntry) = synchronized(lock) {
        writeEntries(readEntries().map { if (it.sessionId == sessionId) transform(it) else it })
    }

    fun retryBlocked() = synchronized(lock) {
        writeEntries(readEntries().map { it.copy(blocked = false, lastError = null) })
    }

    fun archiveSidecar(entry: WearWorkoutInboxEntry) {
        val source = sidecarFile(entry)
        if (!source.isFile) return
        val targetDirectory = File(appContext.filesDir, ARCHIVE_DIRECTORY).apply { mkdirs() }
        runCatching { source.copyTo(File(targetDirectory, source.name), overwrite = true) }
    }

    fun remove(sessionId: Long) = synchronized(lock) {
        val entry = readEntries().firstOrNull { it.sessionId == sessionId }
        if (entry != null) {
            archiveSidecar(entry)
            gpxFile(entry).delete()
            sidecarFile(entry).delete()
        }
        writeEntries(readEntries().filterNot { it.sessionId == sessionId })
    }

    private fun inboxDirectory() = File(appContext.filesDir, INBOX_DIRECTORY)

    private fun readEntries(): List<WearWorkoutInboxEntry> = runCatching {
        preferences.getString(KEY_ENTRIES, null)?.let { json.decodeFromString(serializer, it) }.orEmpty()
    }.getOrDefault(emptyList())

    private fun writeEntries(entries: List<WearWorkoutInboxEntry>) {
        val saved = preferences.edit().putString(KEY_ENTRIES, json.encodeToString(serializer, entries)).commit()
        check(saved) { "Could not persist watch workout inbox" }
        mutablePendingCount.value = entries.count { it.uploadedActivityId == null }
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

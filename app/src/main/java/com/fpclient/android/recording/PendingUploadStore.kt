package com.fpclient.android.recording

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A recorded workout waiting to be shared to FitPub (Iteration 8d). Created when the
 * recording stops (with its GPX 1.1 export already written to app-private storage) and
 * removed once the server has imported the upload — so an entry that survives means
 * "not yet uploaded" and is retried on the next app start or from the Record screen.
 */
@Serializable
data class PendingUpload(
    /** The session's start epoch ms — the stable key used by every persisted artifact. */
    val sessionId: Long,
    /** Epoch ms the session started (for display + ordering). */
    val startedAtEpochMs: Long,
    /** Activity type chosen on the Record pre-start screen (default fallback kept too). */
    val activityType: String,
    val createdAtEpochMs: Long,
    /**
     * Whether the **user** asked for this workout to be shared.
     *
     * This is false for a recording that was merely stopped and never submitted: it sits in
     * the pending list so the user can share it later, but it must not be published without
     * being asked for. It becomes true the moment an upload is actually attempted (see
     * `markFailed`), which is what makes a *failed* share still retryable automatically.
     *
     * Without this flag the app-start retry pass uploaded every recording the user had ever
     * made, so discarding one afterwards was futile — the activity (and the personal record
     * it earned) already existed on the server.
     */
    val requestedByUser: Boolean = false,
    /** How many upload attempts already failed. */
    val attempts: Int = 0,
    /** Message of the most recent failed attempt, shown in the pending list. */
    val lastError: String? = null,
    /**
     * Metadata chosen on the post-workout summary screen, persisted on a failed attempt
     * so later automatic/manual retries replay exactly what the user entered.
     */
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null,
)

/**
 * File-backed registry of [PendingUpload]s (`pending_uploads.json` in app-private
 * storage). Plain java.io + kotlinx.serialization, synchronized per instance, readable
 * from any thread and unit-testable on the JVM with a temporary directory. Corrupt or
 * torn files read back as an empty list instead of crashing the upload path.
 */
class PendingUploadStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun all(): List<PendingUpload> = synchronized(lock) { read() }

    fun get(sessionId: Long): PendingUpload? =
        all().firstOrNull { it.sessionId == sessionId }

    /** Inserts or replaces the entry with the same [PendingUpload.sessionId]. */
    fun upsert(entry: PendingUpload) = synchronized(lock) {
        val list = read().filterNot { it.sessionId == entry.sessionId } + entry
        write(list)
    }

    fun remove(sessionId: Long) = synchronized(lock) {
        write(read().filterNot { it.sessionId == sessionId })
    }

    /**
     * Marks a failed attempt, remembering the metadata the user chose for the retry.
     *
     * Reaching this method at all means an upload was genuinely attempted for this session,
     * which is exactly "the user asked for this to be shared" — so it is also where
     * [PendingUpload.requestedByUser] is set, and from then on the app-start retry pass
     * picks this entry up on its own.
     */
    fun markFailed(
        sessionId: Long,
        error: String?,
        title: String?,
        description: String?,
        visibility: String?,
        activityType: String,
    ) = synchronized(lock) {
        val updated = read().map { entry ->
            if (entry.sessionId != sessionId) {
                entry
            } else {
                entry.copy(
                    requestedByUser = true,
                    attempts = entry.attempts + 1,
                    lastError = error,
                    title = title ?: entry.title,
                    description = description ?: entry.description,
                    visibility = visibility ?: entry.visibility,
                    activityType = activityType,
                )
            }
        }
        write(updated)
    }

    private fun read(): List<PendingUpload> = runCatching {
        if (file.exists()) json.decodeFromString(listSerializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun write(list: List<PendingUpload>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(listSerializer, list))
        }
    }

    private companion object {
        /**
         * Explicit list serializer: the reified `encodeToString`/`decodeFromString` helpers
         * would need an extra kotlinx.serialization import per call site, and a single
         * shared instance keeps the codec identical on both sides of the file.
         */
        val listSerializer = ListSerializer(PendingUpload.serializer())
    }
}

package com.fpclient.android.garmin

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
)

/**
 * Workouts received from a Garmin watch. 10b only records them (the prototype's proof that
 * delivery works); 10d turns this into the upload queue, like `WearWorkoutInboxStore`.
 *
 * The watch resends a workout until it sees our ack, so the same id can arrive several times:
 * [record] is idempotent and reports whether the entry was new.
 */
class GarminInboxStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun all(): List<GarminInboxEntry> = synchronized(lock) { readEntries() }

    /** Stores [entry] unless its id is already known; true when it was new. */
    fun record(entry: GarminInboxEntry): Boolean = synchronized(lock) {
        val current = readEntries()
        if (current.any { it.id == entry.id }) return false
        val saved = preferences.edit()
            .putString(KEY_ENTRIES, json.encodeToString(serializer, (current + entry).takeLast(MAX_ENTRIES)))
            .commit()
        check(saved) { "Could not persist Garmin workout inbox" }
        true
    }

    private fun readEntries(): List<GarminInboxEntry> = runCatching {
        preferences.getString(KEY_ENTRIES, null)?.let { json.decodeFromString(serializer, it) }.orEmpty()
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFERENCES_NAME = "fitpub_garmin_inbox"
        private const val KEY_ENTRIES = "entries"

        /** Also the dedupe window: an id older than the newest [MAX_ENTRIES] would be stored again. */
        private const val MAX_ENTRIES = 100
        private val serializer = ListSerializer(GarminInboxEntry.serializer())
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

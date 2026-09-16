package com.fpclient.android.recording

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level state machine of a track recording session (Iteration 8a skeleton):
 * idle (no snapshot) → recording ⇄ paused → stopped (snapshot cleared again).
 * The GPS engine itself arrives in Iteration 8b; this only tracks the session state.
 */
enum class RecordingState { RECORDING, PAUSED }

/**
 * Immutable snapshot of the active recording session. All timing is wall-clock epoch
 * millis on purpose: a snapshot can be persisted, survive process death, and be restored
 * into exactly the same state — elapsed time keeps running across the death because it
 * is always derived from the wall clock, never from a counter that lived in the process.
 */
data class TrackSessionSnapshot(
    val state: RecordingState,
    /** Epoch ms when the session was started (first press of Record). */
    val startedAtEpochMs: Long,
    /** Moving time accumulated across all completed (paused-out) segments. */
    val accumulatedMs: Long,
    /** Epoch ms of the last resume; null while paused (no segment is running). */
    val lastResumeAtEpochMs: Long?,
) {
    /** Total elapsed time since the session started (includes paused stretches). */
    fun elapsedAt(nowMs: Long): Long = (nowMs - startedAtEpochMs).coerceAtLeast(0L)

    /** Moving time: accumulated segments plus the currently running one, if any. */
    fun movingMsAt(nowMs: Long): Long {
        val running = lastResumeAtEpochMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMs + running
    }
}

/**
 * In-process shared state between the foreground service and any Compose UI. The service
 * is the single writer; screens observe [session]. Lives outside the service instance so
 * it also survives configuration changes and is readable before the service is bound.
 */
object TrackRecordingBus {

    private val _session = MutableStateFlow<TrackSessionSnapshot?>(null)

    /** Non-null while a recording session exists (recording or paused). */
    val session: StateFlow<TrackSessionSnapshot?> = _session

    fun publish(snapshot: TrackSessionSnapshot?) {
        _session.value = snapshot
    }
}

/**
 * Persists the session snapshot so the service can restore into the right state after the
 * OS kills the process (START_STICKY restart with a null intent). Plain SharedPreferences
 * are used — the store must be readable synchronously inside onStartCommand, before any
 * async storage (DataStore) could deliver a value.
 */
class TrackRecordingStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: TrackSessionSnapshot) {
        val map = serialize(snapshot)
        prefs.edit()
            .putString(KEY_STATE, map[KEY_STATE] as String)
            .putLong(KEY_STARTED_AT, map[KEY_STARTED_AT] as Long)
            .putLong(KEY_ACCUMULATED_MS, map[KEY_ACCUMULATED_MS] as Long)
            .putLong(KEY_LAST_RESUME_AT, map[KEY_LAST_RESUME_AT] as Long)
            .apply()
    }

    fun load(): TrackSessionSnapshot? = deserialize(
        mapOf(
            KEY_STATE to prefs.getString(KEY_STATE, null),
            KEY_STARTED_AT to if (prefs.contains(KEY_STARTED_AT)) prefs.getLong(KEY_STARTED_AT, 0L) else null,
            KEY_ACCUMULATED_MS to if (prefs.contains(KEY_ACCUMULATED_MS)) prefs.getLong(KEY_ACCUMULATED_MS, 0L) else null,
            KEY_LAST_RESUME_AT to if (prefs.contains(KEY_LAST_RESUME_AT)) prefs.getLong(KEY_LAST_RESUME_AT, -1L) else null,
        ),
    )

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "track_recording"
        private const val KEY_STATE = "state"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_ACCUMULATED_MS = "accumulated_ms"
        private const val KEY_LAST_RESUME_AT = "last_resume_at"

        /**
         * Pure serialization of a snapshot into a prefs-shaped map (lastResumeAt of -1 encodes
         * "paused"). Exposed for JVM unit tests; save()/load() delegate to these.
         */
        fun serialize(snapshot: TrackSessionSnapshot): Map<String, Any?> = mapOf(
            KEY_STATE to snapshot.state.name,
            KEY_STARTED_AT to snapshot.startedAtEpochMs,
            KEY_ACCUMULATED_MS to snapshot.accumulatedMs,
            KEY_LAST_RESUME_AT to (snapshot.lastResumeAtEpochMs ?: -1L),
        )

        /** Inverse of [serialize]; returns null for unknown/corrupt data (never crashes). */
        fun deserialize(map: Map<String, Any?>): TrackSessionSnapshot? {
            val stateName = map[KEY_STATE] as? String ?: return null
            val state = runCatching { RecordingState.valueOf(stateName) }.getOrNull() ?: return null
            val startedAt = map[KEY_STARTED_AT] as? Long ?: return null
            val accumulated = map[KEY_ACCUMULATED_MS] as? Long ?: return null
            val lastResume = (map[KEY_LAST_RESUME_AT] as? Long)?.takeIf { it >= 0 }
            return TrackSessionSnapshot(state, startedAt, accumulated, lastResume)
        }
    }
}

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
    /**
     * Activity type chosen on the Record pre-start screen (a member of
     * [com.fpclient.android.data.dto.ActivityTypes.ALL], e.g. "RUN"). Persisted with the
     * session so a process-death restore keeps the label, and Iteration 8d can preselect
     * it for the uploaded activity.
     */
    val activityType: String = DEFAULT_ACTIVITY_TYPE,
) {
    /** Total elapsed time since the session started (includes paused stretches). */
    fun elapsedAt(nowMs: Long): Long = (nowMs - startedAtEpochMs).coerceAtLeast(0L)

    /** Moving time: accumulated segments plus the currently running one, if any. */
    fun movingMsAt(nowMs: Long): Long {
        val running = lastResumeAtEpochMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMs + running
    }

    companion object {
        /** Fallback when no type was chosen (or a pre-8c persisted session is restored). */
        const val DEFAULT_ACTIVITY_TYPE = "OTHER"
    }
}

/**
 * The session as it was the moment recording stopped (Iteration 8d): the last snapshot,
 * the final stats and every accepted fix. Published once by the service on stop and
 * consumed by the post-workout summary screen; cleared when the summary is dismissed.
 * The persisted JSONL/GPX files stay on disk regardless, so a dismissed summary remains
 * reachable later through the pending-upload list.
 */
data class FinishedRecording(
    val snapshot: TrackSessionSnapshot,
    val stats: TrackStats,
    val points: List<TrackPoint>,
    /** Epoch ms the stop was performed — freezes the moving time shown in the summary. */
    val endedAtEpochMs: Long,
) {
    /** Moving time of the whole session (accumulated segments + the last running one). */
    val movingMs: Long get() = snapshot.movingMsAt(endedAtEpochMs)
}

/**
 * In-process shared state between the foreground service and any Compose UI. The service
 * is the single writer; screens observe [session]. Lives outside the service instance so
 * it also survives configuration changes and is readable before the service is bound.
 */
object TrackRecordingBus {

    private val _session = MutableStateFlow<TrackSessionSnapshot?>(null)
    private val _stats = MutableStateFlow(TrackStats())
    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())
    private val _finished = MutableStateFlow<FinishedRecording?>(null)

    /** Non-null while a recording session exists (recording or paused). */
    val session: StateFlow<TrackSessionSnapshot?> = _session

    /**
     * Live track totals (distance, elevation gain, fix count) fed by the service's GPS
     * engine; reset to zero when no session is active. Elapsed/moving time are not here —
     * they derive from [session]'s wall-clock math so they keep ticking between fixes.
     */
    val stats: StateFlow<TrackStats> = _stats

    /**
     * Every accepted fix of the active session, in order — consumed by the Record
     * screen's mini-map (drawn polyline + live position dot). Reset when the session
     * ends. One point per ~2 s fix, so copy-per-append is cheap enough.
     */
    val points: StateFlow<List<TrackPoint>> = _points

    /** Non-null right after a session was stopped, until the summary is dismissed. */
    val finished: StateFlow<FinishedRecording?> = _finished

    fun publish(snapshot: TrackSessionSnapshot?) {
        _session.value = snapshot
        if (snapshot == null) {
            _stats.value = TrackStats()
            _points.value = emptyList()
        }
    }

    fun publishStats(stats: TrackStats) {
        _stats.value = stats
    }

    /** Appends one accepted fix to the live track (Iteration 8c mini-map). */
    fun publishPoint(point: TrackPoint) {
        _points.value = _points.value + point
    }

    /** Replaces the live track wholesale (process-death restore replays the file). */
    fun publishPoints(points: List<TrackPoint>) {
        _points.value = points
    }

    /** Publishes the just-stopped session for the post-workout summary (Iteration 8d). */
    fun publishFinished(finished: FinishedRecording) {
        _finished.value = finished
    }

    /** Dismisses the post-workout summary (called when the summary screen closes). */
    fun clearFinished() {
        _finished.value = null
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
            .putString(KEY_ACTIVITY_TYPE, map[KEY_ACTIVITY_TYPE] as String?)
            .apply()
    }

    fun load(): TrackSessionSnapshot? = deserialize(
        mapOf(
            KEY_STATE to prefs.getString(KEY_STATE, null),
            KEY_STARTED_AT to if (prefs.contains(KEY_STARTED_AT)) prefs.getLong(KEY_STARTED_AT, 0L) else null,
            KEY_ACCUMULATED_MS to if (prefs.contains(KEY_ACCUMULATED_MS)) prefs.getLong(KEY_ACCUMULATED_MS, 0L) else null,
            KEY_LAST_RESUME_AT to if (prefs.contains(KEY_LAST_RESUME_AT)) prefs.getLong(KEY_LAST_RESUME_AT, -1L) else null,
            KEY_ACTIVITY_TYPE to prefs.getString(KEY_ACTIVITY_TYPE, null),
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
        private const val KEY_ACTIVITY_TYPE = "activity_type"

        /**
         * Pure serialization of a snapshot into a prefs-shaped map (lastResumeAt of -1 encodes
         * "paused"). Exposed for JVM unit tests; save()/load() delegate to these.
         */
        fun serialize(snapshot: TrackSessionSnapshot): Map<String, Any?> = mapOf(
            KEY_STATE to snapshot.state.name,
            KEY_STARTED_AT to snapshot.startedAtEpochMs,
            KEY_ACCUMULATED_MS to snapshot.accumulatedMs,
            KEY_LAST_RESUME_AT to (snapshot.lastResumeAtEpochMs ?: -1L),
            KEY_ACTIVITY_TYPE to snapshot.activityType,
        )

        /** Inverse of [serialize]; returns null for unknown/corrupt data (never crashes). */
        fun deserialize(map: Map<String, Any?>): TrackSessionSnapshot? {
            val stateName = map[KEY_STATE] as? String ?: return null
            val state = runCatching { RecordingState.valueOf(stateName) }.getOrNull() ?: return null
            val startedAt = map[KEY_STARTED_AT] as? Long ?: return null
            val accumulated = map[KEY_ACCUMULATED_MS] as? Long ?: return null
            val lastResume = (map[KEY_LAST_RESUME_AT] as? Long)?.takeIf { it >= 0 }
            // Sessions persisted before 8c carry no type; they restore with the default.
            val activityType = (map[KEY_ACTIVITY_TYPE] as? String)?.takeIf { it.isNotBlank() }
                ?: TrackSessionSnapshot.DEFAULT_ACTIVITY_TYPE
            return TrackSessionSnapshot(state, startedAt, accumulated, lastResume, activityType)
        }
    }
}

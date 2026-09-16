package com.fpclient.android.recording

/** Recording safety thresholds, kept independent of Android for deterministic tests. */
object RecordingHealth {
    const val NO_FIX_WARNING_MS = 2 * 60 * 1000L
    // Leave space for the final GPX and pending-upload metadata; never fill the disk.
    const val MIN_FREE_BYTES = 10 * 1024 * 1024L

    fun hasStorage(availableBytes: Long): Boolean = availableBytes >= MIN_FREE_BYTES

    /** Do not advance the timer/state until the new segment is durably writable. */
    fun resumeIfWritable(
        current: TrackSessionSnapshot,
        now: Long,
        availableBytes: Long,
        writeMarker: () -> Unit,
    ): TrackSessionSnapshot? {
        if (current.state != RecordingState.PAUSED || !hasStorage(availableBytes)) return null
        try {
            writeMarker()
        } catch (_: java.io.IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        return current.copy(state = RecordingState.RECORDING, lastResumeAtEpochMs = now)
    }

    fun missingFix(session: TrackSessionSnapshot?, lastFixAt: Long?, now: Long): Boolean {
        if (session == null || session.state != RecordingState.RECORDING) return false
        // A resume starts a fresh grace period, not a warning for time spent paused.
        val since = maxOf(session.lastResumeAtEpochMs ?: session.startedAtEpochMs,
            lastFixAt ?: session.startedAtEpochMs)
        return now - since >= NO_FIX_WARNING_MS
    }
}

package com.fpclient.android.wear.recording

/**
 * Pure re-arm policy for the platform PPG listener, used by [WorkoutRecordingService]'s
 * watchdog. All timestamps are `SystemClock.elapsedRealtime()` milliseconds; 0 means "never".
 */
internal object WorkoutHeartRateWatchdog {

    /**
     * No heart-rate callback at all for this long means the listener is starved, not warming up.
     * Several times the observed ~10 s PPG warm-up (Xiaomi Watch 2), so a re-arm — which restarts
     * that warm-up — never interrupts a sensor that is about to deliver.
     */
    const val STALL_MS = 30_000L

    /** Caps re-arms per stall so a watch off the wrist / without a PPG cannot loop forever. */
    const val MAX_ATTEMPTS = 3

    /**
     * True when the listener should be torn down and re-registered: recording, permitted, armed,
     * budget left, and silent — not even a 0 / no-contact callback — for [STALL_MS] since it was
     * armed or last spoke. Applies to a missing first reading and to mid-workout stalls alike.
     */
    fun shouldRearm(
        isRecording: Boolean,
        hasPermission: Boolean,
        armedAtMs: Long,
        lastEventAtMs: Long,
        attempts: Int,
        nowMs: Long,
    ): Boolean {
        if (!isRecording || !hasPermission || armedAtMs == 0L || attempts >= MAX_ATTEMPTS) return false
        return nowMs - maxOf(armedAtMs, lastEventAtMs) >= STALL_MS
    }
}

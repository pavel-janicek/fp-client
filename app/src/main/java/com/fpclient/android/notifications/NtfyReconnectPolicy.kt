package com.fpclient.android.notifications

import kotlin.math.min
import kotlin.math.pow

/**
 * The reconnect state machine of [InstantDeliveryService], extracted from the socket loop so
 * it can be unit-tested without Android, a service or a network.
 *
 * **This class exists because of a real bug.** ntfy's two subscribe modes behave completely
 * differently, and the first implementation treated them as one:
 *
 *  - `GET /{topic}/json` is a *long-lived stream* — it stays open and emits `keepalive`.
 *  - `GET /{topic}/json?poll=1` is a *one-shot request* — it answers in ~0.3 s and closes.
 *
 * The original loop set "replay the gap" after any stream end and then retried immediately,
 * so once it had fallen into poll mode it stayed there: poll (0.3 s) → poll (0.3 s) → poll…
 * That is several requests per second, indefinitely, from a foreground service that never
 * sleeps — and the user saw the result on their own server as **HTTP 429 Too Many Requests**,
 * reported by the app as a failing ntfy connection. The server was fine; the client was
 * flooding it.
 *
 * The rule encoded here is therefore: **a finished poll means the gap is filled, so go back to
 * the long-lived subscribe stream.** A poll is never retried in a tight loop, and every
 * non-urgent reconnect waits at least [MIN_RECONNECT_DELAY_MS].
 */
class NtfyReconnectPolicy(
    /** Wall-clock for the jitter in [delayMillis]; injectable so tests are deterministic. */
    private val elapsedRealtime: () -> Long,
    /** Base of the exponential failure backoff. */
    private val baseBackoffMs: Long,
    /** Ceiling of the exponential failure backoff. */
    private val maxBackoffMs: Long,
) {

    /** Consecutive failures so far; shown in the Settings card as "attempt N". */
    val attempt: Int
        get() = attemptCounter

    private var attemptCounter = 0

    /** Whether the next connection must use `?poll=1&since=…` instead of a subscribe. */
    private var replayNext = false

    /** How the last pass over the stream ended. */
    enum class Outcome {
        /** The stream closed cleanly (server-side close, or a poll finishing normally). */
        Ended,

        /** ntfy asked us to re-poll: we fell too far behind. */
        Replay,

        /** Transport or protocol failure. */
        Failed,
    }

    /** True when the next connection must use `?poll=1&since=…` instead of a subscribe. */
    val isGapReplay: Boolean
        get() = replayNext || attemptCounter > 0

    /**
     * Feeds one completed pass into the machine and returns how long to wait before the next
     * connection (0 = go now).
     *
     * [wasGapReplay] must be the value of [isGapReplay] *as it was when that connection
     * started* — i.e. whether the pass that just finished was a poll. That is the one piece
     * of state the machine cannot infer, and getting it wrong is exactly what caused the
     * flood, so it is a parameter rather than a hidden field.
     */
    fun record(outcome: Outcome, wasGapReplay: Boolean): Long {
        when (outcome) {
            // A clean end is not a failure: a server that closes idle streams should not
            // escalate the backoff forever.
            Outcome.Ended -> {
                attemptCounter = 0
                replayNext = true
            }

            Outcome.Failed -> {
                attemptCounter++
                replayNext = true
            }

            Outcome.Replay -> {
                attemptCounter = 0
                replayNext = true
            }
        }
        // The fix: a poll that completed is the *success* case of a gap replay, so the next
        // connection is a plain subscribe. Without this the machine would stay in poll mode
        // for ever.
        if (wasGapReplay && outcome != Outcome.Failed) {
            replayNext = false
            attemptCounter = 0
        }
        return when {
            // A gap the server explicitly told us about is time-critical: re-poll now.
            outcome == Outcome.Replay -> 0L
            // Failures get exponential backoff with full jitter (no lockstep reconnects).
            outcome == Outcome.Failed -> backoffMillis(attemptCounter)
            // Everything else waits the floor, which is the hard cap on request rate: even a
            // server that closes every connection instantly cannot be driven into a loop.
            else -> MIN_RECONNECT_DELAY_MS
        }
    }

    /** Exponential backoff with full jitter, bounded by the configured ceiling. */
    fun backoffMillis(attempt: Int): Long {
        if (attempt <= 0) return 0
        val exponential = baseBackoffMs.toDouble() * 2.0.pow((attempt - 1).coerceAtMost(16))
        val capped = minOf(exponential, maxBackoffMs.toDouble()).toLong()
        return (elapsedRealtime() % (capped + 1)).coerceAtLeast(baseBackoffMs)
    }

    companion object {
        /**
         * Floor between two clean reconnects. Below ntfy's default visitor burst allowance
         * (15) even in the pathological case where every connection is closed instantly.
         */
        const val MIN_RECONNECT_DELAY_MS = 2_000L
    }
}

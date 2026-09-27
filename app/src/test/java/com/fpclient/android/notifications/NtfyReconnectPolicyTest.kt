package com.fpclient.android.notifications

import com.fpclient.android.notifications.NtfyReconnectPolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the HTTP 429 flood (Iteration 8j).
 *
 * The bug this pins: ntfy's `?poll=1` is a one-shot request that answers in ~0.3 s and
 * closes, while a plain subscribe is a long-lived stream. The first implementation retried
 * immediately after any end-of-stream, so once it entered poll mode it never left — several
 * requests per second, forever, from a foreground service. The user's own ntfy server
 * answered with **HTTP 429** and the app reported it as a broken connection.
 *
 * These tests drive the policy with the *real* shape of that loop, so a future change that
 * reintroduces the spin fails here rather than on a user's phone.
 */
class NtfyReconnectPolicyTest {

    /** A fixed clock, so backoff is deterministic instead of jitter-dependent. */
    private var now = 0L
    private val policy = NtfyReconnectPolicy(
        elapsedRealtime = { now },
        baseBackoffMs = 2_000L,
        maxBackoffMs = 5 * 60_000L,
    )

    /** One pass through the service loop, as the real one runs it. */
    private fun connectAndRecord(outcome: Outcome): Long {
        val wasGapReplay = policy.isGapReplay
        val wait = policy.record(outcome, wasGapReplay)
        now += wait
        return wait
    }

    @Test
    fun aFreshPolicySubscribesRatherThanPolls() {
        assertFalse("the first connection must be a long-lived subscribe", policy.isGapReplay)
    }

    /**
     * The core regression. A dropped subscribe replays the gap, the poll finishes, and the
     * next connection must be a *subscribe* again. Before the fix this stayed in poll mode
     * and every subsequent wait was 0 — i.e. an unbounded request loop.
     */
    @Test
    fun aCompletedPollReturnsToSubscribeInsteadOfPollingForever() {
        // 1. The long-lived subscribe drops.
        connectAndRecord(Outcome.Ended)
        assertTrue("a dropped stream must replay the gap", policy.isGapReplay)

        // 2. The poll completes — this is the moment the old code looped.
        val waitAfterPoll = connectAndRecord(Outcome.Ended)

        assertFalse(
            "after a gap replay the app must go back to a subscribe, not poll again",
            policy.isGapReplay,
        )
        assertTrue(
            "a completed poll must not reconnect instantly (that was the 429 flood)",
            waitAfterPoll > 0,
        )
    }

    /**
     * The property that actually matters, stated as a bound: over any window of simulated
     * time, the loop must not exceed a sane request rate — even in the pathological case
     * where *every* connection (including the subscribe) closes instantly.
     */
    @Test
    fun evenAPerverThatClosesEveryConnectionInstantlyCannotBeFlooded() {
        var requests = 0
        // 60 s of wall clock, every connection closing immediately.
        repeat(600) {
            if (now >= 60_000) return@repeat
            requests++
            connectAndRecord(Outcome.Ended)
        }
        val elapsed = now.coerceAtLeast(1)
        val perMinute = requests * 60_000 / elapsed
        assertTrue(
            "must stay far below ntfy's rate limit, was $requests requests in ${elapsed}ms",
            perMinute < 40,
        )
        assertTrue("the loop must actually be connecting", requests >= 2)
    }

    @Test
    fun failuresBackOffExponentiallyAndAreCapped() {
        var previous = 0L
        repeat(12) { attempt ->
            now = 0L // fixed clock: compare the deterministic exponential ceiling
            val wait = connectAndRecord(Outcome.Failed)
            assertTrue("attempt $attempt waited $wait", wait >= 2_000L)
            assertTrue("attempt $attempt waited $wait", wait <= 5 * 60_000L)
            if (attempt < 6) assertTrue("backoff must grow", wait >= previous)
            previous = wait
        }
        assertEquals(12, policy.attempt)
    }

    @Test
    fun aCleanCloseResetsTheFailureCounterSoBackoffNeverRatchets() {
        repeat(5) { connectAndRecord(Outcome.Failed) }
        assertEquals(5, policy.attempt)
        connectAndRecord(Outcome.Ended)
        // A server that closes idle streams must not permanently escalate the backoff.
        assertTrue(policy.attempt <= 1)
    }

    @Test
    fun aServerRequestedPollRequestReconnectsImmediately() {
        // The subscribe is up, ntfy says "you fell behind" — the gap is time-critical.
        val wait = connectAndRecord(Outcome.Replay)
        assertEquals("a server-requested replay must not wait", 0L, wait)
        assertTrue("the next connection must be the gap-replay poll", policy.isGapReplay)
    }

    @Test
    fun jitteredBackoffStaysWithinItsBounds() {
        val bounds = NtfyReconnectPolicy(
            elapsedRealtime = { 0L },
            baseBackoffMs = 2_000L,
            maxBackoffMs = 300_000L,
        )
        assertEquals(0, bounds.backoffMillis(0))
        assertTrue(bounds.backoffMillis(1) >= 2_000L)
        for (attempt in 1..50) {
            val wait = bounds.backoffMillis(attempt)
            assertTrue("attempt $attempt = $wait", wait in 2_000L..300_000L)
        }
    }

    @Test
    fun theFloorIsBelowNtfysDefaultBurstAllowance() {
        // ntfy's default visitor limit allows 15 requests then throttles. Two clean
        // reconnects per second stays comfortably under that even in the worst case.
        val perSecond = 1000 / NtfyReconnectPolicy.MIN_RECONNECT_DELAY_MS
        assertTrue("must not be a hot loop", perSecond <= 1)
    }
}

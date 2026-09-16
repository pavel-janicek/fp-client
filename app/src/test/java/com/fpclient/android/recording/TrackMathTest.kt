package com.fpclient.android.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure tracking math (PLAN 8b: "unit tests for distance/elevation
 * math"): haversine distance, elevation gain with smoothing + hysteresis, and pace.
 */
class TrackMathTest {

    // ------------------------------------------------------------- haversine

    @Test
    fun `one degree of latitude is about 111_2 km`() {
        val d = TrackMath.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_194.9, d, 1.0) // 6371000 * pi/180
    }

    @Test
    fun `one degree of longitude at the equator is about 111_2 km`() {
        val d = TrackMath.haversineMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_194.9, d, 1.0)
    }

    @Test
    fun `one degree of longitude shrinks with the cosine of the latitude`() {
        val equator = TrackMath.haversineMeters(0.0, 0.0, 0.0, 1.0)
        val at60 = TrackMath.haversineMeters(60.0, 0.0, 60.0, 1.0)
        assertEquals(equator / 2, at60, 1.0) // cos(60°) = 0.5
    }

    @Test
    fun `identical points have zero distance`() {
        val p = TrackPoint(47.3769, 8.5417, 400.0, 0L, 5.0)
        assertEquals(0.0, TrackMath.haversineMeters(p, p), 1e-9)
    }

    @Test
    fun `quarter meridian from pole to equator is about 10_008 km`() {
        val d = TrackMath.haversineMeters(90.0, 0.0, 0.0, 0.0)
        assertEquals(10_007_543.0, d, 5.0) // 6371000 * pi/2
    }

    @Test
    fun `distance is symmetric and world-scale plausible`() {
        val a = TrackPoint(47.3769, 8.5417, 400.0, 0L, 5.0)
        val b = TrackPoint(46.5197, 6.6323, 400.0, 0L, 5.0) // Zurich -> Lausanne
        val ab = TrackMath.haversineMeters(a, b)
        assertEquals(ab, TrackMath.haversineMeters(b, a), 1e-9)
        // Great-circle sanity (~173 km: 95 km N-S, 145 km E-W at that latitude).
        assertEquals(173_000.0, ab, 5_000.0)
    }

    // ------------------------------------------------------------- accuracy filter

    @Test
    fun `accuracy filter accepts good fixes and rejects bad ones`() {
        assertTrue(TrackMath.isAcceptableAccuracy(0.0))
        assertTrue(TrackMath.isAcceptableAccuracy(19.9))
        assertFalse(TrackMath.isAcceptableAccuracy(20.1))
        assertFalse(TrackMath.isAcceptableAccuracy(-1.0)) // nonsense accuracy
    }

    // ------------------------------------------------------------- pace

    @Test
    fun `pace is moving time over distance`() {
        // 30 min (1800 s) over 6 km -> 300 s/km = 5:00 /km
        assertEquals(300L, TrackMath.paceSecondsPerKm(30 * 60_000L, 6_000.0))
    }

    @Test
    fun `pace is null before a meaningful distance exists`() {
        assertNull(TrackMath.paceSecondsPerKm(60_000L, 5.0)) // 5 m of GPS jitter
        assertNull(TrackMath.paceSecondsPerKm(0L, 6_000.0))
        assertNull(TrackMath.paceSecondsPerKm(-1L, 6_000.0))
    }

    // ------------------------------------------------------------- elevation gain

    @Test
    fun `flat track produces zero gain`() {
        val acc = ElevationAccumulator()
        repeat(20) { acc.add(100.0) }
        assertEquals(0.0, acc.add(100.0), 1e-9)
    }

    @Test
    fun `a clean climb counts its full rise`() {
        // window = 1 disables smoothing so the assertions can be exact; the smoothing
        // behavior itself is covered by the spike test below.
        val acc = ElevationAccumulator(window = 1)
        acc.add(100.0)
        assertEquals(10.0, acc.add(110.0), 1e-9)
        assertEquals(25.0, acc.add(125.0), 1e-9) // +15 from the new pivot at 110
    }

    @Test
    fun `sub-threshold noise never accumulates gain`() {
        val acc = ElevationAccumulator()
        acc.add(100.0)
        // ±1-2 m of GPS altitude jitter around the same elevation
        val noise = listOf(101.5, 99.0, 102.0, 100.0, 101.0, 99.5, 102.0, 100.5)
        noise.forEach { acc.add(it) }
        assertEquals(0.0, acc.add(100.0), 1e-9)
    }

    @Test
    fun `descent then climb counts only the climb from the low point`() {
        val acc = ElevationAccumulator(window = 1)
        acc.add(200.0)
        acc.add(100.0) // -100: descent, pivot resets to the low point
        assertEquals(0.0, acc.add(95.0), 1e-9) // still a descent: no gain
        assertEquals(35.0, acc.add(130.0), 1e-9) // climb measured from 95, not from 200
    }

    @Test
    fun `a single spike with a wide smoothing window and threshold is swallowed`() {
        val acc = ElevationAccumulator(thresholdM = 12.0, window = 3)
        acc.add(100.0)
        acc.add(110.0) // window avg = 105 -> delta 5, below the 12 m threshold
        acc.add(100.0)
        assertEquals(0.0, acc.add(100.0), 1e-9)
    }

    @Test
    fun `staircase climbs accumulate across repeated rises`() {
        val acc = ElevationAccumulator(window = 1)
        var elevation = 0.0
        acc.add(elevation)
        var expected = 0.0
        repeat(5) {
            elevation += 10.0
            acc.add(elevation)
            expected += 10.0
            elevation -= 3.0
            acc.add(elevation)
        }
        assertEquals(expected, acc.add(elevation), 0.5)
    }

    // ------------------------------------------------------------- stats folding

    @Test
    fun `stats accumulator folds distance across points`() {
        val acc = TrackStatsAccumulator()
        val a = TrackPoint(0.0, 0.0, 0.0, 0L, 1.0)
        val b = TrackPoint(0.0, 1.0, 0.0, 1_000L, 1.0)
        acc.add(a)
        acc.add(b)
        assertEquals(2, acc.stats.pointCount)
        assertEquals(TrackMath.haversineMeters(a, b), acc.stats.distanceM, 1e-6)
        assertEquals(b, acc.lastPoint)
    }

    @Test
    fun `replaying a session's points reproduces the same stats after process death`() {
        // What the service does on a START_STICKY restore: rebuild stats from the file.
        val points = listOf(
            TrackPoint(47.3769, 8.5417, 400.0, 0L, 2.0),
            TrackPoint(47.3800, 8.5450, 405.0, 2_000L, 3.0),
            TrackPoint(47.3830, 8.5490, 412.0, 4_000L, 4.0),
            TrackPoint(47.3860, 8.5530, 410.0, 6_000L, 5.0),
            TrackPoint(47.3890, 8.5570, 425.0, 8_000L, 3.0),
        )
        val live = TrackStatsAccumulator()
        points.forEach { live.add(it) }
        val restored = TrackStatsAccumulator() // readAll() + fold, same pure path
        points.forEach { restored.add(it) }
        assertEquals(live.stats, restored.stats)
        assertEquals(live.lastPoint, restored.lastPoint)
        assertTrue(restored.stats.distanceM > 0)
        assertTrue(restored.stats.elevationGainM > 0)
    }
}

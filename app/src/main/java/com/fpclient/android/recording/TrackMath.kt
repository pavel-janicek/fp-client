package com.fpclient.android.recording

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure math for the tracking engine — no Android dependencies, fully unit tested.
 * All distances in meters, elevations in meters, times in milliseconds.
 */
object TrackMath {

    /** Mean Earth radius (meters). */
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Fixes less accurate than this are discarded outright (PLAN 8b: ~20 m). */
    const val MAX_ACCURACY_M = 20.0

    /** Below this distance the pace is undefined (GPS noise would produce absurd values). */
    private const val MIN_DISTANCE_FOR_PACE_M = 10.0

    /**
     * Great-circle distance between two points via the haversine formula
     * (Earth mean radius; good to ~0.5 % for running/biking distances).
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun haversineMeters(from: TrackPoint, to: TrackPoint): Double =
        haversineMeters(from.lat, from.lon, to.lat, to.lon)

    /**
     * Average moving pace in seconds per kilometer; null before enough distance exists
     * to make the number meaningful (GPS jitter would otherwise produce silly paces).
     */
    fun paceSecondsPerKm(movingMs: Long, distanceM: Double): Long? {
        if (movingMs <= 0 || distanceM < MIN_DISTANCE_FOR_PACE_M) return null
        val km = distanceM / 1000.0
        val seconds = movingMs / 1000.0
        return (seconds / km).roundToLong().coerceAtLeast(0L)
    }

    /** True when the fix is accurate enough to be recorded. */
    fun isAcceptableAccuracy(accuracyM: Double): Boolean =
        accuracyM in 0.0..MAX_ACCURACY_M
}

/**
 * Live totals of a recording session, published through [TrackRecordingBus.stats].
 * Elapsed/moving time are NOT part of this — they derive from the session snapshot's
 * wall-clock math — while distance/elevation/point-count accrue from accepted fixes.
 */
data class TrackStats(
    val pointCount: Int = 0,
    val distanceM: Double = 0.0,
    val elevationGainM: Double = 0.0,
    /** Total descent, in meters (positive value, kept separate from gain). */
    val elevationLossM: Double = 0.0,
) {
    val hasFixes: Boolean get() = pointCount > 0
}

/**
 * Folds accepted [TrackPoint]s into running [TrackStats]. Used live by the service and,
 * after a process death, to replay the persisted track file into the same totals.
 */
class TrackStatsAccumulator {

    var stats: TrackStats = TrackStats()
        private set

    private val elevation = ElevationAccumulator()

    fun add(point: TrackPoint) {
        val previous = lastPoint
        val delta = previous?.let { TrackMath.haversineMeters(it, point) } ?: 0.0
        elevation.add(point.ele)
        stats = stats.copy(
            pointCount = stats.pointCount + 1,
            distanceM = stats.distanceM + delta,
            elevationGainM = elevation.gainM,
            elevationLossM = elevation.lossM,
        )
        lastPoint = point
    }

    /** Last accepted point; needed by the service to continue the segment after a restore. */
    var lastPoint: TrackPoint? = null
        private set

    /**
     * Segment boundary (pause/resume): gain and loss stay per-segment — the jump
     * across the gap is transport, not climbing. Resets the smoothing window and the
     * pivot so the first fix of the new segment seeds it fresh (the same way the very
     * first fix of a session does), instead of folding the gap into the totals.
     */
    fun startNewSegment() {
        elevation.reset()
        lastPoint = null
    }
}

/**
 * Elevation gain with smoothing: GPS altitude bounces by several meters between fixes,
 * so raw sum-of-positive-deltas would "climb" while standing still. Two mitigations:
 *
 *  1. smoothing — each fix contributes the average of the last [window] elevations;
 *  2. hysteresis — the accumulator keeps a pivot; gain only accrues once the smoothed
 *     elevation has risen at least [thresholdM] above the pivot (and the pivot resets on
 *     equal drops), so sub-threshold noise contributes exactly nothing.
 */
class ElevationAccumulator(
    private val thresholdM: Double = 3.0,
    private val window: Int = 3,
) {

    private val recent = ArrayDeque<Double>()
    private var pivot: Double? = null
    var gainM = 0.0
        private set
    /** Total descent, in meters (positive value, kept separate from gain). */
    var lossM = 0.0
        private set

    /** Feeds one fix elevation; accumulated gain/loss are read from [gainM]/[lossM]. */
    fun add(elevationM: Double) {
        val smoothed = smooth(elevationM)
        val base = pivot
        if (base == null) {
            pivot = smoothed
            return
        }
        val delta = smoothed - base
        when {
            delta >= thresholdM -> {
                gainM += delta
                pivot = smoothed
            }
            delta <= -thresholdM -> {
                // Descent: the drop counts as loss, and the pivot resets to the low
                // point so a later climb only counts from there — never re-summing
                // the descent back as gain.
                lossM += -delta
                pivot = smoothed
            }
            // Sub-threshold jitter: pivot stays put, nothing accumulates.
        }
    }

    /** Segment boundary: the next fix seeds a fresh pivot, like the session's first fix. */
    fun reset() {
        recent.clear()
        pivot = null
    }

    private fun smooth(elevationM: Double): Double {
        recent.addLast(elevationM)
        while (recent.size > window) recent.removeFirst()
        return recent.average()
    }
}

package com.fpclient.android.util

import com.fpclient.android.data.dto.ActivityTrimDataDto
import com.fpclient.android.data.dto.ActivityTrimPointDto
import com.fpclient.android.recording.TrackMath
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor

/**
 * What a trim selection produces, computed with the *server's* own rules so the figures shown
 * before saving are the figures the instance will store.
 *
 * Applying a trim is an ordinary activity update (`PUT /api/web/activities/{id}` carrying a
 * `trim` object), and the server then rebuilds the activity from the retained points
 * (`ActivityTrimmingService.calculate`). The parts the app can reproduce exactly are:
 *
 *  - **distance** — the haversine sum over the retained points, which becomes the server's
 *    `totalDistance`. The trim DTO's per-point `distance` is deliberately *not* used: that is
 *    the parser's cumulative value, while the server recomputes the sub-range from scratch
 *    rather than subtracting two rounded totals.
 *  - **duration** — `end.timestamp - start.timestamp`, i.e. the server's `Duration.between`.
 *  - **elevation gain/loss** — a port of the server's `ElevationCalculationService` (5 m
 *    resampling, centred 70 m median, 2 m hysteresis, runs broken by `elevationSegment`
 *    changes and by unusable points, 1 000 000-sample cap → unavailable). The server ships the
 *    same algorithm to the web editor as `elevation-calculation.js`, and `TrimPreviewTest` pins
 *    this port against that file's own output, so a drift between app and server fails a test
 *    instead of quietly showing a different climb.
 *  - for the two ranges whose elevation is already stored, the *stored* values win
 *    (`currentElevation*` / `originalElevation*`) — the web editor's own shortcut, and one
 *    that cannot drift at all.
 *
 * Speed metrics, timezone and start location are recalculated by the server too, but they
 * depend on the per-point speed policy (`ActivitySpeedPolicy`); the app shows the
 * authoritative values from the update response instead of guessing them.
 */
object TrimPreview {

    /** Preview of one candidate selection. Null elevations mean the server's own algorithm reports them as unavailable. */
    data class Result(
        val retainedPoints: Int,
        val distanceMeters: Double,
        val durationSeconds: Long?,
        val elevationGainMeters: Double?,
        val elevationLossMeters: Double?,
    )

    /**
     * Preview of keeping `points[startIndex..endIndex]` (indices into the trim DTO's
     * **original** track). Out-of-range values are clamped; a selection shorter than two
     * points yields an empty result, which is also what the server rejects.
     */
    fun preview(data: ActivityTrimDataDto, startIndex: Int, endIndex: Int): Result {
        val points = data.points
        val start = startIndex.coerceAtLeast(0)
        val end = endIndex.coerceAtMost(points.lastIndex)
        if (points.size < 2 || start >= end) {
            return Result(0, 0.0, null, null, null)
        }

        var distance = 0.0
        for (i in start + 1..end) {
            distance += TrackMath.haversineMeters(
                points[i - 1].latitude,
                points[i - 1].longitude,
                points[i].latitude,
                points[i].longitude,
            )
        }

        val elevation = elevationTotals(data, start, end)

        return Result(
            retainedPoints = end - start + 1,
            distanceMeters = round(distance),
            durationSeconds = elapsedSeconds(points[start].timestamp, points[end].timestamp),
            elevationGainMeters = elevation.first,
            elevationLossMeters = elevation.second,
        )
    }

    /**
     * Elevation gain/loss for the retained range, with the web editor's two shortcuts: a
     * selection equal to the stored range or to the whole original track reports the values
     * the server already has for it.
     */
    internal fun elevationTotals(data: ActivityTrimDataDto, startIndex: Int, endIndex: Int): Pair<Double?, Double?> {
        if (startIndex == data.currentStartIndex && endIndex == data.currentEndIndex) {
            return data.currentElevationGain to data.currentElevationLoss
        }
        if (startIndex == 0 && endIndex == data.points.lastIndex) {
            return data.originalElevationGain to data.originalElevationLoss
        }
        return calculate(data.points.subList(startIndex, endIndex + 1))
    }

    private fun elapsedSeconds(fromIso: String?, toIso: String?): Long? {
        if (fromIso.isNullOrBlank() || toIso.isNullOrBlank()) return null
        return try {
            Duration.between(Instant.parse(fromIso), Instant.parse(toIso)).seconds
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------------------
    // Port of the server's ElevationCalculationService (see the class KDoc for why it is here).
    // -------------------------------------------------------------------------------------

    private const val MAX_SAMPLES = 1_000_000

    private const val STEP = 5.0

    private const val RADIUS = 35.0

    private class Totals {
        var samples = 0
        var usable = false
        var ascent = 0.0
        var descent = 0.0
    }

    private class SampleLimitExceeded : RuntimeException()

    private data class Sample(val distance: Double, val elevation: Double)

    private class Hysteresis {
        private var initialized = false
        private var direction = 0
        private var low = 0.0
        private var high = 0.0
        var ascent = 0.0
        var descent = 0.0

        fun accept(height: Double) {
            if (!initialized) {
                low = height
                high = height
                initialized = true
                return
            }
            when {
                direction == 0 -> {
                    low = minOf(low, height)
                    high = maxOf(high, height)
                    if (height - low >= 2) {
                        direction = 1
                        high = height
                    } else if (high - height >= 2) {
                        direction = -1
                        low = height
                    }
                }
                direction > 0 -> {
                    high = maxOf(high, height)
                    if (high - height >= 2) {
                        ascent += high - low
                        direction = -1
                        low = height
                    }
                }
                else -> {
                    low = minOf(low, height)
                    if (height - low >= 2) {
                        descent += high - low
                        direction = 1
                        high = height
                    }
                }
            }
        }

        fun finish() {
            if (direction > 0) ascent += high - low
            else if (direction < 0) descent += high - low
        }
    }

    /** One continuous stretch of usable elevation; a segment change or an unusable point starts a new one. */
    private class Run(private val totals: Totals) {

        private val window = ArrayList<Sample>()
        private val hysteresis = Hysteresis()
        private var centre = 0
        private var emitted = 0
        private var travelled = 0.0
        private var next = 0.0
        private var lastSample = -1.0
        private var lastElevation = 0.0

        /** Adds an edge of [length] metres climbing from [from] to [to], resampled every [STEP] metres. */
        fun edge(length: Double, from: Double, to: Double) {
            val end = travelled + length
            val required = floor(end / STEP) + 1 + if (end % STEP == 0.0) 0.0 else 1.0
            if (totals.samples - emitted + required > MAX_SAMPLES) throw SampleLimitExceeded()
            totals.usable = true
            while (next <= end) {
                val fraction = (next - travelled) / length
                add(next, from + fraction * (to - from))
                next += STEP
            }
            travelled = end
            lastElevation = to
        }

        private fun add(position: Double, elevation: Double) {
            if (++totals.samples > MAX_SAMPLES) throw SampleLimitExceeded()
            emitted++
            lastSample = position
            window.add(Sample(position, elevation))
            drain(false)
        }

        private fun drain(end: Boolean) {
            while (centre < window.size &&
                (end || window.last().distance >= window[centre].distance + RADIUS)
            ) {
                val position = window[centre].distance
                val heights = window
                    .filter { it.distance >= position - RADIUS && it.distance <= position + RADIUS }
                    .map { it.elevation }
                    .sorted()
                val middle = heights.size / 2
                val median = if (heights.size % 2 == 0) {
                    (heights[middle - 1] + heights[middle]) / 2
                } else {
                    heights[middle]
                }
                hysteresis.accept(median)
                centre++
                if (centre < window.size) {
                    val minimum = window[centre].distance - RADIUS
                    while (centre > 0 && window.first().distance < minimum) {
                        window.removeAt(0)
                        centre--
                    }
                }
            }
        }

        fun finish() {
            if (travelled == 0.0) return
            if (lastSample != travelled) add(travelled, lastElevation)
            drain(true)
            hysteresis.finish()
            totals.ascent += hysteresis.ascent
            totals.descent += hysteresis.descent
        }
    }

    /** Exposed for `TrimPreviewTest`, which pins it against the server JS's own output. */
    internal fun calculate(points: List<ActivityTrimPointDto>): Pair<Double?, Double?> {
        val totals = Totals()
        var run = Run(totals)
        var previous: ActivityTrimPointDto? = null
        try {
            for (point in points) {
                if (!usable(point)) {
                    run.finish()
                    run = Run(totals)
                    previous = null
                    continue
                }
                if (previous != null && previous.elevationSegment != point.elevationSegment) {
                    run.finish()
                    run = Run(totals)
                    previous = null
                }
                if (previous != null) {
                    val length = TrackMath.haversineMeters(
                        previous.latitude,
                        previous.longitude,
                        point.latitude,
                        point.longitude,
                    )
                    // A repeated position carries no new elevation information; the server skips
                    // it, and does not advance its "previous" point either.
                    if (length == 0.0) continue
                    run.edge(length, previous.elevation ?: 0.0, point.elevation ?: 0.0)
                }
                previous = point
            }
            run.finish()
        } catch (_: SampleLimitExceeded) {
            return null to null
        }
        return if (totals.usable) round(totals.ascent) to round(totals.descent) else null to null
    }

    /** The server's `ElevationPoint.usable`. */
    private fun usable(point: ActivityTrimPointDto): Boolean {
        val elevation = point.elevation
        return point.latitude.isFinite() && abs(point.latitude) <= 90 &&
            point.longitude.isFinite() && abs(point.longitude) <= 180 &&
            elevation != null && elevation.isFinite()
    }

    /** The server's `BigDecimal.valueOf(value).setScale(2, HALF_UP)`, including decimal ties. */
    private fun round(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
}

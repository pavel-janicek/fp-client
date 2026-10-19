package com.fpclient.android.garmin

import com.garmin.fit.DateTime
import com.garmin.fit.File as FitFileType
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import java.io.File
import java.util.Date
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds the FIT FitPub ingests from a Garmin watch workout: the samples the FP Wear Connect IQ
 * app sent (1 Hz, see [GarminProtocol.Sample]) plus its summary. The same record shape as the Wear
 * OS app's `WorkoutFitWriter` — duplicated, not shared, like every `:wear`/`:app` pair (docs/WEAR.md
 * decision 1) — so FitPub parses both alike:
 * - one record per GPS fix carrying the heart rate (FitPub's FitParser starts a new elevation
 *   segment at every record without a position); without any fix, one record per HR/steps sample;
 * - `session.totalStrides` from the step count (FitPub stores `steps = strides * 2`), cadence in
 *   strides per minute, none for cycling;
 * - moving time and total distance from the watch's native session (the numbers Garmin Connect
 *   shows), elapsed time from the samples.
 */
internal object GarminFitWriter {

    internal const val PRODUCT_NAME = "FP Wear"

    /** A gap longer than this between samples is a pause: no distance or cadence across it. */
    internal const val MAX_GAP_SECONDS = 10L
    private const val CADENCE_WINDOW_SECONDS = 20L
    private const val MIN_CADENCE_WINDOW_SECONDS = 5L
    /** FIT cadence is a uint8; anything above is sensor garbage, not a human stride rate. */
    private const val MAX_CADENCE_SPM = 254
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun write(file: File, entry: GarminInboxEntry, samples: List<GarminProtocol.Sample>, title: String): File {
        file.parentFile?.mkdirs()
        val startMs = entry.createdEpochSeconds * 1000
        val elapsedSeconds = samples.lastOrNull()?.secondsSinceStart?.plus(1) ?: entry.durationSeconds ?: 0L
        val stopMs = startMs + elapsedSeconds * 1000
        val movingSeconds = (entry.durationSeconds ?: elapsedSeconds).toFloat()
        val fitSport = sportFor(entry.sport)

        val encoder = FileEncoder(file, Fit.ProtocolVersion.V2_0)
        encoder.write(FileIdMesg().apply {
            type = FitFileType.ACTIVITY
            manufacturer = Manufacturer.DEVELOPMENT
            product = 1
            productName = PRODUCT_NAME
            serialNumber = entry.createdEpochSeconds
            timeCreated = DateTime(Date(startMs))
        })

        val hasGps = samples.any { it.hasFix() }
        val heartRates = samples.mapNotNull { it.validHeartRate() }
        val cadence = if (fitSport != Sport.CYCLING) CadenceTimeline(samples) else null
        var maxCadence: Short? = null
        var distance = 0.0
        var previousFix: GarminProtocol.Sample? = null
        for (sample in samples) {
            if (sample.hasFix()) {
                val previous = previousFix
                if (previous != null && sample.secondsSinceStart - previous.secondsSinceStart <= MAX_GAP_SECONDS) {
                    distance += distanceMeters(previous, sample)
                }
                previousFix = sample
            }
            val writeRecord = if (hasGps) sample.hasFix() else sample.validHeartRate() != null || sample.steps != null
            if (!writeRecord) continue
            val recordCadence = cadence?.stridesPerMinuteAt(sample.secondsSinceStart)
            if (recordCadence != null && recordCadence > (maxCadence ?: 0)) maxCadence = recordCadence
            encoder.write(RecordMesg().apply {
                timestamp = DateTime(Date(startMs + sample.secondsSinceStart * 1000))
                sample.validHeartRate()?.let { heartRate = it.toShort() }
                recordCadence?.let { this.cadence = it }
                if (sample.latE6 != null && sample.lonE6 != null) {
                    positionLat = semicircles(sample.latE6)
                    positionLong = semicircles(sample.lonE6)
                }
                sample.altitudeDm?.let { altitude = it / 10f }
                if (distance > 0) this.distance = distance.toFloat()
            })
        }

        val sessionDistance = (entry.distanceMeters?.toDouble()?.takeIf { it > 0 } ?: distance).toFloat()
        val avgHr = heartRates.takeIf { it.isNotEmpty() }?.average()?.toInt()?.toShort()
        val maxHr = heartRates.maxOrNull()?.toShort()
        val totalStrides = stridesFor(samples.mapNotNull { it.steps }.maxOrNull() ?: 0L)
        val avgCadence = cadence?.let { averageCadence(totalStrides, movingSeconds) }

        encoder.write(LapMesg().apply {
            timestamp = DateTime(Date(stopMs))
            startTime = DateTime(Date(startMs))
            totalElapsedTime = elapsedSeconds.toFloat()
            totalTimerTime = movingSeconds
            totalDistance = sessionDistance
            sport = fitSport
            subSport = SubSport.GENERIC
            avgHr?.let { avgHeartRate = it }
            maxHr?.let { maxHeartRate = it }
            totalStrides?.let { this.totalStrides = it }
            avgCadence?.let { this.avgCadence = it }
            maxCadence?.let { this.maxCadence = it }
        })
        encoder.write(SessionMesg().apply {
            timestamp = DateTime(Date(stopMs))
            startTime = DateTime(Date(startMs))
            totalElapsedTime = elapsedSeconds.toFloat()
            totalTimerTime = movingSeconds
            totalDistance = sessionDistance
            sport = fitSport
            subSport = SubSport.GENERIC
            firstLapIndex = 0
            numLaps = 1
            avgHr?.let { avgHeartRate = it }
            maxHr?.let { maxHeartRate = it }
            totalStrides?.let { this.totalStrides = it }
            avgCadence?.let { this.avgCadence = it }
            maxCadence?.let { this.maxCadence = it }
            sportProfileName = title
        })
        encoder.close()
        return file
    }

    /** The FIT sport for a watch activity key — the mapping of the Wear OS `WorkoutFitWriter`. */
    internal fun sportFor(key: String?): Sport = when (key) {
        "RUN" -> Sport.RUNNING
        "BIKE" -> Sport.CYCLING
        "HIKE" -> Sport.HIKING
        "WALK" -> Sport.WALKING
        else -> Sport.GENERIC
    }

    /** Strides for [steps] (rounded to the nearest stride), or null when no steps were counted. */
    internal fun stridesFor(steps: Long): Long? = steps.takeIf { it > 0 }?.let { (it + 1) / 2 }

    /** Session average in strides per minute, or null without strides or moving time. */
    internal fun averageCadence(totalStrides: Long?, movingSeconds: Float): Short? {
        if (totalStrides == null || movingSeconds < MIN_CADENCE_WINDOW_SECONDS) return null
        return (totalStrides * 60.0 / movingSeconds).roundToInt().takeIf { it in 1..MAX_CADENCE_SPM }?.toShort()
    }

    internal fun distanceMeters(a: GarminProtocol.Sample, b: GarminProtocol.Sample): Double {
        val lat1 = Math.toRadians(a.latE6!! / 1e6)
        val lat2 = Math.toRadians(b.latE6!! / 1e6)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians((b.lonE6!! - a.lonE6!!) / 1e6)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    private fun semicircles(degreesE6: Long): Int = (degreesE6 / 1e6 / 180.0 * 2147483648.0).toInt()

    private fun GarminProtocol.Sample.hasFix() = latE6 != null && lonE6 != null

    private fun GarminProtocol.Sample.validHeartRate(): Int? = heartRate?.toInt()?.takeIf { it in 1..300 }

    /**
     * Instantaneous cadence from the cumulative step counts: strides per minute over the last
     * [CADENCE_WINDOW_SECONDS], never reaching back across a pause (a gap > [MAX_GAP_SECONDS]).
     */
    private class CadenceTimeline(samples: List<GarminProtocol.Sample>) {
        private val steps = samples.filter { it.steps != null }.map { it.secondsSinceStart to it.steps!! }
        private val segmentStarts = samples.zipWithNext()
            .filter { (a, b) -> b.secondsSinceStart - a.secondsSinceStart > MAX_GAP_SECONDS }
            .map { (_, b) -> b.secondsSinceStart }

        fun stridesPerMinuteAt(seconds: Long): Short? {
            if (steps.isEmpty()) return null
            val segmentStart = segmentStarts.lastOrNull { it <= seconds } ?: Long.MIN_VALUE
            val windowStart = maxOf(seconds - CADENCE_WINDOW_SECONDS, segmentStart, steps.first().first)
            val window = seconds - windowStart
            if (window < MIN_CADENCE_WINDOW_SECONDS) return null
            val delta = stepsAt(seconds) - stepsAt(windowStart)
            return (delta / 2.0 * 60.0 / window).roundToInt().coerceIn(0, MAX_CADENCE_SPM).toShort()
        }

        private fun stepsAt(seconds: Long): Long = steps.lastOrNull { it.first <= seconds }?.second ?: 0
    }
}

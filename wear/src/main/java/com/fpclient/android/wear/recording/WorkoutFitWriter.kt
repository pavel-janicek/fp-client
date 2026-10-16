package com.fpclient.android.wear.recording

import com.garmin.fit.DateTime
import com.garmin.fit.File
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import java.io.File as JavaFile
import java.util.Date
import kotlin.math.roundToInt

object WorkoutFitWriter {

    fun write(
        directory: JavaFile,
        session: WorkoutSessionSnapshot,
        events: List<WorkoutTrackEvent>,
        stoppedAtEpochMs: Long,
        title: String? = null,
    ): JavaFile {
        directory.mkdirs()
        val sessionId = session.startedAtEpochMs
        val fitFile = JavaFile(directory, "workout-$sessionId.fit")

        val encoder = FileEncoder(fitFile, Fit.ProtocolVersion.V2_0)

        // 1. File ID Message
        val fileIdMesg = FileIdMesg().apply {
            type = File.ACTIVITY
            manufacturer = Manufacturer.DEVELOPMENT
            product = 1
            // Names the recording app for anything reading the FIT source metadata (FitPub's
            // FitParser already reads file_id product_name); the phone's GPX says "FP-Client".
            productName = PRODUCT_NAME
            serialNumber = sessionId
            timeCreated = DateTime(Date(session.startedAtEpochMs))
        }
        encoder.write(fileIdMesg)

        // Calculate summary metrics
        val movingMs = session.movingMsAt(stoppedAtEpochMs)
        val elapsedMs = session.elapsedMsAt(stoppedAtEpochMs)
        val movingSec = (movingMs / 1000.0).toFloat().coerceAtLeast(0f)
        val elapsedSec = (elapsedMs / 1000.0).toFloat().coerceAtLeast(0f)

        val hrSamples = events.mapNotNull { it.heartRateBpm }.filter { it in 1..300 }
        val avgHr = if (hrSamples.isNotEmpty()) hrSamples.average().toInt().toShort() else null
        val maxHr = if (hrSamples.isNotEmpty()) hrSamples.maxOrNull()?.toShort() else null

        val fitSport = when (session.activityType) {
            WorkoutActivityType.RUN -> Sport.RUNNING
            WorkoutActivityType.BIKE -> Sport.CYCLING
            WorkoutActivityType.HIKE -> Sport.HIKING
            WorkoutActivityType.WALK -> Sport.WALKING
            WorkoutActivityType.OTHER -> Sport.GENERIC
        }

        // One record per GPS fix, tagged with the nearest heart-rate reading — the same shape as
        // the GPX export. FitPub's FitParser starts a new elevation segment at every record
        // without a position, so interleaving separate HR-only / steps-only records (HR ~1 Hz,
        // steps on their own cadence) left every GPS point alone in its segment: no elevation
        // gain could be computed (`elevationGain` null), and no track point carried HR either.
        // Workouts without any GPS fix (indoor) keep one record per HR/steps event.
        val hasGps = events.any { it.isGpsFix() }
        val heartRateTimeline = events
            .mapNotNull { event -> event.heartRateBpm?.takeIf { it in 1..300 }?.let { event.timeEpochMs to it } }
            .sortedBy { it.first }

        // Cadence is only meaningful from the wrist step counter on foot; a bike's pedal cadence
        // has nothing to do with steps, so cycling leaves it out rather than writing nonsense.
        val cadenceTimeline = if (session.activityType != WorkoutActivityType.BIKE) {
            CadenceTimeline(events)
        } else {
            null
        }
        var maxCadence: Short? = null

        // Every event feeds the accumulator (pause boundaries reset the distance segment, step
        // events carry the running total); only the selected ones become records.
        val tempAcc = WorkoutMetricsAccumulator()

        for (event in events) {
            tempAcc.add(event)
            val writeRecord = if (hasGps) event.isGpsFix() else event.heartRateBpm != null || event.steps != null
            if (!writeRecord) continue
            val heartRate = if (hasGps) {
                WorkoutExportWriter.nearestHeartRate(heartRateTimeline, event.timeEpochMs)
            } else {
                event.heartRateBpm?.takeIf { it in 1..300 }
            }
            val cadence = cadenceTimeline?.stridesPerMinuteAt(event.timeEpochMs)
            if (cadence != null && cadence > (maxCadence ?: 0)) maxCadence = cadence
            val record = RecordMesg().apply {
                timestamp = DateTime(Date(event.timeEpochMs))
                heartRate?.let { this.heartRate = it.toShort() }
                cadence?.let { this.cadence = it }
                if (event.latitude != null && event.longitude != null) {
                    positionLat = (event.latitude / 180.0 * 2147483648.0).toInt()
                    positionLong = (event.longitude / 180.0 * 2147483648.0).toInt()
                }
                event.altitudeMeters?.let {
                    altitude = it.toFloat()
                }
                if (tempAcc.distanceMeters > 0) {
                    distance = tempAcc.distanceMeters.toFloat()
                }
            }
            encoder.write(record)
        }

        val totalDistanceMeters = tempAcc.distanceMeters.toFloat()
        // FIT has no step total, only strides (one per two steps); FitPub's FitParser reads
        // `session.totalStrides` and stores `totalSteps = strides * 2`.
        val totalStrides = WorkoutFitWriter.stridesFor(tempAcc.steps)
        // FIT cadence is strides (one-leg RPM) per minute; FitParser doubles it to steps per
        // minute for foot sports. Averaged over moving time so pauses do not drag it down.
        val avgCadence = cadenceTimeline?.let { averageCadence(totalStrides, movingSec) }

        // 3. Lap Message
        val lapMesg = LapMesg().apply {
            timestamp = DateTime(Date(stoppedAtEpochMs))
            startTime = DateTime(Date(session.startedAtEpochMs))
            totalElapsedTime = elapsedSec
            totalTimerTime = movingSec
            totalDistance = totalDistanceMeters
            sport = fitSport
            subSport = SubSport.GENERIC
            avgHr?.let { avgHeartRate = it }
            maxHr?.let { maxHeartRate = it }
            totalStrides?.let { this.totalStrides = it }
            avgCadence?.let { this.avgCadence = it }
            maxCadence?.let { this.maxCadence = it }
        }
        encoder.write(lapMesg)

        // 4. Session Message
        val sessionMesg = SessionMesg().apply {
            timestamp = DateTime(Date(stoppedAtEpochMs))
            startTime = DateTime(Date(session.startedAtEpochMs))
            totalElapsedTime = elapsedSec
            totalTimerTime = movingSec
            totalDistance = totalDistanceMeters
            sport = fitSport
            subSport = SubSport.GENERIC
            firstLapIndex = 0
            numLaps = 1
            avgHr?.let { avgHeartRate = it }
            maxHr?.let { maxHeartRate = it }
            totalStrides?.let { this.totalStrides = it }
            avgCadence?.let { this.avgCadence = it }
            maxCadence?.let { this.maxCadence = it }
            sportProfileName = title?.takeIf { it.isNotBlank() } ?: session.activityType.label
        }
        encoder.write(sessionMesg)

        encoder.close()
        return fitFile
    }

    /** A fix the GPX export would also keep as a trackpoint. */
    private fun WorkoutTrackEvent.isGpsFix(): Boolean =
        latitude != null && longitude != null && accuracyMeters != null

    /** Strides for [steps] (rounded to the nearest stride), or null when no steps were counted. */
    internal fun stridesFor(steps: Int): Long? = steps.takeIf { it > 0 }?.let { (it + 1) / 2L }

    /** Session average in strides per minute, or null without strides or moving time. */
    internal fun averageCadence(totalStrides: Long?, movingSec: Float): Short? {
        if (totalStrides == null || movingSec < MIN_CADENCE_WINDOW_SEC) return null
        return (totalStrides * 60.0 / movingSec).roundToInt().takeIf { it in 1..MAX_CADENCE_SPM }?.toShort()
    }

    internal const val PRODUCT_NAME = "FP Wear"

    internal const val CADENCE_WINDOW_MS = 20_000L
    private const val MIN_CADENCE_WINDOW_SEC = 5f
    /** FIT cadence is a uint8; anything above is sensor garbage, not a human stride rate. */
    private const val MAX_CADENCE_SPM = 254

    /**
     * Instantaneous cadence from the cumulative step totals: strides per minute over the last
     * [CADENCE_WINDOW_MS], never reaching back across a pause/resume boundary (the sensors are off
     * while paused, so a window spanning one would read low).
     */
    internal class CadenceTimeline(events: List<WorkoutTrackEvent>) {
        private val steps: List<Pair<Long, Int>>
        private val boundaries: List<Long> = events.filter { it.boundary != null }.map { it.timeEpochMs }.sorted()

        init {
            var runningMax = 0
            steps = events
                .filter { it.steps != null }
                .sortedBy { it.timeEpochMs }
                .map { event ->
                    runningMax = maxOf(runningMax, event.steps!!)
                    event.timeEpochMs to runningMax
                }
        }

        fun stridesPerMinuteAt(timeEpochMs: Long): Short? {
            if (steps.isEmpty()) return null
            val lastBoundary = boundaries.lastOrNull { it <= timeEpochMs }
            val windowStart = maxOf(timeEpochMs - CADENCE_WINDOW_MS, lastBoundary ?: Long.MIN_VALUE, steps.first().first)
            val windowMs = timeEpochMs - windowStart
            if (windowMs < MIN_CADENCE_WINDOW_SEC * 1000) return null
            val delta = stepsAt(timeEpochMs) - stepsAt(windowStart)
            val stridesPerMinute = (delta / 2.0 * 60_000.0 / windowMs).roundToInt()
            return stridesPerMinute.coerceIn(0, MAX_CADENCE_SPM).toShort()
        }

        private fun stepsAt(timeEpochMs: Long): Int = steps.lastOrNull { it.first <= timeEpochMs }?.second ?: 0
    }
}

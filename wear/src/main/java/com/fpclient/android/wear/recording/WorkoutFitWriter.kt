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

object WorkoutFitWriter {

    fun write(
        directory: JavaFile,
        session: WorkoutSessionSnapshot,
        events: List<WorkoutTrackEvent>,
        stoppedAtEpochMs: Long,
        title: String = "${session.activityType.label} workout",
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

        // Filter events that carry HR, GPS, or steps
        val recordEvents = events.filter {
            it.heartRateBpm != null || (it.latitude != null && it.longitude != null) || it.steps != null
        }

        val tempAcc = WorkoutMetricsAccumulator()

        for (event in recordEvents) {
            tempAcc.add(event)
            val record = RecordMesg().apply {
                timestamp = DateTime(Date(event.timeEpochMs))
                event.heartRateBpm?.takeIf { it in 1..300 }?.let {
                    heartRate = it.toShort()
                }
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
            sportProfileName = title
        }
        encoder.write(sessionMesg)

        encoder.close()
        return fitFile
    }
}

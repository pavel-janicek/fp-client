package com.fpclient.android.wear.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class WorkoutStatus { IDLE, RECORDING, PAUSED, STOPPED }

@Serializable
data class WorkoutSessionSnapshot(
    val status: WorkoutStatus,
    val startedAtEpochMs: Long,
    val accumulatedMovingMs: Long,
    val resumedAtEpochMs: Long?,
) {
    fun elapsedMsAt(nowEpochMs: Long): Long = (nowEpochMs - startedAtEpochMs).coerceAtLeast(0L)

    fun movingMsAt(nowEpochMs: Long): Long = accumulatedMovingMs +
        (resumedAtEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0L) } ?: 0L)
}

@Serializable
data class WorkoutTrackEvent(
    val timeEpochMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Double? = null,
    val heartRateBpm: Int? = null,
    val steps: Int? = null,
    val rawStepCounter: Float? = null,
    val boundary: String? = null,
)

data class WorkoutSensorAvailability(
    val gps: Boolean = false,
    val healthServices: Boolean = false,
    val heartRate: Boolean = false,
    val steps: Boolean = false,
)

data class WorkoutRecordingSnapshot(
    val session: WorkoutSessionSnapshot? = null,
    val elapsedMs: Long = 0L,
    val movingMs: Long = 0L,
    val heartRateBpm: Int? = null,
    val distanceMeters: Double = 0.0,
    val paceSecondsPerKm: Long? = null,
    val steps: Int = 0,
    val availability: WorkoutSensorAvailability = WorkoutSensorAvailability(),
    val errorMessage: String? = null,
) {
    val status: WorkoutStatus get() = session?.status ?: WorkoutStatus.IDLE
}

object WorkoutSessionTransitions {
    fun start(nowEpochMs: Long) = WorkoutSessionSnapshot(
        WorkoutStatus.RECORDING,
        nowEpochMs,
        accumulatedMovingMs = 0L,
        resumedAtEpochMs = nowEpochMs,
    )

    fun pause(session: WorkoutSessionSnapshot, nowEpochMs: Long): WorkoutSessionSnapshot? =
        session.takeIf { it.status == WorkoutStatus.RECORDING }?.copy(
            status = WorkoutStatus.PAUSED,
            accumulatedMovingMs = session.movingMsAt(nowEpochMs),
            resumedAtEpochMs = null,
        )

    fun resume(session: WorkoutSessionSnapshot, nowEpochMs: Long): WorkoutSessionSnapshot? =
        session.takeIf { it.status == WorkoutStatus.PAUSED }?.copy(
            status = WorkoutStatus.RECORDING,
            resumedAtEpochMs = nowEpochMs,
        )

    fun stop(session: WorkoutSessionSnapshot, nowEpochMs: Long): WorkoutSessionSnapshot = session.copy(
        status = WorkoutStatus.STOPPED,
        accumulatedMovingMs = session.movingMsAt(nowEpochMs),
        resumedAtEpochMs = null,
    )
}

object WorkoutRecordingBus {
    private val mutableState = MutableStateFlow(WorkoutRecordingSnapshot())
    val state: StateFlow<WorkoutRecordingSnapshot> = mutableState

    fun publish(snapshot: WorkoutRecordingSnapshot) {
        mutableState.value = snapshot
    }
}

object WorkoutMath {
    const val MAX_ACCURACY_METERS = 20.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MIN_PACE_DISTANCE_METERS = 10.0

    fun isAcceptableAccuracy(accuracyMeters: Double): Boolean =
        accuracyMeters in 0.0..MAX_ACCURACY_METERS

    fun distanceMeters(latitude1: Double, longitude1: Double, latitude2: Double, longitude2: Double): Double {
        val deltaLatitude = Math.toRadians(latitude2 - latitude1)
        val deltaLongitude = Math.toRadians(longitude2 - longitude1)
        val haversine = kotlin.math.sin(deltaLatitude / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(latitude1)) * kotlin.math.cos(Math.toRadians(latitude2)) *
            kotlin.math.sin(deltaLongitude / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * kotlin.math.asin(kotlin.math.sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    fun paceSecondsPerKm(movingMs: Long, distanceMeters: Double): Long? {
        if (movingMs <= 0L || distanceMeters < MIN_PACE_DISTANCE_METERS) return null
        return (movingMs / 1000.0 / (distanceMeters / 1000.0)).toLong().coerceAtLeast(0L)
    }
}

class WorkoutMetricsAccumulator {
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    var heartRateBpm: Int? = null
        private set
    var distanceMeters: Double = 0.0
        private set
    var steps: Int = 0
        private set
    var rawStepCounter: Float? = null
        private set

    fun add(event: WorkoutTrackEvent) {
        event.heartRateBpm?.takeIf { it in 1..300 }?.let { heartRateBpm = it }
        event.steps?.let { steps = maxOf(steps, it) }
        event.rawStepCounter?.let { rawStepCounter = it }

        if (event.boundary != null) {
            clearSegment()
        }

        val latitude = event.latitude
        val longitude = event.longitude
        val accuracy = event.accuracyMeters
        if (latitude == null || longitude == null || accuracy == null ||
            !WorkoutMath.isAcceptableAccuracy(accuracy) || latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) return

        val previousLatitude = lastLatitude
        val previousLongitude = lastLongitude
        if (previousLatitude != null && previousLongitude != null) {
            distanceMeters += WorkoutMath.distanceMeters(previousLatitude, previousLongitude, latitude, longitude)
        }
        lastLatitude = latitude
        lastLongitude = longitude
    }

    fun clearSegment() {
        lastLatitude = null
        lastLongitude = null
    }
}
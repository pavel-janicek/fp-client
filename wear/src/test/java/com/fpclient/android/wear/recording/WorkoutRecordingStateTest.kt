package com.fpclient.android.wear.recording

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecordingStateTest {
    @Test
    fun heartRateZonesUseGenericMaximumAndRejectMissingReadings() {
        assertNull(WorkoutHeartRateZone.fromBpm(null))
        assertEquals(WorkoutHeartRateZone.EASY, WorkoutHeartRateZone.fromBpm(113))
        assertEquals(WorkoutHeartRateZone.AEROBIC, WorkoutHeartRateZone.fromBpm(114))
        assertEquals(WorkoutHeartRateZone.TEMPO, WorkoutHeartRateZone.fromBpm(133))
        assertEquals(WorkoutHeartRateZone.THRESHOLD, WorkoutHeartRateZone.fromBpm(152))
        assertEquals(WorkoutHeartRateZone.PEAK, WorkoutHeartRateZone.fromBpm(171))
    }

    @Test
    fun stateMachineMovesIdleRecordingPausedStopped() {
        val initial = WorkoutRecordingSnapshot()
        val started = WorkoutSessionTransitions.start(1_000L, WorkoutActivityType.BIKE)
        val paused = WorkoutSessionTransitions.pause(started, 6_000L)!!
        val resumed = WorkoutSessionTransitions.resume(paused, 10_000L)!!
        val stopped = WorkoutSessionTransitions.stop(resumed, 13_000L)

        assertEquals(WorkoutStatus.IDLE, initial.status)
        assertEquals(WorkoutStatus.RECORDING, started.status)
        assertEquals(WorkoutActivityType.BIKE, started.activityType)
        assertEquals(5_000L, paused.accumulatedMovingMs)
        assertEquals(8_000L, stopped.accumulatedMovingMs)
        assertEquals(WorkoutStatus.STOPPED, stopped.status)
        assertNull(WorkoutSessionTransitions.pause(paused, 11_000L))
    }

    @Test
    fun sessionClockExcludesPausedTimeFromMovingDuration() {
        val session = WorkoutSessionSnapshot(WorkoutStatus.PAUSED, 1_000L, 5_000L, null)

        assertEquals(15_000L, session.elapsedMsAt(16_000L))
        assertEquals(5_000L, session.movingMsAt(16_000L))
    }

    @Test
    fun metricsAccumulateDistanceHeartRateAndStepsAndFilterBadFixes() {
        val metrics = WorkoutMetricsAccumulator()
        metrics.add(WorkoutTrackEvent(1L, 50.0, 14.0, accuracyMeters = 5.0, heartRateBpm = 120, steps = 10))
        metrics.add(WorkoutTrackEvent(2L, 50.0001, 14.0, accuracyMeters = 5.0, heartRateBpm = 124, steps = 12))
        metrics.add(WorkoutTrackEvent(3L, 51.0, 14.0, accuracyMeters = 500.0, heartRateBpm = 0, steps = 12))

        assertTrue(metrics.distanceMeters in 10.0..12.0)
        assertEquals(124, metrics.heartRateBpm)
        assertEquals(12, metrics.steps)
        assertFalse(WorkoutMath.isAcceptableAccuracy(500.0))
        assertEquals(363L, WorkoutMath.paceSecondsPerKm(4_000L, 11.0))
        assertNull(WorkoutMath.paceSecondsPerKm(4_000L, 2.0))
    }

    @Test
    fun pauseBoundaryPreventsDistanceAcrossTheGap() {
        val metrics = WorkoutMetricsAccumulator()
        metrics.add(WorkoutTrackEvent(1L, 50.0, 14.0, accuracyMeters = 5.0))
        metrics.add(WorkoutTrackEvent(2L, boundary = "PAUSE"))
        metrics.add(WorkoutTrackEvent(3L, 52.0, 14.0, accuracyMeters = 5.0))

        assertEquals(0.0, metrics.distanceMeters, 0.0)
    }

    @Test
    fun jsonlTrackStoreFlushesAndReplaysEvents() {
        val directory = Files.createTempDirectory("watch-track-test").toFile()
        val store = WorkoutTrackStore(directory)
        val event = WorkoutTrackEvent(1_000L, 50.0, 14.0, accuracyMeters = 4.0, steps = 7)

        store.append(1_000L, event)
        store.close()

        assertEquals(listOf(event), store.readEvents(1_000L))
        assertTrue(store.fileFor(1_000L).readText().endsWith("\n"))
        directory.deleteRecursively()
    }

    @Test
    fun snapshotJsonRoundTripsAndRejectsCorruptData() {
        val snapshot = WorkoutSessionSnapshot(
            WorkoutStatus.RECORDING,
            10L,
            2L,
            8L,
            WorkoutActivityType.HIKE,
        )

        assertEquals(snapshot, WorkoutSessionStore.decode(WorkoutSessionStore.encode(snapshot)))
        assertNull(WorkoutSessionStore.decode("not-json"))
    }
}
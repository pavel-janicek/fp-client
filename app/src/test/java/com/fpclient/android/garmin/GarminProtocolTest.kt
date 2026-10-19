package com.fpclient.android.garmin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the phone side of the Garmin message contract, mirroring `garmin/source/ProtocolTest.mc`
 * — a changed string on either side breaks one of the two tests before it breaks a paired watch.
 */
class GarminProtocolTest {

    @Test
    fun wireStringsMatchWatch() {
        assertEquals("acb5d45a68f54311b0223006a814250f", GarminProtocol.APP_ID)
        assertEquals(1, GarminProtocol.VERSION)
        assertEquals("t", GarminProtocol.KEY_TYPE)
        assertEquals("v", GarminProtocol.KEY_VERSION)
        assertEquals("id", GarminProtocol.KEY_ID)
        assertEquals("created", GarminProtocol.KEY_CREATED)
        assertEquals("label", GarminProtocol.KEY_LABEL)
        assertEquals("sport", GarminProtocol.KEY_SPORT)
        assertEquals("duration", GarminProtocol.KEY_DURATION)
        assertEquals("distance", GarminProtocol.KEY_DISTANCE)
        assertEquals("chunks", GarminProtocol.KEY_CHUNKS)
        assertEquals("index", GarminProtocol.KEY_INDEX)
        assertEquals("data", GarminProtocol.KEY_DATA)
        assertEquals("chunk", GarminProtocol.TYPE_CHUNK)
        assertEquals("need", GarminProtocol.TYPE_NEED)
        assertEquals(6, GarminProtocol.SAMPLE_FIELDS)
        assertEquals("workout", GarminProtocol.TYPE_WORKOUT)
        assertEquals("ack", GarminProtocol.TYPE_ACK)
        assertEquals("pull", GarminProtocol.TYPE_PULL)
    }

    @Test
    fun ackAndPullCarryTypeAndVersion() {
        assertEquals(mapOf("t" to "ack", "v" to 1, "id" to "42-7"), GarminProtocol.ack("42-7"))
        assertEquals(mapOf("t" to "pull", "v" to 1), GarminProtocol.pull())
        assertEquals(mapOf("t" to "need", "v" to 1, "id" to "42-7", "index" to 3), GarminProtocol.need("42-7", 3))
    }

    @Test
    fun parsesWorkoutAsTheSdkDeliversIt() {
        // The SDK turns a Monkey C Dictionary into a HashMap and a Number into an Integer.
        val message = hashMapOf<Any, Any>("t" to "workout", "v" to 1, "id" to "42-7", "created" to 1_700_000_000, "label" to "Run")
        assertEquals(GarminProtocol.Workout("42-7", 1_700_000_000L, "Run"), GarminProtocol.parseWorkout(message))
    }

    @Test
    fun parsesWorkoutSummary() {
        val message = hashMapOf<Any, Any>(
            "t" to "workout", "v" to 1, "id" to "1700000000-7", "created" to 1_700_000_000, "label" to "Run",
            "sport" to "RUN", "duration" to 1880, "distance" to 5230, "chunks" to 32,
        )
        assertEquals(
            GarminProtocol.Workout("1700000000-7", 1_700_000_000L, "Run", "RUN", 1880L, 5230L, 32),
            GarminProtocol.parseWorkout(message),
        )
    }

    @Test
    fun parsesChunkWithNullsIntoSamples() {
        // The SDK delivers a Monkey C Array as a List; missing values stay null.
        val message = hashMapOf<Any, Any>(
            "t" to "chunk", "v" to 1, "id" to "42-7", "index" to 2,
            "data" to arrayListOf(0, 50_123_456, 14_123_456, 2345, 140, 12, 1, null, null, null, 141, 15),
        )
        val chunk = GarminProtocol.parseChunk(message)!!
        assertEquals("42-7", chunk.id)
        assertEquals(2, chunk.index)
        assertEquals(
            listOf(
                GarminProtocol.Sample(0, 50_123_456, 14_123_456, 2345, 140, 12),
                GarminProtocol.Sample(1, null, null, null, 141, 15),
            ),
            GarminProtocol.samples(chunk.values),
        )
    }

    @Test
    fun rejectsChunksThatAreNotWholeSamples() {
        assertNull(GarminProtocol.parseChunk(mapOf("t" to "chunk", "v" to 1, "id" to "1", "index" to 0, "data" to listOf(1, 2, 3))))
        assertNull(GarminProtocol.parseChunk(mapOf("t" to "chunk", "v" to 1, "id" to "1", "index" to -1, "data" to emptyList<Int>())))
        assertNull(GarminProtocol.parseChunk(mapOf("t" to "chunk", "v" to 1, "id" to "1", "index" to 0, "data" to listOf("a", 1, 2, 3, 4, 5))))
    }

    @Test
    fun entryIsCompleteOnlyWithEveryChunk() {
        val entry = GarminInboxEntry("1", "Run", 0, "", 0, expectedChunks = 3, receivedChunks = listOf(0, 2))
        assertEquals(listOf(1), entry.missingChunks)
        assertEquals(false, entry.isComplete)
        assertEquals(true, entry.copy(receivedChunks = listOf(0, 1, 2)).isComplete)
        // A workout without samples (watch storage full) is complete with its summary alone.
        assertEquals(true, entry.copy(expectedChunks = 0, receivedChunks = emptyList()).isComplete)
    }

    @Test
    fun summaryTextShowsOnlyWhatTheWatchReported() {
        val entry = GarminInboxEntry("1", "Run", 0, "fēnix 8", 0, "RUN", 3723, 5230)
        assertEquals("Run · 5.23 km · 1:02:03 from fēnix 8", GarminNotifications.summaryText(entry))
        assertEquals("Walk · 31:20", GarminNotifications.summaryText(GarminInboxEntry("2", "Walk", 0, "", 0, durationSeconds = 1880, distanceMeters = 0)))
        assertEquals("Workout", GarminNotifications.summaryText(GarminInboxEntry("3", "", 0, "", 0)))
    }

    @Test
    fun rejectsOtherTypesVersionsAndJunk() {
        assertNull(GarminProtocol.parseWorkout(mapOf("t" to "ack", "v" to 1, "id" to "1")))
        assertNull(GarminProtocol.parseWorkout(mapOf("t" to "workout", "v" to 2, "id" to "1", "created" to 1)))
        assertNull(GarminProtocol.parseWorkout(mapOf("t" to "workout", "v" to 1, "id" to "", "created" to 1)))
        assertNull(GarminProtocol.parseWorkout(mapOf("t" to "workout", "v" to 1, "id" to "1")))
        assertNull(GarminProtocol.parseWorkout("workout"))
        assertNull(GarminProtocol.parseWorkout(null))
    }
}

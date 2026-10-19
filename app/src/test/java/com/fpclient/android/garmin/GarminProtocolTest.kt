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
        assertEquals("workout", GarminProtocol.TYPE_WORKOUT)
        assertEquals("ack", GarminProtocol.TYPE_ACK)
        assertEquals("pull", GarminProtocol.TYPE_PULL)
    }

    @Test
    fun ackAndPullCarryTypeAndVersion() {
        assertEquals(mapOf("t" to "ack", "v" to 1, "id" to "42-7"), GarminProtocol.ack("42-7"))
        assertEquals(mapOf("t" to "pull", "v" to 1), GarminProtocol.pull())
    }

    @Test
    fun parsesWorkoutAsTheSdkDeliversIt() {
        // The SDK turns a Monkey C Dictionary into a HashMap and a Number into an Integer.
        val message = hashMapOf<Any, Any>("t" to "workout", "v" to 1, "id" to "42-7", "created" to 1_700_000_000, "label" to "Run")
        assertEquals(GarminProtocol.Workout("42-7", 1_700_000_000L, "Run"), GarminProtocol.parseWorkout(message))
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

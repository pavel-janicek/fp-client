package com.fpclient.android.garmin

/**
 * Phone ↔ Garmin watch message contract (Connect IQ Mobile SDK, Iteration 10b). Mirrored on the
 * watch in `garmin/source/Protocol.mc`; the wire strings are pinned by unit tests on both sides
 * (`GarminProtocolTest` here, `ProtocolTest.mc` there), so keep them synchronized.
 *
 * Messages are Monkey C dictionaries, which the SDK hands over as `Map`s:
 * - watch → phone `workout` {id, created, label, sport, duration, distance}: a finished workout's
 *   summary, resent by the watch until acked (10c; the samples follow with 10d);
 * - phone → watch `ack` {id}: stored, the watch may drop it;
 * - phone → watch `pull`: resend everything still queued.
 */
internal object GarminProtocol {
    /** The Connect IQ app id from `garmin/manifest.xml`; the SDK addresses the watch app by it. */
    const val APP_ID = "acb5d45a68f54311b0223006a814250f"

    const val VERSION = 1

    const val KEY_TYPE = "t"
    const val KEY_VERSION = "v"
    const val KEY_ID = "id"
    const val KEY_CREATED = "created"
    const val KEY_LABEL = "label"
    const val KEY_SPORT = "sport"
    const val KEY_DURATION = "duration"
    const val KEY_DISTANCE = "distance"

    const val TYPE_WORKOUT = "workout"
    const val TYPE_ACK = "ack"
    const val TYPE_PULL = "pull"

    /**
     * A finished workout as announced by the watch. [sport] is the activity key shared with the
     * Wear OS app (RUN, WALK, HIKE, BIKE, OTHER); the summary fields are null when absent (the
     * 10b test workout had none), never guessed.
     */
    data class Workout(
        val id: String,
        val createdEpochSeconds: Long,
        val label: String,
        val sport: String? = null,
        val durationSeconds: Long? = null,
        val distanceMeters: Long? = null,
    )

    fun ack(id: String): Map<String, Any> = mapOf(KEY_TYPE to TYPE_ACK, KEY_VERSION to VERSION, KEY_ID to id)

    fun pull(): Map<String, Any> = mapOf(KEY_TYPE to TYPE_PULL, KEY_VERSION to VERSION)

    /** The workout in [message], or null for anything else (other types, other versions, junk). */
    fun parseWorkout(message: Any?): Workout? {
        val map = message as? Map<*, *> ?: return null
        if (map[KEY_TYPE] != TYPE_WORKOUT) return null
        if ((map[KEY_VERSION] as? Number)?.toInt() != VERSION) return null
        val id = (map[KEY_ID] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val created = (map[KEY_CREATED] as? Number)?.toLong() ?: return null
        val label = map[KEY_LABEL] as? String ?: ""
        return Workout(
            id = id,
            createdEpochSeconds = created,
            label = label,
            sport = map[KEY_SPORT] as? String,
            durationSeconds = (map[KEY_DURATION] as? Number)?.toLong(),
            distanceMeters = (map[KEY_DISTANCE] as? Number)?.toLong(),
        )
    }
}

package com.fpclient.android.garmin

/**
 * Phone ↔ Garmin watch message contract (Connect IQ Mobile SDK, Iteration 10). Mirrored on the
 * watch in `garmin/source/Protocol.mc`; the wire strings are pinned by unit tests on both sides
 * (`GarminProtocolTest` here, `ProtocolTest.mc` there), so keep them synchronized.
 *
 * Messages are Monkey C dictionaries, which the SDK hands over as `Map`s:
 * - watch → phone `workout` {id, created, label, sport, duration, distance, chunks}: a finished
 *   workout's summary, resent by the watch until acked;
 * - watch → phone `chunk` {id, index, data}: one chunk of samples (see [Sample]), pushed after
 *   Save and on every `need`;
 * - phone → watch `need` {id, index}: send that chunk again;
 * - phone → watch `ack` {id}: the phone holds the summary and every chunk, the watch may drop it;
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
    const val KEY_CHUNKS = "chunks"
    const val KEY_INDEX = "index"
    const val KEY_DATA = "data"

    const val TYPE_WORKOUT = "workout"
    const val TYPE_ACK = "ack"
    const val TYPE_PULL = "pull"
    const val TYPE_CHUNK = "chunk"
    const val TYPE_NEED = "need"

    /** Values per sample in a chunk, see [Sample]. */
    const val SAMPLE_FIELDS = 6

    /**
     * A finished workout as announced by the watch. [sport] is the activity key shared with the
     * Wear OS app (RUN, WALK, HIKE, BIKE, OTHER); the summary fields are null when absent (the
     * 10b test workout had none), never guessed. [chunks] is how many sample chunks follow.
     */
    data class Workout(
        val id: String,
        val createdEpochSeconds: Long,
        val label: String,
        val sport: String? = null,
        val durationSeconds: Long? = null,
        val distanceMeters: Long? = null,
        val chunks: Int = 0,
    )

    data class Chunk(val id: String, val index: Int, val values: List<Long?>)

    /**
     * One per-second sample as the watch stores it (`SampleBuffer.mc`): integers only, null when
     * the watch had no value. [steps] counts from the workout start.
     */
    data class Sample(
        val secondsSinceStart: Long,
        val latE6: Long?,
        val lonE6: Long?,
        val altitudeDm: Long?,
        val heartRate: Long?,
        val steps: Long?,
    )

    fun ack(id: String): Map<String, Any> = mapOf(KEY_TYPE to TYPE_ACK, KEY_VERSION to VERSION, KEY_ID to id)

    fun pull(): Map<String, Any> = mapOf(KEY_TYPE to TYPE_PULL, KEY_VERSION to VERSION)

    fun need(id: String, index: Int): Map<String, Any> =
        mapOf(KEY_TYPE to TYPE_NEED, KEY_VERSION to VERSION, KEY_ID to id, KEY_INDEX to index)

    /** The workout in [message], or null for anything else (other types, other versions, junk). */
    fun parseWorkout(message: Any?): Workout? {
        val map = v1Message(message, TYPE_WORKOUT) ?: return null
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
            chunks = (map[KEY_CHUNKS] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
        )
    }

    /** The chunk in [message]; null for anything else, or when its data is not whole samples. */
    fun parseChunk(message: Any?): Chunk? {
        val map = v1Message(message, TYPE_CHUNK) ?: return null
        val id = (map[KEY_ID] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val index = (map[KEY_INDEX] as? Number)?.toInt()?.takeIf { it >= 0 } ?: return null
        val data = map[KEY_DATA] as? List<*> ?: return null
        if (data.size % SAMPLE_FIELDS != 0) return null
        val values = data.map { value ->
            when (value) {
                null -> null
                is Number -> value.toLong()
                else -> return null
            }
        }
        return Chunk(id, index, values)
    }

    /** Splits flat chunk values into samples, dropping any without a time. */
    fun samples(values: List<Long?>): List<Sample> =
        values.chunked(SAMPLE_FIELDS).filter { it.size == SAMPLE_FIELDS && it[0] != null }.map {
            Sample(it[0]!!, it[1], it[2], it[3], it[4], it[5])
        }

    private fun v1Message(message: Any?, type: String): Map<*, *>? {
        val map = message as? Map<*, *> ?: return null
        if (map[KEY_TYPE] != type) return null
        if ((map[KEY_VERSION] as? Number)?.toInt() != VERSION) return null
        return map
    }
}

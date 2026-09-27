package com.fpclient.android.notifications

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One decoded line of the ntfy subscribe stream (`GET {server}/{topic}/json`, Iteration 8j).
 *
 * ntfy's JSON subscribe API is newline-delimited: every server event is one complete JSON
 * object on its own line. FP Client only ever needs four of them — the two keep-alive-ish
 * bookends, the message itself, and the "you fell behind, re-poll" instruction.
 */
sealed interface NtfyEvent {

    /**
     * The stream was accepted. ntfy also puts an `id` on it, but that id belongs to the
     * *open* event, not to a notification, so it is deliberately not exposed here: the
     * replay cursor is only ever advanced by real [Message] ids.
     */
    data class Open(val topic: String?) : NtfyEvent

    /** ntfy's periodic heartbeat; its arrival is what proves the connection is alive. */
    data object Keepalive : NtfyEvent

    /**
     * A published notification. [message] is the relay's opaque payload (standard base64 of
     * the untouched RFC 8291 aes128gcm blob — 8j forwards ciphertext, so the relay never
     * needed the key and never had it), [tags] is whatever the publisher put in ntfy's
     * `tags` array (`["fitpub", …]`, and FP Client reads a `fitpub-` entry when present),
     * and [click] is the instance origin the relay points at.
     */
    data class Message(
        val id: String,
        val topic: String?,
        val message: String,
        val title: String? = null,
        val tags: List<String> = emptyList(),
        val click: String? = null,
    ) : NtfyEvent

    /**
     * The server is telling us the stream fell too far behind and to re-fetch with
     * `?poll=1&since=…`. The app answers by reconnecting with a gap-replay poll instead of
     * sitting on a stream that will never catch up.
     */
    data object PollRequest : NtfyEvent
}


/**
 * Android-free decoder + URL builder for the ntfy subscribe API (Iteration 8j).
 *
 * Deliberately no framework types: the parse, the topic validation and the URL shapes are
 * the parts most likely to break silently, so they are pinned by JVM unit tests. Unknown
 * `event` values and unknown JSON keys are tolerated exactly like everywhere else in the app
 * (ntfy adds event types between releases), and a line that is not a decodable event is
 * simply dropped — one bad line must never kill a long-lived stream.
 */
object NtfyMessages {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The literal tags entry every FP relay message carries, so a ntfy client can filter. */
    const val TAG_APP = "fitpub"

    /** Payload tags the relay may publish as a second entry look like this. */
    private const val TAG_PREFIX = "fitpub-"

    /** True for a payload collapse tag such as `fitpub-activity_liked`. */
    fun isPayloadTag(tag: String?): Boolean = tag?.startsWith(TAG_PREFIX) == true

    /**
     * Decodes one NDJSON line, or returns `null` when the line is not an event this app
     * cares about: blank, not JSON, not an object, an unknown `event`, or a `message` without
     * the two fields FP Client cannot work without (`id`, `message`).
     */
    fun parse(line: String): NtfyEvent? {
        if (line.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
        val topic = obj.string("topic")
        return when (obj.string("event")) {
            "open" -> NtfyEvent.Open(topic)
            "keepalive" -> NtfyEvent.Keepalive
            "poll_request" -> NtfyEvent.PollRequest
            "message" -> {
                val id = obj.string("id")
                val body = obj.string("message")
                if (id.isNullOrBlank() || body.isNullOrBlank()) null
                else NtfyEvent.Message(
                    id = id,
                    topic = topic,
                    message = body,
                    title = obj.string("title"),
                    tags = obj.stringList("tags"),
                    click = obj.string("click"),
                )
            }
            // Unknown / future event types are ignored, not fatal.
            else -> null
        }
    }

    /**
     * `{base}/{topic}/json` — the live stream. `null` when the base is not a usable http(s)
     * URL or the topic breaks ntfy's own `[-_a-z0-9]{1,64}` rule, so an invalid operator
     * setting can never turn into a request to some other host.
     */
    fun subscribeUrl(base: String, topic: String): String? = url(base, topic) { it }

    /**
     * `{base}/{topic}/json?poll=1&since={lastMessageId}` — the gap replay used after a
     * dropped connection (and whenever ntfy sends `poll_request`). ntfy caches messages for
     * 12 h by default, so a gap older than that is simply gone; the 15-minute
     * `fitpub_push_mailbox_check` remains the real backstop.
     *
     * A blank [since] yields a plain `?poll=1` (everything still cached), which is the right
     * answer on a first-ever connect: no cursor means no gap to skip.
     */
    fun pollUrl(base: String, topic: String, since: String?): String? =
        url(base, topic) { builder ->
            builder.addQueryParameter("poll", "1")
            if (!since.isNullOrBlank()) builder.addQueryParameter("since", since)
            builder
        }

    /** Shared base/topic validation; [finish] adds the stream- or poll-specific query. */
    private fun url(base: String, topic: String, finish: (HttpUrl.Builder) -> HttpUrl.Builder): String? {
        // The topic goes into the path verbatim — reject anything ntfy itself would reject
        // *before* building a URL, so a crafted topic cannot escape into another path.
        if (!PushSubscriptionStore.isValidNtfyTopic(topic)) return null
        val httpBase = base.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        if (httpBase.scheme != "http" && httpBase.scheme != "https") return null
        return finish(httpBase.newBuilder().addPathSegment(topic).addPathSegment("json")).build()
            .toString()
    }

    /** A string field, or null when absent / null / a nested object. */
    private fun JsonObject.string(key: String): String? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    /** A string array field, tolerating ntfy sending a comma-joined string instead. */
    private fun JsonObject.stringList(key: String): List<String> {
        val element = this[key] ?: return emptyList()
        return runCatching {
            element.jsonArray.mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrElse { listOfNotNull((element as? JsonPrimitive)?.content) }
    }
}

/**
 * Guards the ntfy replay cursor: an id is accepted at most once, and the newest accepted id
 * is remembered so a reconnect can ask for the gap.
 *
 * Two shapes of duplicate reach the stream in practice: ntfy replays from `since=` on every
 * reconnect (so the boundary message legitimately arrives twice), and a `poll_request`
 * followed by a poll replays the same window. Both are harmless to the user only if the app
 * drops them — otherwise a "someone liked your activity" notification shows up two or three
 * times within seconds.
 *
 * Bounded: ntfy ids are short strings but a long-lived connection sees many of them, so only
 * the most recent [capacity] ids are remembered. That is far more than any replay window
 * needs, and it keeps the memory cost fixed.
 */
class NtfyDeduper(private val capacity: Int = DEFAULT_CAPACITY) {

    private val seen = LinkedHashSet<String>()

    /** The newest id accepted so far — the `since` value for the next reconnect. */
    var lastId: String? = null
        private set

    /** Seeds the cursor from the persisted value without accepting a delivery for it. */
    fun restore(id: String?) {
        if (!id.isNullOrBlank() && lastId == null) lastId = id
    }

    /**
     * True when [id] is new and should be delivered. A blank id is accepted (nothing to
     * dedupe against, and dropping it would silently lose a notification); it just does not
     * advance the cursor.
     */
    fun accept(id: String): Boolean {
        if (id.isBlank()) return true
        if (!seen.add(id)) return false
        val iterator = seen.iterator()
        while (seen.size > capacity && iterator.hasNext()) iterator.next()
        lastId = id
        return true
    }

    private companion object {
        /** Far more than any ntfy replay window can deliver. */
        const val DEFAULT_CAPACITY = 64
    }
}

package com.fpclient.android.notifications

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Iteration 8j — the in-app ntfy receiver's contract, pinned on the JVM.
 *
 * Covers the four things that break silently in production: the NDJSON parse, the
 * URL shapes, the dedupe cursor, and the fact that the bytes ntfy carries are
 * byte-identical to the ones the mailbox path carries — so the fast path can never
 * drift from the 15-minute one. The service's session/account gate is covered in
 * `InstantDeliveryServiceGateTest`.
 */
class NtfyMessagesTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    // ------------------------------------------------------------------ parsing

    @Test
    fun parsesOpen() {
        val event = NtfyMessages.parse(
            """{"id":"AK5Ph","time":1770000000,"event":"open","topic":"fp-abc"}""",
        )
        assertEquals(NtfyEvent.Open("fp-abc"), event)
    }

    @Test
    fun parsesKeepalive() {
        assertEquals(
            NtfyEvent.Keepalive,
            NtfyMessages.parse("""{"id":"x","time":1,"event":"keepalive","topic":"fp-abc"}"""),
        )
    }

    @Test
    fun parsesPollRequest() {
        assertEquals(
            NtfyEvent.PollRequest,
            NtfyMessages.parse("""{"id":"x","time":1,"event":"poll_request","topic":"fp-abc"}"""),
        )
    }

    @Test
    fun parsesMessageWithThePayloadTagAsASecondNtfyTag() {
        val event = NtfyMessages.parse(
            """{"id":"m1","time":1,"event":"message","topic":"fp-abc",""" +
                """"message":"YmxvYg==","title":"","tags":["fitpub","fitpub-activity_liked"],""" +
                """"click":"https://fitpub.example"}""",
        ) as NtfyEvent.Message
        assertEquals("m1", event.id)
        assertEquals("YmxvYg==", event.message)
        assertEquals(listOf("fitpub", "fitpub-activity_liked"), event.tags)
        assertEquals("https://fitpub.example", event.click)
        // FP Client recognises the payload tag and ignores the literal app tag.
        assertTrue(NtfyMessages.isPayloadTag(event.tags[1]))
        assertFalse(NtfyMessages.isPayloadTag(event.tags[0]))
    }

    @Test
    fun ignoresUnknownEventsAndUnknownKeys() {
        // A future ntfy event type, and a message carrying keys this build predates.
        assertNull(NtfyMessages.parse("""{"event":"cleared","topic":"fp-abc"}"""))
        assertNull(NtfyMessages.parse("""{"event":"","topic":"fp-abc"}"""))
        assertNull(NtfyMessages.parse("""{"event":"message"}"""))
        assertNull(NtfyMessages.parse("""{"topic":"fp-abc"}"""))
        val withExtras = NtfyMessages.parse(
            """{"id":"m2","event":"message","message":"x","future_field":{"a":1},"uid":"abc"}""",
        ) as NtfyEvent.Message
        assertEquals("m2", withExtras.id)
    }

    @Test
    fun dropsGarbageWithoutThrowing() {
        listOf("", "   ", "not json", "{", "[]", "\"a string\"", "null").forEach {
            assertNull("must be dropped: $it", NtfyMessages.parse(it))
        }
        // A message event missing the two fields the app cannot work without.
        assertNull(NtfyMessages.parse("""{"event":"message","message":"x"}"""))
        assertNull(NtfyMessages.parse("""{"event":"message","id":"m3"}"""))
    }

    // ------------------------------------------------------------------ the URLs

    @Test
    fun subscribeUrlIsTheNdjsonStream() {
        assertEquals(
            "https://ntfy.example/fp-abc/json",
            NtfyMessages.subscribeUrl("https://ntfy.example", "fp-abc"),
        )
        // A trailing slash and surrounding whitespace are the user's typing, not intent.
        assertEquals(
            "https://ntfy.example/fp-abc/json",
            NtfyMessages.subscribeUrl("  https://ntfy.example/  ", "fp-abc"),
        )
    }

    @Test
    fun pollUrlCarriesTheSinceCursor() {
        assertEquals(
            "https://ntfy.example/fp-abc/json?poll=1&since=abc123",
            NtfyMessages.pollUrl("https://ntfy.example", "fp-abc", "abc123"),
        )
        // No cursor yet (first connect): ask for everything still cached.
        assertEquals(
            "https://ntfy.example/fp-abc/json?poll=1",
            NtfyMessages.pollUrl("https://ntfy.example", "fp-abc", null),
        )
        assertEquals(
            "https://ntfy.example/fp-abc/json?poll=1",
            NtfyMessages.pollUrl("https://ntfy.example", "fp-abc", "  "),
        )
    }

    @Test
    fun rejectsATopicNtfyItselfWouldReject() {
        // ntfy's own rule: 1-64 chars of [-_a-z0-9]. A crafted topic must never
        // escape into another path on another host.
        listOf(
            "", "a".repeat(65), "Topic", "has space", "dot.topic", "a/b", "../etc",
            "fp-abc?poll=1", "fp-abc#frag", "fp%2Fabc", "fp-abc&x=1",
        ).forEach { topic ->
            assertNull("must be rejected: $topic", NtfyMessages.subscribeUrl("https://ntfy.example", topic))
            assertNull("must be rejected: $topic", NtfyMessages.pollUrl("https://ntfy.example", topic, "x"))
        }
        assertNotNull(NtfyMessages.subscribeUrl("https://ntfy.example", "a".repeat(64)))
    }

    @Test
    fun rejectsAnUnusableServerAddress() {
        // A bare host is rejected rather than silently assumed to be https: the
        // Settings field is the operator's, and guessing is how a typo ends up
        // pointing somewhere unexpected.
        listOf("", "   ", "not a url", "ftp://ntfy.example", "ntfy.example").forEach { base ->
            assertNull("must be rejected: $base", NtfyMessages.subscribeUrl(base, "fp-abc"))
        }
    }

    // ------------------------------------------------------------------ the stream

    @Test
    fun consumesAnNdjsonStreamAndSurvivesAMalformedLine() {
        // chunkSize = 1 makes MockWebServer dribble the body out, so the reader is
        // genuinely line-by-line over a stream rather than one convenient blob.
        server.enqueue(
            MockResponse().setChunkedBody(
                listOf(
                    """{"event":"open","topic":"fp-abc"}""",
                    """{"id":"m1","event":"message","message":"AAAA"}""",
                    "{\"id\":\"broken\", \"event\":",  // truncated by a flaky network
                    "!!!not json!!!",
                    """{"id":"m2","event":"message","message":"BBBB"}""",
                    """{"id":"m3","event":"polling_request","message":"C"}""",
                    """{"event":"keepalive"}""",
                ).joinToString("\n", postfix = "\n"),
                1,
            ),
        )

        val events = readStream(
            NtfyMessages.subscribeUrl(server.url("/").toString(), "fp-abc")!!,
        )

        // The two good messages, in order; open and keepalive are bookends, and the
        // two broken lines are dropped without ending the stream.
        val messages = events.filterIsInstance<NtfyEvent.Message>()
        assertEquals(listOf("m1", "m2"), messages.map { it.id })
        assertEquals(listOf("AAAA", "BBBB"), messages.map { it.message })
        assertTrue(events.first() is NtfyEvent.Open)
        assertTrue(events.last() is NtfyEvent.Keepalive)
    }

    @Test
    fun theRelayPublishesBytesThePhoneCanDecryptWithTheServerFixture() {
        // The ntfy `message` field is standard base64 of the untouched blob - the
        // exact round trip the relay performs. Replaying the FitPub server's own
        // fixture through it proves the fast path decrypts to the same payload the
        // 15-minute mailbox check produces, collapse tag included.
        val ntfyLine = """{"id":"m1","event":"message",""" +
            """"tags":["fitpub","fitpub-activity_shared"],""" +
            """"message":"${NtfyInstantFixture.publishedBlob()}"}"""

        val event = NtfyMessages.parse(ntfyLine) as NtfyEvent.Message
        val payload = NtfyInstantFixture.decrypt(event.message)

        assertNotNull("the relayed blob must decrypt on-device", payload)
        assertEquals("FitPub", payload!!.title)
        assertEquals("Alice boosted Morning Run", payload.body)
        assertEquals("fitpub-activity_shared", payload.tag)
        assertEquals("/activities/5f0e7b2c-9d31-4a6e-8c5f-2b7e4d1a9c03", payload.url)
        // The ntfy tag carries the same value, so the collapse behaves identically
        // whichever route delivered the message.
        assertEquals(payload.tag, event.tags.last { NtfyMessages.isPayloadTag(it) })
    }

    @Test
    fun anUndecryptableBlobIsDroppedRatherThanPosted() {
        assertNull(NtfyInstantFixture.decrypt("bm90LWEtYmxvYg=="))
        assertNull(NtfyInstantFixture.decrypt("not even base64 ###"))
    }

    // ------------------------------------------------------------------ the dedupe

    @Test
    fun dedupeDropsAReDeliveredNtfyId() {
        val deduper = NtfyDeduper()
        assertTrue(deduper.accept("m1"))
        // `since=` deliberately re-delivers its boundary, so m1 arrives twice.
        assertFalse("a re-delivered id must be dropped", deduper.accept("m1"))
        assertTrue(deduper.accept("m2"))
        assertEquals("m2", deduper.lastId)
    }

    @Test
    fun dedupeRemembersTheCursorAcrossARestart() {
        val first = NtfyDeduper()
        first.accept("m1")
        first.accept("m2")

        // A killed-and-restarted service restores the persisted cursor, so the first
        // replay request does not re-run the whole ntfy cache as if it were new.
        val restarted = NtfyDeduper()
        restarted.restore(first.lastId)
        assertEquals("m2", restarted.lastId)
        assertTrue("a genuinely new id is still accepted", restarted.accept("m3"))
    }

    @Test
    fun dedupeIsBoundedSoAConnectionCannotGrowWithoutLimit() {
        val deduper = NtfyDeduper(capacity = 4)
        (1..100).forEach { assertTrue(deduper.accept("m$it")) }
        assertEquals("m100", deduper.lastId)
        assertTrue(deduper.accept("m101"))
        assertTrue("m100 is still inside the window", !deduper.accept("m100"))
    }

    @Test
    fun aBlankIdIsDeliveredRatherThanSilentlyDropped() {
        val deduper = NtfyDeduper()
        // Nothing to dedupe against, and dropping it would lose a notification.
        assertTrue(deduper.accept(""))
        assertNull("a blank id must not move the cursor", deduper.lastId)
    }

    // ------------------------------------------------------------------ helpers

    /** Reads a real NDJSON body off a MockWebServer, exactly as the service does. */
    private fun readStream(url: String): List<NtfyEvent> {
        val events = mutableListOf<NtfyEvent>()
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals(200, response.code)
            val source = checkNotNull(response.body).source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                NtfyMessages.parse(line)?.let { events.add(it) }
            }
        }
        return events
    }
}

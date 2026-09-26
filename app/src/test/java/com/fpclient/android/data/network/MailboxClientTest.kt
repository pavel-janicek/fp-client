package com.fpclient.android.data.network

import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The credential-free mailbox client's pure parts: base URL normalization (what the user
 * types in Settings) and the relay's exact `GET /push/<id>` response shape (RFC 8030 +
 * Go `[]byte` standard-base64 payload encoding).
 */
class MailboxClientTest {

    private val client = MailboxClient()

    @Test
    fun normalizeBaseUrl_addsHttpsStripsTrailingSlashAndTrims() {
        assertEquals("https://push.paveljanicek.cz", MailboxClient.normalizeBaseUrl("  push.paveljanicek.cz/  "))
        assertEquals("https://push.example.test", MailboxClient.normalizeBaseUrl("https://push.example.test///"))
        assertEquals("http://localhost:8090", MailboxClient.normalizeBaseUrl("http://localhost:8090/"))
        assertEquals("https://vps.example.cz/relay", MailboxClient.normalizeBaseUrl("vps.example.cz/relay"))
    }

    @Test
    fun normalizeBaseUrl_keepsBlankSoTheCallerCanReportIt() {
        assertEquals("", MailboxClient.normalizeBaseUrl("   "))
    }

    @Test
    fun parseMessages_readsTheRelayShapeIncludingStandardBase64Payload() {
        val blob = ByteArray(48) { it.toByte() }
        val encoded = Base64.getEncoder().encodeToString(blob) // Go []byte JSON: standard, padded
        val body = """{"messages":[{"ttl":86400,"received":1770000000,"payload":"$encoded"}]}"""

        val messages = client.parseMessages(body)

        assertEquals(1, messages.size)
        assertEquals(86400L, messages[0].ttl)
        assertEquals(1770000000L, messages[0].received)
        assertArrayEquals(blob, Base64.getDecoder().decode(messages[0].payload))
    }

    @Test
    fun parseMessages_emptyQueueMeansNoPushMessageWasEverSent() {
        val messages = client.parseMessages("""{"messages":[]}""")
        assertTrue(messages.isEmpty())
    }

    @Test
    fun mint_readsTheManageTokenTheRelayMintsAlongsideTheEndpoint() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(201)
                    .setBody("""{"endpoint":"${server.url("/push/abc")}","manageToken":"mt-secret"}"""),
            )
            val result = client.mint(server.url("/").toString())
            server.takeRequest()

            val minted = (result as ApiResult.Success).data
            assertEquals("${server.url("/push/abc")}", minted.endpoint)
            assertEquals("mt-secret", minted.manageToken)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun mint_onAPre8iRelayWithoutATokenIsStillUsableFor8h() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(201)
                    .setBody("""{"endpoint":"${server.url("/push/abc")}"}"""),
            )
            val minted = (client.mint(server.url("/").toString()) as ApiResult.Success).data
            server.takeRequest()

            assertEquals("${server.url("/push/abc")}", minted.endpoint)
            assertNull("a relay before 8i mints no token", minted.manageToken)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun parseForwardStatus_readsTheRelayShapeWithAndWithoutATopic() {
        val on = client.parseForwardStatus("""{"enabled":true,"topic":"fp-abc123"}""")
        assertTrue(on.enabled)
        assertEquals("fp-abc123", on.topic)

        // The relay omits `topic` when forwarding is off — it must decode to "disabled",
        // not to an error (omitempty is on the Go side).
        val off = client.parseForwardStatus("""{"enabled":false}""")
        assertFalse(off.enabled)
        assertNull(off.topic)
    }
}

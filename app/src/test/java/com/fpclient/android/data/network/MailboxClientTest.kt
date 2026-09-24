package com.fpclient.android.data.network

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}

package com.fpclient.android.notifications

import com.fpclient.android.data.dto.PushPayloadDto
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The decrypted mailbox message contract (Iteration 8h): the five keys
 * `WebPushService.buildPayload` writes, with the optional ones optional and anything
 * server-side unexpected tolerated (same `ignoreUnknownKeys` policy as every other DTO).
 */
class PushPayloadsTest {

    @Test
    fun decode_readsAllFiveServerFields() {
        val payload = PushPayloads.decode(
            """
            {"title":"FitPub","body":"Sam started following you","icon":"/img/fitpub-logo-256.png",
             "tag":"fitpub-user_followed","url":"/notifications"}
            """.trimIndent().toByteArray(),
        )
        assertEquals("FitPub", payload.title)
        assertEquals("Sam started following you", payload.body)
        assertEquals("/img/fitpub-logo-256.png", payload.icon)
        assertEquals("fitpub-user_followed", payload.tag)
        assertEquals("/notifications", payload.url)
    }

    @Test
    fun decode_toleratesUnknownFieldsAndMissingOptionals() {
        val payload: PushPayloadDto = PushPayloads.decode(
            """{"title":"FitPub","body":"Something happened","futureField":42}""".toByteArray(),
        )
        assertEquals("FitPub", payload.title)
        assertEquals("Something happened", payload.body)
        assertNull(payload.icon)
        assertNull(payload.tag)
        assertNull(payload.url)
    }

    @Test
    fun decode_rejectsPlaintextThatIsNotAPayload() {
        assertThrows(SerializationException::class.java) {
            PushPayloads.decode("{}".toByteArray())
        }
        assertThrows(SerializationException::class.java) {
            PushPayloads.decode("not json at all".toByteArray())
        }
    }
}

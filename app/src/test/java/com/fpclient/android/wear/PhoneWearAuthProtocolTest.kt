package com.fpclient.android.wear

import com.fpclient.android.data.session.Session
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneWearAuthProtocolTest {
    @Test
    fun signedInSessionRelaysServerTokenAndIdentity() {
        val session = Session(
            serverUrl = "https://fitpub.example",
            token = "jwt-token",
            username = "runner",
            displayName = "Runner",
        )

        val encoded = PhoneWearAuthProtocol.encode(PhoneWearAuthProtocol.stateFor(session))
        val message = Json.decodeFromString<PhoneWearAuthMessage>(encoded.decodeToString())

        assertEquals("credentials", message.type)
        assertEquals("https://fitpub.example", message.serverUrl)
        assertEquals("jwt-token", message.token)
        assertEquals("runner", message.username)
        assertEquals("Runner", message.displayName)
    }

    @Test
    fun revokedSessionDistinguishesExpiredFromSignedOut() {
        assertEquals("expired", PhoneWearAuthProtocol.stateFor(Session(authExpired = true)).type)
        assertEquals("signed_out", PhoneWearAuthProtocol.stateFor(Session()).type)
    }
}
package com.fpclient.android.wear.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class WearAuthProtocolTest {
    @Test
    fun decodesPhoneCredentialsAndIgnoresFutureFields() {
        val message = WearAuthProtocol.decode(
            """{"type":"credentials","serverUrl":"https://fitpub.example","token":"jwt-token","username":"runner","displayName":"Runner","futureField":true}"""
                .toByteArray(),
        )

        assertEquals("credentials", message.type)
        assertEquals("https://fitpub.example", message.serverUrl)
        assertEquals("jwt-token", message.token)
        assertEquals("runner", message.username)
        assertEquals("Runner", message.displayName)
    }

    @Test
    fun protocolUsesStableCapabilityAndMessagePaths() {
        assertEquals("fitpub_phone", WearAuthProtocol.CAPABILITY)
        assertEquals("fitpub_watch", WearAuthProtocol.WATCH_CAPABILITY)
        assertEquals("/fitpub/auth/request", WearAuthProtocol.REQUEST_PATH)
        assertEquals("/fitpub/auth/revoke", WearAuthProtocol.REVOKE_PATH)
    }
}
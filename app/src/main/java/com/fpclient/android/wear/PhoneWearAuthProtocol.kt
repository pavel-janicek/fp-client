package com.fpclient.android.wear

import com.fpclient.android.data.session.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PhoneWearAuthMessage(
    val type: String,
    val serverUrl: String? = null,
    val token: String? = null,
    val username: String? = null,
    val displayName: String? = null,
)

internal object PhoneWearAuthProtocol {
    const val CAPABILITY = "fitpub_phone"
    const val WATCH_CAPABILITY = "fitpub_watch"
    const val REQUEST_PATH = "/fitpub/auth/request"
    const val CREDENTIALS_PATH = "/fitpub/auth/credentials"
    const val SIGNED_OUT_PATH = "/fitpub/auth/signed_out"
    const val EXPIRED_PATH = "/fitpub/auth/expired"
    const val REVOKE_PATH = "/fitpub/auth/revoke"

    private val json = Json { explicitNulls = false; ignoreUnknownKeys = true }

    fun encode(message: PhoneWearAuthMessage): ByteArray =
        json.encodeToString(message).toByteArray(Charsets.UTF_8)

    fun stateFor(session: Session): PhoneWearAuthMessage = when {
        session.isLoggedIn -> PhoneWearAuthMessage(
            type = "credentials",
            serverUrl = session.serverUrl,
            token = session.token,
            username = session.username,
            displayName = session.displayName,
        )
        session.authExpired -> PhoneWearAuthMessage(type = "expired")
        else -> PhoneWearAuthMessage(type = "signed_out")
    }
}
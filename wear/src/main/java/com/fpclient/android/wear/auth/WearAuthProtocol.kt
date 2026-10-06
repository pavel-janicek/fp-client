package com.fpclient.android.wear.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
internal data class WearAuthMessage(
    val type: String,
    val serverUrl: String? = null,
    val token: String? = null,
    val username: String? = null,
    val displayName: String? = null,
)

internal object WearAuthProtocol {
    const val CAPABILITY = "fitpub_phone"
    const val WATCH_CAPABILITY = "fitpub_watch"
    const val REQUEST_PATH = "/fitpub/auth/request"
    const val CREDENTIALS_PATH = "/fitpub/auth/credentials"
    const val SIGNED_OUT_PATH = "/fitpub/auth/signed_out"
    const val EXPIRED_PATH = "/fitpub/auth/expired"
    const val REVOKE_PATH = "/fitpub/auth/revoke"

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(payload: ByteArray): WearAuthMessage =
        json.decodeFromString(payload.toString(Charsets.UTF_8))
}
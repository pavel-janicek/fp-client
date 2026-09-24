package com.fpclient.android.notifications

import com.fpclient.android.data.dto.PushPayloadDto
import kotlinx.serialization.json.Json

/**
 * Decodes the plaintext a decrypted mailbox message carries (Iteration 8h): the exact
 * `{title, body, icon, tag, url}` JSON `WebPushService.buildPayload` serialises.
 *
 * Android-free so the parse — including its tolerance for server-side additions — is pinned
 * by JVM unit tests; unknown keys are ignored like everywhere else in the app.
 */
object PushPayloads {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** @throws kotlinx.serialization.SerializationException when the plaintext is not the
     *  expected JSON (the caller then drops the message instead of posting a broken one). */
    fun decode(plaintext: ByteArray): PushPayloadDto =
        json.decodeFromString(String(plaintext, Charsets.UTF_8))
}

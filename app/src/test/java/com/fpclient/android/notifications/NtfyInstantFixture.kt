package com.fpclient.android.notifications

import com.fpclient.android.data.dto.PushPayloadDto
import com.fpclient.android.notifications.PushFixture.BLOB
import com.fpclient.android.notifications.PushFixture.P256DH
import com.fpclient.android.notifications.PushFixture.PRIVATE
import com.fpclient.android.notifications.PushFixture.AUTH
import java.util.Base64

/**
 * Runs the *production* decrypt path (`WebPushCrypto` + `PushPayloads`, exactly
 * what `PushRepository.fetchPayloads` uses) over a standard-base64 blob, with no
 * network and no Android types.
 *
 * Exists so the 8j tests can assert that what ntfy delivers — the relay's
 * untouched ciphertext, base64-encoded — decrypts to the same payload the 15-minute
 * mailbox check produces, collapse tag included. Keeping this pointed at the real
 * code rather than a re-implementation is the point: a test that decrypts its own
 * way proves nothing about the app.
 */
internal object NtfyInstantFixture {

    /** The blob a relay publishes to ntfy, in the standard-base64 form it uses. */
    fun publishedBlob(): String =
        Base64.getEncoder().encodeToString(Base64.getUrlDecoder().decode(BLOB))

    /** Decrypts one published blob, or null when it cannot be read (never throws). */
    fun decrypt(base64Blob: String): PushPayloadDto? = runCatching {
        val blob = Base64.getDecoder().decode(base64Blob)
        val plaintext = WebPushCrypto.decrypt(
            blob,
            WebPushCrypto.fromBase64Url(PRIVATE),
            WebPushCrypto.fromBase64Url(P256DH),
            WebPushCrypto.fromBase64Url(AUTH),
        )
        PushPayloads.decode(plaintext)
    }.getOrNull()
}

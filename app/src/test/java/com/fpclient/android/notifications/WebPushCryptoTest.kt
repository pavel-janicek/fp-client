package com.fpclient.android.notifications

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Iteration 8h decryption pipeline against vectors captured from the **server's own**
 * WebPushService: [PushFixture] is produced by invoking `social.fitpub.push.WebPushService.encrypt`
 * via reflection (fitpub-push-relay/testdata), so the fixture test proves the app decrypts
 * byte-for-byte what a real FitPub instance sends. The rest pins HKDF against RFC 5869 test
 * case 1, the generated key shapes, the record framing and the malformed-header rejections.
 */
class WebPushCryptoTest {

    // ------------------------------------------------------------------
    // The server's fixture — the vector required by PLAN 8h
    // ------------------------------------------------------------------

    @Test
    fun serverFixture_decryptsToTheExactPayloadWebPushServiceEncrypted() {
        val plaintext = WebPushCrypto.decrypt(
            Base64.getUrlDecoder().decode(PushFixture.BLOB),
            WebPushCrypto.fromBase64Url(PushFixture.PRIVATE),
            WebPushCrypto.fromBase64Url(PushFixture.P256DH),
            WebPushCrypto.fromBase64Url(PushFixture.AUTH),
        )

        assertEquals(PushFixture.PAYLOAD, String(plaintext, Charsets.UTF_8))

        val payload = PushPayloads.decode(plaintext)
        assertEquals("FitPub", payload.title)
        assertEquals("Alice boosted Morning Run", payload.body)
        assertEquals("/img/fitpub-logo-256.png", payload.icon)
        assertEquals("fitpub-activity_shared", payload.tag)
        assertEquals("/activities/5f0e7b2c-9d31-4a6e-8c5f-2b7e4d1a9c03", payload.url)
    }

    @Test
    fun serverFixture_failsAuthenticationWithAnyOtherKey() {
        val other = WebPushCrypto.generateRecipientKeys()
        // Different private key: the ECDH shared secret diverges, so the GCM tag must reject
        // the message instead of letting "decrypted" garbage escape.
        assertThrows(GeneralSecurityException::class.java) {
            WebPushCrypto.decrypt(
                Base64.getUrlDecoder().decode(PushFixture.BLOB),
                other.privateKey,
                WebPushCrypto.fromBase64Url(PushFixture.P256DH),
                WebPushCrypto.fromBase64Url(PushFixture.AUTH),
            )
        }
    }

    // ------------------------------------------------------------------
    // HKDF — RFC 5869 test case 1 (SHA-256)
    // ------------------------------------------------------------------

    @Test
    fun hkdf_matchesRfc5869TestCase1() {
        val ikm = hex("0b".repeat(22))
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")

        val prk = WebPushCrypto.hkdfExtract(salt, ikm)
        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", hexOf(prk))

        // 42 bytes = two expand blocks, so this also pins the multi-block loop.
        val okm = WebPushCrypto.hkdfExpand(prk, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            hexOf(okm),
        )
    }

    // ------------------------------------------------------------------
    // Key generation & point encoding
    // ------------------------------------------------------------------

    @Test
    fun generateKeys_producesTheShapesASubscriptionRegisters() {
        val keys = WebPushCrypto.generateRecipientKeys()
        assertEquals(65, keys.publicKey.size)
        assertEquals(0x04, keys.publicKey[0].toInt())
        assertEquals(32, keys.privateKey.size)
        assertEquals(16, keys.authSecret.size)
        assertTrue("fresh keys must not be all-zero", !keys.authSecret.contentEquals(ByteArray(16)))

        val point = WebPushCrypto.decodeUncompressedPoint(keys.publicKey)
        assertArrayEquals(keys.publicKey, WebPushCrypto.encodeUncompressedPoint(point))

        val scalar = WebPushCrypto.privateKeyFromRaw(keys.privateKey).s
        assertEquals(1, scalar.signum())
        assertTrue(scalar.bitLength() in 1..256)
    }

    @Test
    fun decodeUncompressedPoint_rejectsCompressedOrShortEncodings() {
        assertThrows(IllegalArgumentException::class.java) {
            WebPushCrypto.decodeUncompressedPoint(ByteArray(65)) // no 0x04 prefix
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebPushCrypto.decodeUncompressedPoint(ByteArray(33))
        }
    }

    // ------------------------------------------------------------------
    // Round trips through the record framing (test-side seal mirrors the server's encrypt)
    // ------------------------------------------------------------------

    @Test
    fun roundTrip_singleRecordAsTheServerEmits() {
        val keys = WebPushCrypto.generateRecipientKeys()
        val content = PushFixture.PAYLOAD.toByteArray(Charsets.UTF_8)
        val blob = seal(content + byteArrayOf(0x02), keys.publicKey, keys.authSecret, recordSize = 4096)

        val plaintext = WebPushCrypto.decrypt(blob, keys.privateKey, keys.publicKey, keys.authSecret)
        assertArrayEquals(content, plaintext)
    }

    @Test
    fun roundTrip_multiRecordWithTrailingPadding() {
        val keys = WebPushCrypto.generateRecipientKeys()
        val content = ByteArray(100) { ('a' + it % 26).code.toByte() }
        // 101 framed bytes + 7 pad bytes across 32-byte records forces the multi-record loop
        // with its per-record nonce (record sequence XOR).
        val framed = content + byteArrayOf(0x02) + ByteArray(7)
        val blob = seal(framed, keys.publicKey, keys.authSecret, recordSize = 32)

        val plaintext = WebPushCrypto.decrypt(blob, keys.privateKey, keys.publicKey, keys.authSecret)
        assertArrayEquals(content, plaintext)
    }

    // ------------------------------------------------------------------
    // Malformed input
    // ------------------------------------------------------------------

    @Test
    fun decrypt_rejectsMalformedHeaders() {
        val keys = WebPushCrypto.generateRecipientKeys()
        val body = ByteArray(40) { 1 }

        assertThrows(IllegalArgumentException::class.java) {
            decryptRaw(ByteArray(10), keys)
        }
        // Record size 0 (checked before the key id).
        val zeroRecordSize = ByteBuffer.allocate(21).put(ByteArray(16)).putInt(0).put(65.toByte()).array()
        assertThrows(IllegalArgumentException::class.java) {
            decryptRaw(zeroRecordSize + body, keys)
        }
        // Key id length points past the end of the blob.
        val longKeyId = ByteBuffer.allocate(21).put(ByteArray(16)).putInt(4096).put(65.toByte()).array()
        assertThrows(IllegalArgumentException::class.java) {
            decryptRaw(longKeyId + ByteArray(10), keys)
        }
        // Ciphertext shorter than the GCM tag.
        assertThrows(IllegalArgumentException::class.java) {
            decryptRaw(longKeyId + keys.publicKey + ByteArray(8), keys)
        }
        // Key id is not a 65-byte uncompressed point (no 0x04 prefix).
        assertThrows(IllegalArgumentException::class.java) {
            decryptRaw(longKeyId + ByteArray(65) + body, keys)
        }
    }

    @Test
    fun decrypt_rejectsARecordWithoutTheLastRecordDelimiter() {
        val keys = WebPushCrypto.generateRecipientKeys()
        // Framed WITHOUT the trailing 0x02: structurally valid message, wrong content.
        val blob = seal("hello".toByteArray(), keys.publicKey, keys.authSecret, recordSize = 4096)
        val error = assertThrows(IllegalArgumentException::class.java) {
            WebPushCrypto.decrypt(blob, keys.privateKey, keys.publicKey, keys.authSecret)
        }
        assertTrue(error.message!!.contains("delimiter"))
    }

    @Test
    fun decrypt_rejectsATamperedBody() {
        val keys = WebPushCrypto.generateRecipientKeys()
        val blob = seal("payload".toByteArray() + byteArrayOf(0x02), keys.publicKey, keys.authSecret, 4096)
        blob[blob.size - 3] = (blob[blob.size - 3].toInt() xor 0x40).toByte()
        assertThrows(GeneralSecurityException::class.java) {
            WebPushCrypto.decrypt(blob, keys.privateKey, keys.publicKey, keys.authSecret)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun decryptRaw(blob: ByteArray, keys: RecipientKeys) =
        WebPushCrypto.decrypt(blob, keys.privateKey, keys.publicKey, keys.authSecret)

    /**
     * Test-side mirror of `WebPushService.encrypt` (RFC 8291 + RFC 8188): ECDH, HKDF, then
     * AES-128-GCM over the already-framed plaintext split into [recordSize]-byte records with
     * the sequence-nonce XOR. Exists only so the record framing (multi-record, padding) can be
     * exercised — the fixture test above remains the cross-implementation check.
     */
    private fun seal(
        framed: ByteArray,
        recipientPublicRaw: ByteArray,
        authSecret: ByteArray,
        recordSize: Int,
    ): ByteArray {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val ephemeral = generator.generateKeyPair()
        val asPublic = WebPushCrypto.encodeUncompressedPoint(ephemeral.public as ECPublicKey)

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(ephemeral.private)
        agreement.doPhase(WebPushCrypto.decodeUncompressedPoint(recipientPublicRaw), true)
        val sharedSecret = agreement.generateSecret()

        val prkAuth = WebPushCrypto.hkdfExtract(authSecret, sharedSecret)
        val info = "WebPush: info\u0000".toByteArray(Charsets.UTF_8) + recipientPublicRaw + asPublic
        val ikm = WebPushCrypto.hkdfExpand(prkAuth, info, 32)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val prk = WebPushCrypto.hkdfExtract(salt, ikm)
        val cek = WebPushCrypto.hkdfExpand(prk, "Content-Encoding: aes128gcm\u0000".toByteArray(Charsets.UTF_8), 16)
        val nonceBase = WebPushCrypto.hkdfExpand(prk, "Content-Encoding: nonce\u0000".toByteArray(Charsets.UTF_8), 12)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val records = ByteArrayOutputStream()
        var offset = 0
        var sequence = 0
        while (offset < framed.size) {
            val chunk = minOf(recordSize, framed.size - offset)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(cek, "AES"),
                GCMParameterSpec(128, recordNonce(nonceBase, sequence)),
            )
            records.write(cipher.doFinal(framed, offset, chunk))
            offset += chunk
            sequence++
        }

        return ByteBuffer.allocate(21 + asPublic.size + records.size())
            .put(salt)
            .putInt(recordSize)
            .put(asPublic.size.toByte())
            .put(asPublic)
            .put(records.toByteArray())
            .array()
    }

    private fun recordNonce(nonceBase: ByteArray, sequence: Int): ByteArray {
        val nonce = nonceBase.copyOf()
        var value = sequence
        for (i in nonce.size - 1 downTo nonce.size - 4) {
            nonce[i] = (nonce[i].toInt() xor (value and 0xff)).toByte()
            value = value ushr 8
        }
        return nonce
    }

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { Integer.parseInt(text.substring(it * 2, it * 2 + 2), 16).toByte() }

    private fun hexOf(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

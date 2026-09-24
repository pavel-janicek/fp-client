package com.fpclient.android.notifications

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The keys an Iteration 8h subscription is built from, generated on-device with plain JCA:
 * [publicKey] is the 65-byte uncompressed P-256 point published as `keys.p256dh`, [privateKey]
 * the 32-byte scalar that can only decrypt notification payloads, [authSecret] the 16-byte
 * RFC 8291 auth secret. [toString] is redacted so key material cannot leak into a log line.
 */
data class RecipientKeys(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val authSecret: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is RecipientKeys &&
            publicKey.contentEquals(other.publicKey) &&
            privateKey.contentEquals(other.privateKey) &&
            authSecret.contentEquals(other.authSecret)

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        result = 31 * result + authSecret.contentHashCode()
        return result
    }

    override fun toString(): String = "RecipientKeys(publicKey=${publicKey.size}B, privateKey=…, authSecret=…)"
}

/**
 * Web Push message crypto for Iteration 8h — a client-side mirror of the FitPub server's
 * `social.fitpub.push.WebPushService` implemented with **JCA only** (no new crypto library):
 * an ECDH P-256 keypair + 16-byte auth secret are generated on-device, and queued mailbox
 * blobs are decrypted per RFC 8291 (`aes128gcm`, RFC 8188 framing).
 *
 * Android-free on purpose, so the whole pipeline — including the byte-for-byte server fixture
 * — is unit-tested on the JVM (see `WebPushCryptoTest`).
 */
object WebPushCrypto {

    /** Length of the auth secret every subscription carries (RFC 8291 §3.3). */
    const val AUTH_SECRET_BYTES = 16

    /** P-256 private scalar / coordinate length. */
    private const val P256_BYTES = 32

    /** Uncompressed EC point: 0x04 || X || Y. */
    private const val UNCOMPRESSED_POINT_BYTES = 65

    /** aes128gcm header: salt (16) + record size (4) + key id length (1). */
    private const val HEADER_BYTES = 21

    private const val SALT_BYTES = 16

    private const val TAG_BYTES = 16

    private val p256Params: ECParameterSpec by lazy {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        params.getParameterSpec(ECParameterSpec::class.java)
    }

    // ------------------------------------------------------------------
    // Key generation & encoding
    // ------------------------------------------------------------------

    /** Generates the recipient keypair + auth secret of a fresh subscription (JCA only). */
    fun generateRecipientKeys(): RecipientKeys {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val authSecret = ByteArray(AUTH_SECRET_BYTES)
        SecureRandom().nextBytes(authSecret)
        return RecipientKeys(
            publicKey = encodeUncompressedPoint(pair.public as ECPublicKey),
            privateKey = toFixedLength((pair.private as ECPrivateKey).s, P256_BYTES),
            authSecret = authSecret,
        )
    }

    /** 65-byte uncompressed point (`0x04 || X || Y`) — the encoding both sides publish. */
    fun encodeUncompressedPoint(key: ECPublicKey): ByteArray {
        val x = toFixedLength(key.w.affineX, P256_BYTES)
        val y = toFixedLength(key.w.affineY, P256_BYTES)
        val result = ByteArray(UNCOMPRESSED_POINT_BYTES)
        result[0] = 0x04
        System.arraycopy(x, 0, result, 1, P256_BYTES)
        System.arraycopy(y, 0, result, 1 + P256_BYTES, P256_BYTES)
        return result
    }

    /** Inverse of [encodeUncompressedPoint]; rejects compressed/short encodings. */
    fun decodeUncompressedPoint(encoded: ByteArray): ECPublicKey {
        if (encoded.isEmpty() || encoded[0].toInt() != 0x04 || encoded.size != UNCOMPRESSED_POINT_BYTES) {
            throw IllegalArgumentException("Expected 65-byte uncompressed EC point")
        }
        val x = BigInteger(1, encoded.copyOfRange(1, 1 + P256_BYTES))
        val y = BigInteger(1, encoded.copyOfRange(1 + P256_BYTES, UNCOMPRESSED_POINT_BYTES))
        return KeyFactory.getInstance("EC")
            .generatePublic(ECPublicKeySpec(ECPoint(x, y), p256Params)) as ECPublicKey
    }

    /** Rebuilds the agreement-capable key object from a stored 32-byte scalar. */
    fun privateKeyFromRaw(raw: ByteArray): ECPrivateKey {
        require(raw.size == P256_BYTES) { "P-256 private key must be $P256_BYTES bytes" }
        val spec = ECPrivateKeySpec(BigInteger(1, raw), p256Params)
        return KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
    }

    /** base64url without padding — the encoding the server stores and the relay URLs use. */
    fun toBase64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** Inverse of [toBase64Url]; also accepts padded input. */
    fun fromBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

    // ------------------------------------------------------------------
    // RFC 8291 — Message Encryption for Web Push (decryption)
    // ------------------------------------------------------------------

    /**
     * Decrypts one `aes128gcm` push message: parses the RFC 8188 header
     * `[salt | record size | ephemeral AS public key]`, performs ECDH with the stored
     * private key, derives CEK + nonce per RFC 8291 §3.4 (HKDF-SHA256), AES-128-GCM-decrypts
     * every record and strips the RFC 8188 last-record delimiter (`0x02` + zero padding).
     *
     * @param blob the raw body the mailbox relay queued byte-for-byte.
     * @param privateKeyRaw the stored 32-byte recipient scalar.
     * @param publicKeyRaw the stored 65-byte recipient point (it feeds the `WebPush: info`
     *   derivation string, so it must be the same key published as `p256dh`).
     * @param authSecret the stored 16-byte auth secret.
     * @return the plaintext payload, ready for [PushPayloads.decode].
     * @throws IllegalArgumentException on a malformed header, record or missing delimiter.
     * @throws java.security.GeneralSecurityException when authentication fails (wrong key,
     *   tampered or truncated ciphertext).
     */
    fun decrypt(
        blob: ByteArray,
        privateKeyRaw: ByteArray,
        publicKeyRaw: ByteArray,
        authSecret: ByteArray,
    ): ByteArray {
        if (blob.size < HEADER_BYTES) {
            throw IllegalArgumentException("aes128gcm blob too short: ${blob.size} bytes")
        }
        val salt = blob.copyOfRange(0, SALT_BYTES)
        val recordSize = ByteBuffer.wrap(blob, SALT_BYTES, 4).int
        if (recordSize <= 0) {
            throw IllegalArgumentException("Invalid aes128gcm record size: $recordSize")
        }
        val keyIdLength = blob[20].toInt() and 0xff
        if (keyIdLength == 0 || blob.size < HEADER_BYTES + keyIdLength) {
            throw IllegalArgumentException("Invalid aes128gcm key id length: $keyIdLength")
        }
        val asPublicRaw = blob.copyOfRange(HEADER_BYTES, HEADER_BYTES + keyIdLength)
        val body = blob.copyOfRange(HEADER_BYTES + keyIdLength, blob.size)
        if (body.size <= TAG_BYTES) {
            throw IllegalArgumentException("aes128gcm ciphertext shorter than its GCM tag")
        }
        if (publicKeyRaw.size != UNCOMPRESSED_POINT_BYTES || publicKeyRaw[0].toInt() != 0x04) {
            throw IllegalArgumentException("Stored public key is not a 65-byte uncompressed point")
        }

        // ECDH(our private key, the ephemeral application-server key from the header).
        val ephemeral = decodeUncompressedPoint(asPublicRaw)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKeyFromRaw(privateKeyRaw))
        agreement.doPhase(ephemeral, true)
        val sharedSecret = agreement.generateSecret()

        // RFC 8291 §3.4 key derivation (mirrors WebPushService.encrypt steps 4-6).
        val prkAuth = hkdfExtract(authSecret, sharedSecret)
        val info = "WebPush: info\u0000".toByteArray(Charsets.UTF_8) + publicKeyRaw + asPublicRaw
        val ikm = hkdfExpand(prkAuth, info, 32)
        val prk = hkdfExtract(salt, ikm)
        val cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\u0000".toByteArray(Charsets.UTF_8), 16)
        val nonceBase = hkdfExpand(prk, "Content-Encoding: nonce\u0000".toByteArray(Charsets.UTF_8), 12)

        return stripDelimiter(decryptRecords(body, recordSize, cek, nonceBase))
    }

    /** Decrypts each RFC 8188 record (record nonce = base nonce XOR 96-bit sequence, §7). */
    private fun decryptRecords(
        body: ByteArray,
        recordSize: Int,
        cek: ByteArray,
        nonceBase: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var offset = 0
        var sequence = 0
        while (offset < body.size) {
            val remaining = body.size - offset
            if (remaining <= TAG_BYTES) {
                throw IllegalArgumentException("Truncated aes128gcm record")
            }
            val cipherLength = minOf(recordSize, remaining - TAG_BYTES)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(cek, "AES"),
                GCMParameterSpec(TAG_BYTES * 8, recordNonce(nonceBase, sequence)),
            )
            out.write(cipher.doFinal(body, offset, cipherLength + TAG_BYTES))
            offset += cipherLength + TAG_BYTES
            sequence++
        }
        return out.toByteArray()
    }

    /** The record nonce: base nonce XOR the 96-bit record sequence number (0, 1, 2, …). */
    private fun recordNonce(nonceBase: ByteArray, sequence: Int): ByteArray {
        val nonce = nonceBase.copyOf()
        var value = sequence
        for (i in nonce.size - 1 downTo nonce.size - 4) {
            nonce[i] = (nonce[i].toInt() xor (value and 0xff)).toByte()
            value = value ushr 8
        }
        return nonce
    }

    /** Strips the zero padding and the `0x02` last-record delimiter (RFC 8188 §7). */
    private fun stripDelimiter(plaintext: ByteArray): ByteArray {
        var i = plaintext.size - 1
        while (i >= 0 && plaintext[i].toInt() == 0) i--
        if (i < 0 || plaintext[i].toInt() != 0x02) {
            throw IllegalArgumentException("Missing RFC 8188 last-record delimiter (0x02)")
        }
        return plaintext.copyOfRange(0, i)
    }

    // ------------------------------------------------------------------
    // HKDF (SHA-256) — pinned against RFC 5869 test case 1 in the unit tests
    // ------------------------------------------------------------------

    /** HKDF-Extract: `PRK = HMAC-Hash(salt, IKM)`. */
    fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    /** HKDF-Expand per RFC 5869 §2.2 — multi-block, so any `length` up to 255·32 works. */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32) { "Invalid HKDF expand length: $length" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var position = 0
        var counter = 1
        while (position < length) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val chunk = minOf(previous.size, length - position)
            System.arraycopy(previous, 0, out, position, chunk)
            position += chunk
            counter++
        }
        return out
    }

    /** Left-pads (or trims) a BigInteger to the fixed width P-256 uses. */
    private fun toFixedLength(value: BigInteger, width: Int): ByteArray {
        val bytes = value.toByteArray()
        return when {
            bytes.size == width -> bytes
            bytes.size == width + 1 && bytes[0].toInt() == 0 -> bytes.copyOfRange(1, bytes.size)
            else -> {
                val result = ByteArray(width)
                System.arraycopy(bytes, 0, result, width - bytes.size, bytes.size)
                result
            }
        }
    }
}


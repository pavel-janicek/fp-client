package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

/**
 * Request/response shapes for Iteration 8h and 8i — the app-side half of the FitPub server's
 * existing Web Push API (`social.fitpub.push.boundary.PushSubscriptionResource`) plus the
 * RFC 8030 mailbox relay (8g), instant forwarding to ntfy (8i), and the encrypted payload
 * the relay hands back.
 */

/** The subscriber's public key (`p256dh`) and auth secret, both base64url without padding. */
@Serializable
data class PushKeysDto(val p256dh: String, val auth: String)

/** `POST /api/web/push/subscribe` body — verified against the server's `SubscriptionRequest`. */
@Serializable
data class PushSubscribeRequest(val endpoint: String, val keys: PushKeysDto)

/** `DELETE /api/web/push/subscribe` body — the server's `UnsubscribeRequest`. */
@Serializable
data class PushUnsubscribeRequest(val endpoint: String)

/** `{"status": "subscribed" | "unsubscribed"}` from the server's subscribe endpoints. */
@Serializable
data class PushStatusDto(val status: String? = null)

/**
 * `POST /mailbox` on the relay answers 201 with the endpoint the phone must register, plus
 * an optional `manageToken` (Plan 8i) that authorises instant forwarding configuration.
 */
@Serializable
data class MailboxCreateDto(
    val endpoint: String,
    val manageToken: String? = null,
)

/** One queued push message from `GET /push/<id>`; [payload] is standard base64 (Go `[]byte`
 *  JSON encoding) of the exact aes128gcm blob the FitPub server POSTed. */
@Serializable
data class MailboxMessageDto(
    val ttl: Long = 0L,
    val received: Long = 0L,
    val payload: String,
)

/** Relay fetch response: `{"messages":[…]}`, empty list when nothing is pending. */
@Serializable
data class MailboxMessagesDto(val messages: List<MailboxMessageDto> = emptyList())

/**
 * Decryption key material uploaded to the relay for instant ntfy delivery (Iteration 8i).
 * All fields are base64url-encoded without padding:
 * - [p256dh]: 65-byte uncompressed public point
 * - [auth]: 16-byte RFC 8291 auth secret
 * - [privkey]: 32-byte P-256 private scalar
 */
@Serializable
data class ForwardKeysDto(
    val p256dh: String,
    val auth: String,
    val privkey: String,
)

/** `PUT /push/{id}/forward` request body for enabling instant delivery via ntfy. */
@Serializable
data class ForwardRequestDto(
    val topic: String,
    val clickBase: String? = null,
    val keys: ForwardKeysDto,
)

/** `GET /push/{id}/forward` response reporting whether instant delivery is active on the relay. */
@Serializable
data class ForwardStatusDto(
    val enabled: Boolean = false,
    val topic: String? = null,
)

/**
 * The plaintext a decrypted push message carries — exactly the five keys
 * `WebPushService.buildPayload` writes, in that order. [title] and [body] are always present
 * server-side; [icon] is an instance-relative URL the OS notification deliberately does not
 * fetch (the notification shows the app's own small icon), and [tag] drives the collapse.
 */
@Serializable
data class PushPayloadDto(
    val title: String,
    val body: String,
    val icon: String? = null,
    val tag: String? = null,
    val url: String? = null,
)

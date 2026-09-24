package com.fpclient.android.data.dto

import kotlinx.serialization.Serializable

/**
 * Request/response shapes for Iteration 8h — the app-side half of the FitPub server's
 * existing Web Push API (`social.fitpub.push.boundary.PushSubscriptionResource`) plus the
 * RFC 8030 mailbox relay (8g) and the encrypted payload the relay hands back.
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

/** `POST /mailbox` on the relay answers 201 with the endpoint the phone must register. */
@Serializable
data class MailboxCreateDto(val endpoint: String)

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

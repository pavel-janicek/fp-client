# Push notifications — the whole stack

FP Client delivers in-app notifications (likes, comments, boosts, follows, follow requests)
while the app is closed. There are three independent delivery paths, and you only need the first
one:

| Path | Needs | Latency | Keys leave the phone? |
| --- | --- | --- | --- |
| **8f — background poll** (always on) | nothing | ~30 min (WorkManager floor) | no |
| **8h — mailbox push** | a self-hosted push mailbox on your own VPS | ~15 min | no |
| **8i — instant via ntfy** (optional) | the same VPS plus an ntfy server | seconds | the notification key only, opt-in |

Nothing here uses a third-party push service (no FCM, no Mozilla autopush). Every path is
self-hosted or local to the device.

---

## 1. 8f — the universal fallback

A WorkManager periodic worker (unique work `fitpub_notification_poll`, 30-minute interval,
`NetworkType.CONNECTED`) polls `GET api/web/notifications` page 0 and posts a local Android
notification for new rows. The last-seen id lives in DataStore together with the owning
`serverUrl|username`, so the cursor is per session: switching accounts neither replays the
previous account's rows nor swallows the new one's first page.

Nothing to configure, and nothing to install. This is also the path that keeps working when your
instance has Web Push switched off entirely.

## 2. 8h — mailbox push (self-hosted, ciphertext only)

The FitPub server already speaks Web Push. Instead of handing the encrypted payloads to a
commercial push service, your VPS runs a tiny RFC 8030 receiver
(`dockerized-server/fitpub-push-relay`) and the app subscribes *itself* as the endpoint.

- The app generates an ECDH P-256 keypair + a 16-byte auth secret **on-device** (JCA only, no
  crypto library) and stores it in `EncryptedSharedPreferences`.
- It mints a mailbox on your relay (`POST <mailbox>/mailbox`) and registers it with the instance
  (`POST /api/web/push/subscribe` with `{endpoint, keys{p256dh, auth}}`).
- When a notification happens, the instance pushes it to the mailbox. The mailbox only ever sees
  ciphertext — it has no account, no password and no session token.
- A WorkManager worker (`fitpub_push_mailbox_check`, 15 minutes) fetches queued blobs, decrypts
  RFC 8291 `aes128gcm` on-device and posts the notification with the payload's `tag` as the
  Android collapse tag.

The mailbox URL is a **capability**: anyone holding the endpoint URL can read your notifications
until the relay expires it (~30 days of no fetch), so the URL is a secret.

Disabling runs three calls in order: `DELETE /api/web/push/subscribe` (instance) →
`DELETE /push/<id>` (relay) → wipe the local keys. The local wipe happens even when the network
fails; anything left behind expires on its own.

## 3. 8i — instant delivery via ntfy (optional)

15 minutes is a long time to wait for a like. 8i makes delivery *instant* by having the relay
decrypt each message **as it arrives** and republish the readable text to a private ntfy topic.
The ntfy Android app (self-hosted, WebSocket mode) then shows it within seconds, even with FP
Client fully closed.

### The trust boundary — read this before switching it on

This is the only switch in the app that moves a key off the device, so here is exactly what
happens:

- The key uploaded is the **same keypair the phone already holds**. It can decrypt notification
  payloads and nothing else — it cannot access your account, your activities, your password or
  your session token.
- It is stored **only on the relay you chose** (your VPS), and only when you switch this on.
- The ntfy topic is a capability URL. Anyone who knows it can read these notifications.
- Switching it off issues `DELETE /push/<id>/forward` and the relay drops the key immediately.
  Disabling mailbox push entirely drops it in the same call, since unregistering the mailbox
  removes its forward config.

If that trade is not for you, leave it off: 8h keeps working at ~15-minute latency with keys that
never leave the phone.

### VPS setup

Add the ntfy service to `docker-compose.yaml` (the reference stack already has it) and point the
relay at it:

```yaml
  ntfy:
    image: binwiederhier/ntfy
    container_name: ntfy
    command: serve
    restart: always
    environment:
      NTFY_BASE_URL: "https://ntfy.paveljanicek.cz"
      NTFY_LISTEN_HTTP: ":80"
      NTFY_BEHIND_PROXY: "true"
    volumes:
      - ntfy-data:/var/lib/ntfy

  push-relay:
    environment:
      RELAY_NTFY_URL: "http://ntfy:80"   # "" = instant mode off, relay stays ciphertext-only
```

TLS terminates in Nginx Proxy Manager: add a Proxy Host `ntfy.paveljanicek.cz → ntfy:80` with
**WebSockets enabled** (without them, the ntfy app falls back to polling and you lose the
"seconds" part). `RELAY_NTFY_URL` is the *internal* compose address; the phone uses the public
`https://ntfy.paveljanicek.cz`.

Leaving `RELAY_NTFY_URL` empty is a fully supported configuration: the relay stays strictly
ciphertext-only, `PUT /forward` answers 503, and the app says "instant delivery is not
configured on this relay" without breaking anything else.

### In the app (Settings → Push)

1. Enable **Mailbox push** (8h) first — the instant switch only appears while your account owns
   the mailbox.
2. Turn on **Deliver instantly through ntfy**. Leave the topic empty to have an unguessable
   `fp-…` topic generated, or type your own (a-z, 0-9, `-`, `_`, up to 64 characters).
3. Copy the topic, then in the ntfy app (ntfy.sh): *Settings → Use a different server* →
   `https://ntfy.paveljanicek.cz` (keep **WebSocket mode**), and subscribe to the topic.
4. Exempt ntfy from battery optimisation in Android's settings — otherwise Doze can delay it
   (same caveat as the location permission flow in 8e).

The 15-minute check stays scheduled as a fallback, and the ntfy notification deep-links into the
instance the event came from (`clickBase`).

### Relay API added for 8i

| Call | Meaning |
| --- | --- |
| `POST /mailbox` | mint; now also returns `manageToken` (stored as HMAC-SHA256 hash) |
| `PUT /push/<id>/forward` | `{"topic","clickBase","keys":{"p256dh","auth","privkey"}}` → 204; 400 bad topic/keys, 401 bad token, 503 no `RELAY_NTFY_URL` |
| `GET /push/<id>/forward` | `{"enabled":true,"topic":"…"}` |
| `DELETE /push/<id>/forward` | drop the key, stop publishing |

All three forward endpoints require `Authorization: Bearer <manageToken>`; the manage token is
the only credential that can add or remove a forward config, so a leaked endpoint URL is not
enough.

## Instance admin

Web Push is **off by default on every instance**. To enable it, the FitPub server needs
`FITPUB_PUSH_ENABLED=true` and a VAPID keypair (`WebPushService`). With it off,
`GET /api/web/push/vapid-key` answers 503 and the app's Mailbox push card says so, keeping 8f as
the delivery path. No server code changes are needed beyond those settings — the app uses the
existing `PushSubscriptionResource` and `WebPushService`.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| "Push is switched off on this instance" | server-side `FITPUB_PUSH_ENABLED`/VAPID missing; 8f keeps working |
| "Instant delivery is not configured on this relay" | `RELAY_NTFY_URL` empty (or the relay not restarted); set it and `docker compose up -d push-relay` |
| "This mailbox relay does not support instant delivery" | pre-8i relay (no manage token); update the relay |
| ntfy app shows nothing, app is fine | Proxy Host without WebSockets enabled, or ntfy not exempt from battery optimisation |
| Notifications arrive twice | shouldn't happen — 8f stops announcing while a matching 8h subscription is active, and 8i replaces the queue drain for the same events |
| Nothing after switching to a new mailbox | the old mailbox is unregistered first; re-enable if the new one looks dead |

## Related

- `dockerized-server/fitpub-push-relay/README.md` and `DEPLOYMENT.md` — the relay itself.
- `PLAN.md` → Iteration 8, sub-steps 8f / 8g / 8h / 8i — the design history and the wire
  contracts, including why no new dependencies were introduced.


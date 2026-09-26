# Push notifications — the whole stack

FP Client delivers in-app notifications (likes, comments, boosts, follows, follow requests)
while the app is closed. There are three independent delivery paths, and you only need the first
one:

| Path | Needs | Latency | Keys leave the phone? |
| --- | --- | --- | --- |
| **8f — background poll** (always on) | nothing | ~30 min (WorkManager floor) | no |
| **8h — mailbox push** | a self-hosted push mailbox on your own VPS | ~15 min | no |
| **8i/8j — instant via ntfy** (optional) | the same VPS plus an ntfy server | seconds | **no** — the relay forwards the same ciphertext, and FP Client subscribes itself |

Nothing here uses a third-party push service (no FCM, no Mozilla autopush). Every path is
self-hosted or local to the device. All three paths keep the decryption key on the phone.

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

## 3. 8i / 8j — instant delivery via ntfy (optional)

15 minutes is a long time to wait for a like. Instant delivery makes it seconds by taking a
shortcut through your own ntfy server: the relay republishes every message it receives to a
private ntfy topic, and FP Client — running as a foreground service — is subscribed to that
topic and posts the notification itself.

**Since 8j there is no second app.** The ntfy Android app, the copy-the-topic dance and the
separate battery exemption for ntfy are all gone: FP Client *is* the subscriber, so the
topic never leaves the device. What is left is a self-hosted ntfy address, which stays the
operator's choice.

### The trust boundary — this is the important part

**Your key never leaves this phone, not even for instant delivery.** The relay forwards the
*untouched* RFC 8291 `aes128gcm` blob — the same ciphertext it would have handed to the
15-minute check — and this app decrypts it on-device with the key that only ever existed
here. The relay and the ntfy server both see:

- a random mailbox endpoint and a random topic name, neither derived from your username;
- encrypted blobs, their size and their arrival time;
- your IP address, because that is unavoidable for any network service.

They cannot see the text of your notifications, who reacted, or anything about your account.
Instant delivery moves **exactly** as much off the phone as mailbox push does — only the
latency changes. (8i, the first cut of this feature, was the opposite: it uploaded the
private key so the relay could decrypt and forward readable text. 8j exists to undo that,
and `PRIVACY.md` in the relay repo says so.)

### In the app (Settings → Push)

1. Enable **Mailbox push** (8h) first — the instant switch only appears while your account
   owns the mailbox.
2. Turn on **Instant delivery**. Leave the topic empty to have an unguessable `fp-…` topic
   generated on the device, or type your own (a-z, 0-9, `-`, `_`, up to 64 characters).
   The topic is shown afterwards for reference only — there is nothing to copy it into.
3. The card then shows the live socket state: *not listening*, *connecting…*, *connected
   since … (last notification …)* or *retrying (attempt N, next try in … — reason)*. If it
   says "retrying", the reason is the one thing worth acting on.
4. Allow the permanent **"listening for notifications"** notification the first time Android
   asks. It is required: a foreground service is what keeps the connection alive, and the
   notification is how Android shows that it exists.
5. Exempt FP Client from battery optimisation (the card links to the settings page, and
   re-reads the state when you come back). Without it, Doze can delay the connection and
   delivery degrades to "whenever Android wakes the app".

The ongoing notification sits on its own `fitpub_instant_delivery` channel, so silencing
"FitPub instant delivery" in Android's notification settings does not also silence your
activity notifications. Turning the switch off (or disabling mailbox push) removes the
service, the notification and the connection in one go.

### The foreground service, and why `specialUse`

`InstantDeliveryService` is declared with `foregroundServiceType="specialUse"` and
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. The reason is a hard platform limit, not a preference:
`dataSync` is time-capped on API 35+ (6 h in 24 h) and `Service.onTimeout()` is then
mandatory, so an always-on push subscription would be torn down every few hours and silently
fall back to ~15-minute delivery. `specialUse` has no cap. `connectedDevice` was rejected
because it would drag a `BLUETOOTH_CONNECT` grant into the manifest for a feature that has
nothing to do with Bluetooth. `onTimeout()` is implemented anyway — it is required on
API 35+ and the correct response is the same in every case: stop cleanly.

### Reliability, and what is still lost

- A read timeout shorter than ntfy's `keepalive` interval means a silent connection *fails*
  and reconnects, rather than lingering as a connected-looking zombie.
- Reconnects use exponential backoff with full jitter, and ntfy's `poll_request` triggers an
  immediate `?poll=1&since=<lastId>` gap replay instead of a wait.
- **The relay enqueues the ciphertext even when a forward succeeds.** That is what makes the
  15-minute check a real backstop: a message the socket missed is still waiting for it, and
  the app drops the duplicate ntfy message id.
- What is genuinely lost: ntfy caches messages for **12 hours** by default, so a phone that
  was offline longer than that gets those events from the 15-minute check instead — the same
  class of loss as 8f, not a new one.

### Relay API added for 8i/8j

| Call | Meaning |
| --- | --- |
| `POST /mailbox` | mint; also returns `manageToken` (stored as HMAC-SHA256 hash) |
| `PUT /push/<id>/forward` | `{"topic","clickBase","tags"}` → 204; 400 bad topic/tags, 401 bad token, 503 no `RELAY_NTFY_URL` |
| `GET /push/<id>/forward` | `{"enabled":true,"topic":"…"}` |
| `DELETE /push/<id>/forward` | stop publishing to the topic |

All three forward endpoints require `Authorization: Bearer <manageToken>`; the manage token is
the only credential that can add or remove a forward config, so a leaked endpoint URL is not
enough.

**One-release compatibility:** `PUT /push/<id>/forward` still *accepts* the old
`"keys": {"p256dh", "auth", "privkey"}` object, because a phone running 8i sends one. The
relay reads none of it, stores none of it, and has no decryption code left in the binary;
`PushRepository.enableInstant` stopped sending the private scalar in the same release.

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
| **Instant delivery is not connecting** | the card names the reason, and it is worth reading: *Retrying — the ntfy server could not be resolved / refused the connection / the TLS connection failed* → check the **ntfy server** field against `RELAY_NTFY_URL` (the public `https://…` address, not the internal `http://ntfy:80`). *Retrying — ntfy answered HTTP 404* → the relay's topic and this phone's topic disagree; switch instant delivery off and on to re-register. *Retrying — the connection went quiet (no keep-alive)* → Android closed the socket: grant the battery-optimization exemption and make sure the **FitPub instant delivery** notification is not blocked. *Not listening* → the service is not running: toggle instant delivery off and on, or restart the app. |
| Notifications arrive twice | shouldn't happen — 8f stops announcing while a matching 8h subscription is active, and the ntfy path dedupes by ntfy message id. The relay deliberately also queues the ciphertext as a backstop, so a duplicate collapses onto the same notification instead of stacking. |
| Nothing after switching to a new mailbox | the old mailbox is unregistered first; re-enable if the new one looks dead |

## Related

- `dockerized-server/fitpub-push-relay/README.md` and `DEPLOYMENT.md` — the relay itself.
- `dockerized-server/fitpub-push-relay/PRIVACY.md` — the user-facing privacy page, including
  why instant delivery no longer makes the operator a reader.
- `PLAN.md` → Iteration 8, sub-steps 8f / 8g / 8h / 8i / 8j — the design history and the wire
  contracts, including why no new dependencies were introduced.


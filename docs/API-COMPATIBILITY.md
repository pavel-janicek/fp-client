# API compatibility

> Moved out of the README. Documents which FitPub server versions FP Client works with
> and the server-side breaking changes the app has adapted to.


This client is tested against the **FitPub** `main` branch (snapshot as of 2026-09-01), the
latest release line after the stateless-cookie authentication and `/api/web/**` routing
changes.

This client targets the FitPub REST API as implemented by the
`social.fitpub:fitpub` server artifact; the full endpoint
surface is defined in `app/src/main/java/com/fpclient/android/data/network/FitPubApi.kt`.

### 1.3.4 — web routing, cookie auth, and CSRF (breaking server changes)

The FitPub server (PRs #469, #470, #474) reworked its HTTP API; the app was realigned to match.
The endpoint contract has remained unchanged since FitPub 1.2.1; newer responses also carry
additive fields (`boostsCount`, `titleTruncated`, …) that the client tolerates (unknown JSON
keys are ignored) — that additive policy is unchanged.

* **Routes moved under `/api/web/`.** Web/consumer endpoints (auth, timeline, activities,
  likes/reactions, comments, users, analytics, notifications, privacy-zones, heatmap,
  batch-import, and `push/vapid-key`) now live below `/api/web/**` instead of `/api/**`.
  Two routes are intentionally **kept** under `/api/` for federation compatibility:
  `GET /api/activities/{id}` and `GET /api/activities/{id}/image`. The deprecated
  `GET /api/activities/{id}/track` endpoint was removed; the activity-detail map now reads
  its polyline from the `simplifiedTrack` geometry embedded in the activity DTO. Route
  downloads use `GET /api/web/activities/{id}/route?format=fit|gpx|tcx`.

  ⚠️ Note: the federation detail route `GET /api/activities/{id}` returns the deliberately
  minimal `PublishedActivityDTO` (activity type, title, description, times, distances,
  metrics, location, simplified track) **without any author fields** — it is meant for
  federation peers that resolve the author via the actor URI themselves. FP Client therefore
  fetches activity detail from `GET /api/web/activities/{id}` (full `ActivityDTO` with
  `username`/`displayName`/`avatarUrl`/`actorUri`); using the federation route here made the
  author card fall back to a generic "Athlete". The image route stays as-is.
* **Authentication is cookie-based, not bearer-token based.** Since FitPub 1.3 the JWT is delivered only as an `Set-Cookie: JWT_TOKEN=…` (HttpOnly) header on login / registration-verify / password-reset; the app reads it from that response and sends it back as a `Cookie` header on every subsequent request. `Authorization: Bearer <token>` is no longer accepted.
* **CSRF protection enforced.** Mutating requests (POST/PUT/PATCH/DELETE) require an
  `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie. The app primes the cookie with an
  anonymous `GET /api/web/auth/registration-status`, captures fresh tokens from each response,
  and echoes both on every mutating call.
* **Debug endpoints removed:** obsolete `/api/**` debug/admin endpoints were dropped; the app never used them.

### 1.3.9 — authentication-version claim (stale stored sessions rejected)

FitPub commit `cb6acc84` (#474, "authenticate users statelessly on every HTTP request") makes
the JWT carry an `authenticationVersion` claim that the server checks against the user's
`passwordHashVersion` on every request. `JwtTokenProvider.getClaims` now **rejects any token
without that claim** — so JWTs issued by earlier server versions are permanently invalid, and
the server answers `401` and clears its cookie.

The client's login flow is unaffected (the login contract is unchanged), but a stored token
from before the server update loops on "Unauthorized". The app therefore treats a `401` on a
request that carried the stored JWT as "this credential is permanently dead": the session
interceptor clears the stored session (same as a logout) so the app returns to the login
screen and a fresh login mints a valid token. This also covers expired fixed-lifetime JWTs
and instances that rotate their signing secret — previously those left the user stranded
on "Unauthorized" errors.

### 2.1 — Web Push subscription (Iteration 8h, optional feature)

The app's mailbox push rides the server's **pre-existing** Web Push API
(`social.fitpub.push.boundary.PushSubscriptionResource`); no server changes are involved:

* `GET /api/web/push/vapid-key` — availability probe. **503** with
  `{"error": "Push notifications are not configured"}` means `FITPUB_PUSH_ENABLED` is off or the
  VAPID keys are missing on that instance; the app treats the 503 as definitive ("push disabled
  here") and keeps its 30-minute notification poll as the delivery path. 200 answers with the VAPID
  public key (the app only needs the *availability*, never the key itself).
* `POST /api/web/push/subscribe` — `{endpoint, keys:{p256dh, auth}}` (base64url, no padding),
  authenticated by the session cookie + CSRF like every other mutating call. 503 with the same
  body when push is disabled; the app then unregisters the mailbox it had just minted.
* `DELETE /api/web/push/subscribe` — body `{endpoint}`. The server declares `@RequestBody`, so the
  app sends it via Retrofit's `@HTTP(method = "DELETE", hasBody = true)` (plain `@DELETE` cannot
  carry a body). 404 = endpoint already unknown, treated as unsubscribed.

No password or token ever leaves the device: the subscription publishes only the public half of a
keypair that can decrypt notification payloads, revocable with the `DELETE` above (plus automatic
expiry — the mailbox relay answers 410 after ~30 days without a fetch and the server then drops the
subscription via `WebPushService.deleteByEndpoint`).

The server now **enforces** text length limits it previously accepted silently — activity
title 200 chars, activity description 5000 chars, bio 500 chars, comments 5000 chars, display
name 100 chars, passwords 100 chars — returning HTTP 400 `BAD_REQUEST` on overflow. The
Android UI caps all of these inputs to match (`app/src/main/java/com/fpclient/android/util/TextLimits.kt`).

### 2.2 — Server-contract audit against `main` (2026-09-27)

The whole endpoint surface was re-checked against the server sources rather than against this
document. All 79 client endpoints exist, with two exceptions that were client bugs, both now
fixed:

* **The heatmap endpoint the app called does not exist.** `HeatmapResource` serves only
  `GET /api/web/heatmap/me` and `POST /api/web/heatmap/me/rebuild`; there is no per-user
  route. `UserRepository.heatmap()` nevertheless branched to `GET
  /api/web/heatmap/user/{username}`, and `ProfileScreen` always passed a non-blank username
  (its own name on your own profile), so the request 404'd on *every* profile and the heatmap
  card never appeared. The route is deleted and the heatmap is now requested only for the
  signed-in user.
* **The heatmap payload is GeoJSON, not `{points, bounds}`.** `HeatmapDataDTO` is a
  FeatureCollection: `features[].geometry.coordinates` is `[longitude, latitude]` and
  `features[].properties.intensity`, plus `maxIntensity` / `activityCount`. Because the old
  DTO's every field had a default, this failed *silently* — an empty list, no error. The wire
  types are mirrored in `HeatmapFeatureCollectionDto` and converted by `HeatmapMapper`
  (unit-tested, since a swapped axis would draw a plausible map in the wrong hemisphere).

Three field names had drifted from the server and were corrected (each showed permanently
empty values rather than an error):

| Client had | Server sends | Effect of the mismatch |
| --- | --- | --- |
| `BatchImportJobDto.successful` / `.failed` | `successCount` / `failedCount` | every import read "0 ok · 0 failed" |
| `BatchImportFileEntryDto.error` | `errorMessage` | per-file failure reason never shown |
| `ActivityDto.entryMethod` | `creationSource` | field permanently null |
| `NotificationTypes.FOLLOW_REQUEST_ACCEPTED` | `FOLLOW_ACCEPTED` | accepted-follow-request rows read "…interacted with you" |

`ActivityTypes` also gained `FISTBALL` (the server's enum has 20 values), and
`NotificationText` now phrases the four notification types the app had no wording for:
`MENTIONED_IN_COMMENT`, `DATA_EXPORT_READY`, `BATCH_IMPORT_COMPLETED`, `FEEDBACK_RECEIVED`
— the last three carry no actor, so they previously rendered as "Someone interacted with you".

Verified as still correct, no change needed: cookie/CSRF auth (`JWT_TOKEN` + `X-XSRF-TOKEN`,
and the `authenticationVersion` claim whose absence the client answers by clearing the
session on 401), the `{content, page}` envelope (`spring.data.web.pageable.serialization-mode:
via_dto`), every analytics DTO and enum, the reaction palette, and all `TextLimits` values.
The Retrofit `Json` uses `ignoreUnknownKeys = true`, so additive server fields stay harmless.

#### Known server-side constraints the app cannot work around

* **Cloudflare Turnstile.** When an instance enables Turnstile, `POST
  /api/web/auth/register/start` and `/register/resend` require a `turnstileToken` verified
  server-side (`TurnstileService.verify`). A native app cannot run the widget, so **in-app
  registration cannot succeed on a Turnstile-enabled instance** — users must register in a
  browser. Login, and every other feature, are unaffected.
* **Heatmap rate limit.** `GET /api/web/heatmap/me` is limited per user
  (`FITPUB_HEATMAP_RATE_LIMIT_*`, default 10 burst + 1 per 2 s) and answers 429 with
  `Retry-After`. The app requests it once per profile open, well inside the budget.
* **Optimistic locking on activity edits.** `PUT /api/web/activities/{id}` rejects a stale
  `expectedUpdatedAt` with 400 "This activity was changed in another session", and refuses a
  visibility change once the activity has federated. The app never sends
  `expectedUpdatedAt`, so it cannot hit the first; the second is a genuine server rule.

#### New server capabilities the app does not use yet

Not breakage — feature surface available if wanted: passkeys
(`/api/web/auth/passkeys/**`, `/api/web/users/me/passkeys/**`), Komoot import
(`/api/web/komoot-import/**`), user feedback (`/api/web/feedback`), e-mail change
(`POST|DELETE /api/web/users/me/email-change` — the status `GET` is already used), profile
previews (`/api/web/users/{username}/preview`, avatar gravatar preview), peaks
(`/api/web/users/{username}/peaks/**`, `/api/web/activities/user/{u}/peaks/{id}/tracks`),
activity trimming (`GET /api/web/activities/{id}/trim`), and data export
(`/settings/export/download`).

**Passkeys are deliberately *not* implemented, and are not an oversight.** The wire format is
standard WebAuthn JSON, but the server accepts exactly one origin — a hard-coded singleton
derived from `fitpub.base-url` (`PasskeyRelyingPartyConfig.passkeyAllowedOrigins`, no
environment override) — and a native Android ceremony reports
`android:apk-key-hash:<cert hash>` as its origin, so every assertion is rejected with a
neutral 401. Separately, Android passkeys require Credential Manager, whose passkey support
comes from Google Play services, which the F-Droid build deliberately does not ship. The
server-side prerequisite and the full, ordered implementation prompts are in `PLAN.md` →
"Passkey sign-in in the app".

The FitPub server is under active development; if you are running a newer or older
version and notice breakage, please file an issue. The client targets the REST API
as implemented by the `social.fitpub:fitpub` server artifact.


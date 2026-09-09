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
* **Authentication is cookie-based, not bearer-token based.** Since FitPub 1.3 the JWT is delivered only as an `Set-Cookie: JWT_TOKEN=…` (HttpOnly) header on login / registration-verify / password-reset; the app reads it from that response and sends it back as a `Cookie` header on every subsequent request. `Authorization: Bearer <token>` is no longer accepted.
* **CSRF protection enforced.** Mutating requests (POST/PUT/PATCH/DELETE) require an
  `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie. The app primes the cookie with an
  anonymous `GET /api/web/auth/registration-status`, captures fresh tokens from each response,
  and echoes both on every mutating call.
* **Debug endpoints removed:** obsolete `/api/**` debug/admin endpoints were dropped; the app never used them.

### Pre-1.3.4 behavior the app already adapted to

The server now **enforces** text length limits it previously accepted silently — activity
title 200 chars, activity description 5000 chars, bio 500 chars, comments 5000 chars, display
name 100 chars, passwords 100 chars — returning HTTP 400 `BAD_REQUEST` on overflow. The
Android UI caps all of these inputs to match (`app/src/main/java/com/fpclient/android/util/TextLimits.kt`).

The FitPub server is under active development; if you are running a newer or older
version and notice breakage, please file an issue. The client targets the REST API
as implemented by the `social.fitpub:fitpub` server artifact.


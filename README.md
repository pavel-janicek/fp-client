# FP Client — Unofficial FitPub for Android

> **⚠️ Unofficial client.** FP Client is a community-developed Android client for
> the [FitPub](https://codeberg.org/fitpub/fitpub) federated fitness platform.
> It is **not** an official FitPub product and is not endorsed by or affiliated
> with the FitPub project maintainers.

An Android client for FitPub — a federated, self-hosted
fitness activity sharing platform (think "Strava meets the Fediverse"). Track your
rides and runs, share them with followers on any FitPub instance, comment, react,
and follow other athletes.

Built with Kotlin and Jetpack Compose (Material 3).

## API compatibility

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

## Features

- **Auth** — connect to any FitPub instance, register (with email verification code),
  log in, reset password. The instance is shown on the login screen and can be
  changed at any time.
- **Guest mode** — skip account setup entirely ("Skip for now") or browse without signing in from the login screen; guests see the public timeline of the selected instance and can sign up later from Settings.
- **Timelines** — federated / public / personal feeds of activities.
- **Discover** — search and browse athletes across the instance, follow / unfollow.
- **Activities** — upload FIT / GPX / TCX files or create entries manually; view
  details with an OpenStreetMap track render (osmdroid), likes and comments.
- **Analytics** — dashboard, personal records, achievements, form status, weekly summaries.
- **Notifications** — reactions, comments, follows, with unread badge and mark-all-read.
- **Profile** — edit display name, bio, visibility, default timeline, units; privacy zones.

## Pointing the app at a self-hosted instance

On first launch the app shows a **server setup screen**. Enter the base URL of your
FitPub instance (e.g. `https://fitpub.example.com` or `http://192.168.1.10:8080`).
A scheme is added automatically if omitted (`https://` by default), and every API
request is routed to that host via an OkHttp interceptor — so a single install can
talk to any instance without rebuilding.

You are not locked in: the instance can be changed later from
**Settings → Instance → Change instance**, or directly from the login screen
(**Change instance** link under the host name). Switching instances signs you out,
since access tokens are per-instance.

Plain-HTTP LAN/dev URLs are allowed: cleartext traffic is enabled via
`app/src/main/res/xml/network_security_config.xml` and `usesCleartextTraffic` in
the manifest. If you only ever talk HTTPS, remove both to harden the app.

## Building

Requirements: JDK 17, Android SDK with platform 36 (Android 16).

```bash
./gradlew assembleDebug        # debug APK at app/build/outputs/apk/debug/
./gradlew installDebug         # install on a connected device/emulator
```

`local.properties` (git-ignored) should contain `sdk.dir=/path/to/android-sdk`.

## Architecture

- **UI** — Jetpack Compose + Navigation-Compose, MVVM with `androidx.ViewModel`s per screen.
- **DI** — hand-rolled object graph in `AppContainer` (no DI framework, kept deliberately light).
- **Data** — Retrofit + kotlinx-serialization against the FitPub REST API (`data/network/FitPubApi.kt`),
  repositories in `data/repository`, session persisted in DataStore (`data/session/SessionStore`).
- **Maps** — osmdroid with OSM tiles for GPS track rendering.

See `PLAN.md` for the current project status and roadmap.

## Built with AI

This project is developed with the help of an AI coding agent:
[Cline](https://github.com/cline/cline), powered by the **ox-alpha** large language
model. Code reviews, testing on device/emulator, and product decisions are done by
a human; implementation, refactoring, and documentation drafts are produced by the
agent in iterative sessions (see `PLAN.md` for the iteration plan).


## 📥 Installation (APK)
**1. Download the APK** from the [Assets](https://github.com/pavel-janicek/fitpub-android/releases) section below.  
**2. Enable “Install unknown apps”** for your browser or file manager (Settings → Security).  
**3. Open the downloaded APK** from your Downloads folder.  
**4. Confirm installation** and launch the app.

**⚠️ Install at your own risk.** No guarantees, no warranties, no crying if your phone decides to rebel.

## 📥 Installation (Google Play Store)
At this time, the application is in closed beta testing on Google Play Store. Here is how to obtain the app using the store:

1. Become member of [FP Client Google Group](https://groups.google.com/g/fp-client)
2. Click on the web [to become a tester](https://play.google.com/apps/testing/com.fpclient.android)
3. Download the app from [Play Store](https://play.google.com/store/apps/details?id=com.fpclient.android)
4. Use the app actively; it will help me make the app officially available to all Google Play users

## And what about other stores?
To be honest, I tried publishing the app to the [F-Droid](https://f-droid.org) app store.

... And I keep failing and failing.

So, [I need your help](https://www.pavelchcepsat.cz/2026/09/02/a-cry-for-help-with-f-droid/)

### Submitting issues
I am trying to make the [Issues](https://github.com/pavel-janicek/fp-client/issues) tab as single source of truth of known issues with this app.

Best is to check there and see if any open issue resembles yours. If none do, read next chaprer "Before submitting" to see what I would like you to do.

You can also reach me at [FitPub Uers Matrix channel](https://matrix.to/#/#fitpub-users:matrix.org)

### Before submitting issue
Please check, that your app is running at the latest release.

And until I reach at least one official approaval from some stores, also please try this on your phone: Settings - Appliicatons - FP Client - Delete data
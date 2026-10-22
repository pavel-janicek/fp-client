# FitPub Android — Project Roadmap

Assessment date: 2026-08-25 · Last updated: 4.0.0 (versionCode 63 / `:wear` 64)
Current app version: **`4.0.0`** (`versionCode` 63 / `:wear` 64)

## Current state

**Stack:** Kotlin 2.2.10 (built-in Kotlin compiler plugin via AGP) + Jetpack Compose (Material3, BOM 2024.09), Navigation-Compose,
Retrofit + kotlinx-serialization, OkHttp, DataStore Preferences, Coil, osmdroid.
AGP 9.4.1 / Gradle 9.6.0, compileSdk 36, targetSdk 36, minSdk 26 (Android 8.0).
Hand-rolled DI via `AppContainer`, MVVM (ViewModels + Repositories). Targets
self-hosted FitPub instances (dynamic base URL via interceptor).

**What exists and looks complete:** full auth flow (server setup, register +
verify code, login, password reset), timeline/discover/analytics/notifications
tabs, activity detail with osmdroid track map, comments & likes, activity
creation (file upload FIT/GPX/TCX + manual entry), profile + edit profile,
settings incl. change password and privacy zones CRUD. The API layer
(`FitPubApi.kt`, ~70 endpoints) is broad and mostly mirrored by repositories.

**Blocking problem: the project did not compile (resolved in 0.2.0).**
The initial assessment found `./gradlew assembleDebug` failing with **28 Kotlin errors
across 8 files**. Iteration 1 ("Make it compile") fixed all errors:

| File | Errors | Nature |
|---|---|---|
| `ui/discover/DiscoverTab.kt` | 15 | Missing imports (`LoadingIndicator`/`ErrorState`/`EmptyState` from CommonUi, `UserDto`, `UserAvatar`) + generic type mismatch at line 80 |
| `ui/notifications/NotificationsTab.kt` | 3 | Missing `Icon` import, `ArrowBack` needs `Icons.AutoMirrored.Filled.ArrowBack`, experimental M3 API needs OptIn |
| `ui/profile/EditProfileScreen.kt` | 2 | Missing `AppViewModel` import; smart-cast on delegated property |
| `ui/profile/ProfileScreen.kt` | 1 | Missing `androidx.lifecycle.viewmodel.compose.viewModel` import |
| `ui/create/ManualFormParts.kt` → `ManualForm.kt` | 4 | Helpers declared `private` but used from another file in same package |
| `ui/create/ManualFormParts.kt` | 1 | Smart cast impossible on delegated property (`error`) |
| `ui/components/CommonUi.kt` | 1 | Invalid Dp math: `Modifier.padding(size.dp.value / 4.dp)` |
| `ui/activity/ActivityDetailWidgets.kt` | 1 | `c.content` type mismatch (comment DTO field vs String) |

Other observations:
- Dead directories `ui/map/`, `ui/screens/` (empty leftovers).
- `.gradle/`, `app/build/`, `.kotlin/` artifacts are **committed to git**; no `.gitignore`.
- Tests now exist: JVM unit tests + Compose UI smoke tests + GitHub Actions CI (added in 0.5.0).
- README now exists (added in 0.2.0). No LICENSE or CI artifact signing yet.
- `ApiClient.isDebug` uses `BuildConfig.DEBUG` (the module sets `buildConfig = true`); the
  initial assessment's note about AGP 8 disabling buildConfig by default was resolved.
- Auth token stored unencrypted in DataStore (acknowledged in code comment).
- API endpoints defined but **not wired to any UI**: heatmap (own/user),
  batch import jobs, activity route download, activity image, profile header
  upload/delete, delete account, monthly & yearly summaries, training load,
  timezones. Routes `SEARCH` / `CREATE_MANUAL` / `ANALYTICS_DETAIL` defined but unused.

## Versioning policy

The roadmap is grouped into three release gates:

| Gate | Contents | Ships as |
|---|---|---|
| Core finalization | Iterations 1–6 | **v1.0** |
| On-device recording ("Record" feature) | Iteration 8 | **v2.0** |
| Wear OS companion app | Iteration 9 | **v3.0** |
| Garmin watch support (Connect IQ companion app) | Iteration 10 | **v4.0** |
| Multilingual support (community-driven translations) | Iteration 7 (deferred from v2.0, then from v4.0) | **v5.0** |

Pre-1.0 policy: each completed roadmap iteration bumps the app to
`0.<N>.0` (`versionName`) and increments `versionCode` by one, so every
shipped APK reflects real, verified progress. Feature work done outside
the numbered iterations (guest mode, instance switching from login/settings,
federated search, layout/inset fixes) folds into the next pre-release bump.
From v1.0 onward the gates above (v1.0, v2.0, v3.0, v4.0, v5.0) are the major milestones;
post-1.0 minor/patch releases (1.1, 1.1.1, 1.2, …) track incremental
hardening, renames, and platform-targeting work — recorded here and set
in `app/build.gradle.kts` at each release.

Release-process policy for v2.0: **no interim minor releases between gates.**
Work towards 2.0 proceeds one roadmap feature per iteration, landed directly on
the `Release-2.0` branch (the ledger below only records *shipped* versions —
in-flight 2.0 scaffolding is noted inside the 2.0 row, not as phantom 1.x
entries). After the last feature lands, `Release-2.0` enters a polish phase
(final UI pass, lint, docs, versionName/versionCode bump); while it is
polishing, any urgent fixes for the shipped 1.4 line are made on
`Release-1.4` and merged into `Release-2.0` when applicable. Everything
accumulated ships together as **v2.0**.

Progress ledger (kept up to date per iteration):

| Version | Milestone | Status |
|---|---|---|
| 0.1.0 | Initial assessment snapshot | ✅ superseded |
| 0.2.0 | Iterations 1+2 — compiles; repo/build hygiene, README | ✅ done |
| 0.4.0 | Iteration 4 — feature completion (heatmap, summaries + training load, batch import, profile header, delete account, route cleanup) | ✅ done |
| **0.4.1** | Patch — activity descriptions now shown on timeline cards (were dropped by ActivityCard) | ✅ done |
| **0.4.2** | Patch — web parity in Edit profile: avatar upload/remove, time-zone picker (bio & profile header already editable) | ✅ done |
| **0.4.3** | Patch — bio changes now visible on Me tab after saving (stale-cache fix); Analytics redesign: scrollable tab row + weekly-distance bar chart on Overview | ✅ done |
| **0.4.4** | Patch — activity speed shown 3.6× too high: API's `metrics.averageSpeed` is km/h (verified against live instance), Format now interprets it as such + imperial conversion fixed | ✅ done |
| **0.4.5** | Patch — following: author tap opens profile from timeline cards & activity detail header; Follow/Unfollow button on activity detail; unfollow-button stuck-gray fixed (busy flag never reset); clickable Followers/Following stat pills with new FollowListScreen (follow/unfollow inline); bio emoticons rendered (HTML-entity decode) | ✅ done |
| **0.4.6** | Patch — request-to-follow for private accounts: 403 "only visible to followers" profile errors detected → dedicated LockedProfileBody with 🔒 + "Request to follow" button; pending-request state shown as "Request sent" and tappable again to cancel; follow-status still fetched for locked profiles so button reflects server state | ✅ done |
| **0.4.7** | Patch — author card on activity detail: avatar + display name + @username in a tappable card at the top of the detail screen, with an always-visible Follow/Unfollow button (supporting request-to-follow for private accounts and no-cached-status first-time follow); fixes the 0.4.5 follow affordance that never rendered | ✅ done |
| **0.4.8** | Patch — walkthrough gap fixes: timeline pagination (Load-more appends next 20), follow-request Accept/Reject buttons on notifications (FOLLOW_REQUEST rows), edit + delete own activity from detail (title/description dialog + delete confirm), comment delete affordance on comments the user owns (`canDelete`) | ✅ done |
| **0.4.9** | Patch — activity detail clearly shows the author: avatar + display name + @handle + date in an author card at the top, the **entire card is now clickable** (avatar included) to open the author's profile, and a Follow/Unfollow button sits inline (request-to-follow + "Request sent" states included). Verified against live instance — API returns `username`/`displayName` for federated + local authors. Rebuild to pick up any stale pre-0.4.7 APK. | ✅ done |
| **0.5.0** | Iteration 5 — JVM utility/repository/ViewModel tests, Compose auth/timeline smoke tests, and GitHub Actions CI | ✅ done |
| **0.5.1** | Patch — activity detail resolves and displays nested or flat activity author identity | ✅ done |
| 0.6.0 | Iteration 6 — release hardening (token encryption, R8, signing) | ⬜ |
| **1.0** | All of the above → first stable release | ✅ done |
| **1.1** | Application renaming: rename to "FP Client", unofficial client README notice, API compatibility/version info, version bump | ✅ done |
| **1.1.1** | Patch — package rename from `com.fitpub.android` to `com.fpclient.android` | ✅ done |
| **1.2** | Target Android 16 (API 36): upgrade AGP 8.5.2→8.8.0, Kotlin 2.0.20→2.1.20, Gradle 8.9→8.12.1, compileSdk/targetSdk 35→36 | ✅ done |
| **1.2.1** | Patch — activity detail: `ActivityDetailViewModel.toggleFollow()` used `activity?.username` (nullable, often null for federated authors) instead of `activity?.resolvedUsername`, causing the Follow button to do nothing on activities where `username` wasn't directly populated | ✅ done |
| **1.2.2** | Patch — API compatibility check against latest FitPub (main, 1.3.0-SNAPSHOT, remote-boosts): endpoint surface + request/response shapes verified compatible; added UI input caps matching the server's newly enforced text limits (activity title 200, description 5000, bio 500, comment 5000, display name 100, password 100) in create/upload/edit-activity/edit-profile/register/comment/change-password forms via new `TextLimits` util | ✅ done |
| **1.2.3** | Patch — **editing an activity failed with 400**: the edit dialog sent only `title`/`description` to `PUT /api/activities/{id}`, but the server's `ActivityUpdateRequest.visibility` is `@NotNull` (and the client's JSON encoder omits nulls), so every save was rejected. `ActivityUpdateRequest.visibility` is now required (mirrors the server contract) and the detail screen preserves the activity's current visibility when saving title/description edits | ✅ done |
| **1.2.4** | Patch — **release-build registration failed** ("Unable to create converter for class java.lang.Object for method i.y"): R8 full mode (AGP 8.x + `proguard-android-optimize.txt`) strips generic signatures from non-kept classes, so Retrofit's suspend endpoints (`Continuation<Response<T>>`) resolved to `java.lang.Object` at runtime. Added Retrofit's documented R8 full-mode keep rules (`kotlin.coroutines.Continuation` + response-type `-if/-keep` rule). Also fixed the kotlinx.serialization keep rules, which pointed at the pre-rename package `com.fitpub.android.data.dto` (no-op since the 1.1.1 rename) and now target `com.fpclient.android` per the library's official rules | ✅ done |
| **1.3** | Feature — Settings gains an **About** screen: app version (from `BuildConfig`, cannot drift), contact via the FitPub Matrix room ("FitPub Users"), bug-reporting guidance via GitHub Issues (incl. "please include the app version"), info for instance administrators about the app's `FP-Client/<version>` HTTP User-Agent, project link and OpenStreetMap attribution. Added `VERSION_CHECKLIST.md` to verify version propagation on every release | ✅ done |
| **1.3.1** | F-Droid compatibility prep: AGPL-3.0 `LICENSE` added (was missing — hard F-Droid requirement), release binaries untracked from git + `app/release/` and `*.keystore` gitignored (source repo must be binary-free), release signing config made conditional so unsigned release builds succeed for the F-Droid buildserver (which re-signs with its own key), fastlane metadata (`fastlane/metadata/android/en-US/`: title, short/full description, changelogs) for F-Droid auto-import. Dependency audit clean: all libs are Apache-2.0/MIT from Maven Central/Google, no proprietary SDKs/services | ✅ done |
| **1.3.2** | F-Droid submission — reproducibility fix: the reference binary previously pinned to the pre-merge `Release-1.3` commit failed F-Droid's byte-compare (only `META-INF/version-control-info.textproto` differed). Cut as a fresh release built from the merged `main` commit so the uploaded `app-release.apk` matches the F-Droid buildserver output exactly. No user-facing changes. | ✅ done |
| **1.3.4** | **API compatibility update for FitPub `main` (post 1.3.2):** web/consumer endpoints migrated under `/api/web/` (auth, timeline, activities, likes/comments, users, analytics, notifications, privacy-zones, heatmap, batch-import, push `vapid-key`); the session JWT is now delivered and read only as the `JWT_TOKEN` HttpOnly cookie — `Authorization: Bearer <token>` is no longer accepted, so the app now authenticates via cookie + CSRF instead of a bearer header; CSRF protection now enforced on every mutating call (`X-XSRF-TOKEN` header + `XSRF-TOKEN` cookie, primed via a registration-status GET). Removed the deprecated `GET /api/activities/{id}/track` endpoint; the activity-detail map now sources its polyline segments from the `simplifiedTrack` field embedded in `ActivityDTO`. Published federation routes (`GET /api/activities/{id}`, `GET /api/activities/{id}/image`) are preserved. Bump versionCode 27 / versionName 1.3.4; `FP-Client/1.3.4` user-agent. | ✅ done |
| **1.3.5** | **GUI overhaul:** "Change instance" promoted to a full `OutlinedButton` on the login screen for better visibility; comment composer on the activity-detail screen no longer hidden by the soft keyboard (body uses `imePadding()` + the composer's text field reports focus so the `LazyColumn` scrolls it into view); timeline feed switches (Following / Public / My activities) restyled as a floating `ScrollableTabRow` matching the analytics tab; usernames across timeline cards and follow lists now carry the full federated `@username@instance` handle (stored in DTOs via `ActorHandle.full`) so remote actors keep their home instance; activity-detail author card opens a profile search by the full handle; timeline reload button wired to `refresh()` plus pull-to-refresh gesture via `PullToRefreshBox`. Bump versionCode 28 / versionName 1.3.5; `FP-Client/1.3.5` user-agent. | ✅ done |
| **1.3.6** | **Usability & F-Droid readiness:** all mutating API calls fixed against `403 Forbidden` when the CSRF token went stale — the session interceptor now primes the token from the public `/login` page, caches it per instance and retries a rejected call once with a fresh token; manual entry's date field is a Material3 date picker instead of a typed `YYYY-MM-DD` string; show/hide password toggles on the login screen, and the change-password dialog fields are now masked (they were plain text) with the same toggles; file upload button shows the picked file's real display name (via `OpenableColumns.DISPLAY_NAME`) instead of a cryptic content-URI id like `msf:2323`; the app registers for `ACTION_SEND`/`ACTION_VIEW` of GPX/FIT track files so sharing a file from a file manager opens the upload form with the file pre-selected; timeline search moved from a permanent input field to a toggleable icon left of the reload button (with a working IME "Search" action) and the doubled bottom inset on the timeline tab is fixed. `VERSION_CHECKLIST.md` extended with the F-Droid/fastlane procedures (per-versionCode changelog, unsigned-build check). Bump versionCode 29 / versionName 1.3.6; `FP-Client/1.3.6` user-agent. | ✅ done |
| **1.3.7** | **Pace & boosts:** timeline cards now show a pace for activities where the timeline API returns no stored `averagePaceSeconds` (notably Trek) — the client derives it from `averageSpeed`, or from duration over distance, mirroring how the web UI computes it, so the timeline matches the activity-detail screen; activity detail gains a **Boosts** section listing who boosted the activity (avatar, display name, full `@user@host` handle, relative time, tap to open the profile) with a Boost/Boosted chip when the signed-in user is eligible, wired to the new `GET/POST/DELETE api/web/activities/{id}/boosts` endpoints; `ActivityDto` parses the server's boost enrichment (`boostsCount`, `boostedByCurrentUser`, `boostEligible`). README rewritten short-and-sweet (what it does / where to get it / contributing), with the API-compatibility notes moved to `docs/API-COMPATIBILITY.md`. Bump versionCode 30 / versionName 1.3.7; `FP-Client/1.3.7` user-agent. | ✅ done |
| **1.3.8** | **Update check, unit fix, bigger map:** the **Check for updates** card now lives directly in Settings (previously only on the About screen) — extracted into a shared composable both entry points use; fixed the unit-system switch silently reverting: the manual metric/imperial choice was overwritten by the server-profile value on every profile load (e.g. pressing Back from Settings) and never persisted, so it now survives leaving Settings and app restarts (device-level DataStore preference outranks the server value, which only seeds the default; saving Edit Profile re-applies the saved unit); activity detail's track map gains an **Enlarge map** button opening the route full-screen; README's issue-filing checklist explains how to check for updates first. Bump versionCode 31 / versionName 1.3.8; `FP-Client/1.3.8` user-agent. | ✅ done |
| **1.3.9** | **Share & route download:** activity detail gains a **Share** button in the top bar that opens the Android share sheet with the text "I just finished: {activity} check it out at: {instance}/activities/{id}" (title falls back to the capitalized activity type; link built via new `ShareLinks` helper from the configured instance URL); a **Download** button saves the route as a GPX file through the system file picker (`CreateDocument`, `application/gpx+xml`, suggested name `fitpub-route-{id}.gpx`), wired to the existing `GET /api/web/activities/{id}/route?format=gpx` endpoint with friendly error mapping (401/403/404/422); the enlarged full-screen map now closes via the same translucent top-right button that opened it (system Back still works) instead of a separate header row. Bump versionCode 32 / versionName 1.3.9; `FP-Client/1.3.9` user-agent. | ✅ done |
| **1.4.0** | **Self-healing sessions:** realigned with FitPub `main` after commit `cb6acc84` (#474) made the JWT carry a mandatory `authenticationVersion` claim — the server now rejects (401 + cookie clear) any token issued before that change, so existing app installs looped on "Unauthorized". The session interceptor now treats a `401` on a request that carried the stored JWT as a permanently dead credential and clears the stored session (as a logout does), so the app returns to the login screen and a fresh sign-in mints a valid token; guests and failed logins are unaffected. This also covers expired fixed-lifetime JWTs and instances rotating their signing secret, which previously stranded users on "Unauthorized" the same way. `docs/API-COMPATIBILITY.md` documents the server change. Bump versionCode 33 / versionName 1.4.0; `FP-Client/1.4.0` user-agent. | ✅ done |
| **1.4.1** | **Follow / Request-to-follow fixes:** the Follow button "did nothing" because the client's `FollowStatusDto` expected `canUnfollow`/`isFollowRequestPending` flags that the server's `GET /{username}/follow-status` never sends (it answers `{"isFollowing", "status": "NONE|PENDING|ACCEPTED|REJECTED"}`) — the dropped `status` string made `toggleFollow` compute "already following" for complete strangers and send the **unfollow** call (400 "Not following this user"), with the error invisible on the profile body. The DTO now carries `status` plus derived `isAccepted`/`isPending` flags every follow button branches on, and follow errors are surfaced under the profile Follow button. "Request to follow" failed with "user not found" for same-instance users reached via their full `@user@host` handle (follower lists always navigate with the full handle): the server classifies any `user@host` path segment as federated and routes it into WebFinger discovery instead of the local follow. New `ActorHandle.normalizeToUsername()` collapses same-instance handles to the plain local username before follow/unfollow/follow-status calls (genuinely remote handles pass through), `discover-remote`'s `local: true` is honored as authoritative locality, and the ProfileScreen buttons no longer stay permanently disabled when a follow-status fetch fails. Unit tests pin the DTO parsing against the real server payload and the handle normalization. | ✅ done |
| **2.0.0-alpha** | **Recording polish:** active workouts now surface GPS-off, prolonged no-fix, and low-storage warnings while recording; the release also includes the 8e battery guidance, storage safeguards, and discard confirmations. Bump versionCode 34 / versionName 2.0.0-alpha; `FP-Client/2.0.0-alpha` user-agent. | ✅ done |
| **2.0.0-beta** | Version promotion alpha → beta on the road to 2.0: versionCode 35 / versionName 2.0.0-beta; `FP-Client/2.0.0-beta` User-Agent. The GitHub-release update checker now orders pre-release suffixes semver-style (alpha < beta < rc1…rcN < final) instead of folding them into extra numeric components — previously a running `2.0.0-alpha` was never prompted to update when the final `2.0.0` shipped, and `1.3.6-rc2` was (wrongly) ranked newer than `1.3.6`. Activity detail switched from the author-less federation route `GET /api/activities/{id}` (`PublishedActivityDTO` has no author fields, so the author card always fell back to "Athlete") to the full `GET /api/web/activities/{id}` `ActivityDTO`, restoring the author's name, handle and avatar on the detail screen. Privacy zones overhauled: creating and editing now happens on a full-screen map editor (new `PrivacyZoneEditScreen` via `privacy_zone_edit` route) — a minimap circle whose radius is set by a slider (server contract 50–10000 m, enforced), the zone center is always the map's screen center (drag to place), a locate button centers on the device's last known GPS fix (framework LocationManager only, FINE/COARSE permission), and existing zones open in edit mode from the list. The update request now sends name + latitude + longitude + radius (the server's `UpdatePrivacyZoneRequest` declares all four `@NotNull`; the previous name+radius-only body was rejected with 400). The old lat/lon text-field dialog is removed. | ✅ done |
| **2.0.0** | **First stable 2.0 release** — versionCode 36 / versionName `2.0.0`; `FP-Client/2.0.0` User-Agent. No 2.0 tag had ever been published, so this release closes the pre-release line (the 2.0.0-alpha/beta work is all included here): the unsuffixed tag now ranks above every suffixed label in `UpdateVersions` — `2.0.0` > `2.0.0-final` > `2.0.0-rcN` > `2.0.0-beta` > `2.0.0-alpha` — so any pre-release install is offered the final update, while a released build is never offered a `-final` label as an update. Ships the on-device recording engine + share-to-FitPub flow, the recording-polish warnings, the activity-detail author fix and the privacy-zone map editor. | 🔄 in progress |
| **2.0.1** | **Federation gap + recording fixes** — versionCode 37 / versionName `2.0.1`; `FP-Client/2.0.1` User-Agent. Remote (federated) timeline activities now open on their origin server in a Chrome Custom Tab instead of the in-app detail (which 404s because only a metadata mirror exists locally) — web parity with `timeline.js`'s "View on Origin Server": `TimelineActivityDto` parses the `activityUri` the server already sends, remote cards carry a "Remote" badge, and the in-app "not accessible yet" screen remains the fallback for old payloads / no browser. Privacy-zone enable/disable works again: `PATCH /api/web/privacy-zones/{id}/toggle` now sends the required `{"isActive": bool}` body, the DTO matches the server's `isActive` field, and the switch forwards the user's chosen state; toggle/delete failures surface in the UI instead of silently doing nothing. Recording: the pre-start mini-map no longer centers on a stale cached position from a previous recording (cached fixes older than 60 s are rejected, so a new town shows "Searching for GPS…" until a real fix arrives) and the live/pre-start map keeps following every fresh fix — following is now only disabled by actual user pans, not by the map's own programmatic camera moves. | ✅ done |
| **2.0.2** | **Large-screen support** — versionCode 38 / versionName `2.0.2`; `FP-Client/2.0.2` User-Agent. All size/orientation restrictions removed ahead of Android 16 ignoring them on large screens (foldables, tablets): every Compose dialog now passes `DialogProperties(usePlatformDefaultWidth = false)` so the dialog window resizes with the screen (Material3 still caps content at 560dp, so phone layouts are unchanged), and the portrait lock while recording was dropped — the recording layout (timer + controls + mini-map column) adapts to landscape/split-screen. Toolchain modernized in the same cut: AGP 8.8.0 → 9.4.1 with Gradle 9.6.0 and built-in Kotlin compilation (the `org.jetbrains.kotlin.android` plugin is gone), R8 resource shrinking now runs as part of R8 with precise shrinking. | ✅ done |
| **2.0** | + Iteration 8 — record workouts on-device and share (community-driven translations, originally Iteration 7 for 2.0, are deferred to **4.0** and ship as the very last thing — release decision 2026-09-20; moved again to **5.0** on 2026-10-14). **Groundwork already landed on `feature/tracking-engine`**: (Iteration 8a/8b) `TrackRecordingService` records real GPS fixes with the framework `LocationManager` (no Play services, per project policy), `requestLocationUpdates(GPS_PROVIDER, 2 s, 2 m)`, fixes with accuracy > 20 m discarded, GPS detached while paused; every accepted fix is append-flushed to `files/recordings/track-<sessionStart>.jsonl` (single source of truth — live stats rebuilt by replaying the file on START_STICKY restore); live state via `TrackRecordingBus` (session + stats + points flows; distance via haversine, elevation gain with 3-fix smoothing + 3 m hysteresis, point count, derived pace). (Iteration 8c) the full Record flow UI: Route RECORD with pre-start activity-type picker (ActivityTypes), live recording screen (big elapsed timer, distance/pace/elevation, pause/resume, stop with confirmation, keep-screen-on toggle), osmdroid mini-map with live position dot + polyline (pan stops auto-follow, recenter button), Record entry next to the + on Timeline (stacked small FAB) and Me tab, app-wide "recording in progress" banner above every tab with quick pause/resume, and a second-session guard (pre-start only exists while idle; service no-ops a duplicate start); the chosen activity type is persisted with the session snapshot (pre-8c sessions restore with the default). Unit tests pin the bus/point accumulation, the activity-type persistence round trip, and the haversine/elevation/pace math; instrumented smoke tests cover pre-start, live screen, and banner. (Iteration 8d) on stop the session is assembled into a GPX 1.1 file (`GpxBuilder`: trk/trkseg/trkpt with ele + time, one trkseg per paused segment via `#PAUSE`/`#RESUME` markers in the track file, strict UTC second-precision timestamps) in app-private storage and registered in a file-backed pending-upload registry (`pending_uploads.json`); the post-workout summary route (`workout_summary/{sessionId}`) shows stats + mini-map with title/description/visibility/activity-type pickers, uploads via the same multipart endpoint as the upload form (`POST api/web/activities/upload`, activity type reconciled with a follow-up metadata update), persists the entered metadata on failure for retries (automatic retry pass on app start + manual retry from the Record screen's "waiting to be shared" list), cleans up track + GPX files after a successful import, and offers navigation to the created ActivityDetail; privacy zones are applied server-side on import exactly as for any uploaded track. Unit tests pin the GPX document shape and the pending-store round trip. Still pending after this cut: push sub-steps 8f–8i (universal push notifications) and the 2.0 polish pass — see the 2.1 status row. | 🔄 in progress — 8d done |
| **2.1** | **Background notification delivery (Iteration 8f) + the Analytics redesign + a server-contract audit** — versionCode 39 / versionName `2.1.0`; `FP-Client/2.1.0` User-Agent. In-app notifications are now delivered while the app is closed, against **any** FitPub instance and with no extra infrastructure: a WorkManager periodic worker (unique work `fitpub_notification_poll`, 30-minute interval — WorkManager's floor is 15 — `NetworkType.CONNECTED`, exponential backoff, `ExistingPeriodicWorkPolicy.KEEP`) polls the existing `GET api/web/notifications` page 0 via `NotificationRepository` for signed-in sessions only (guests and signed-out users are skipped) and posts a **local** Android notification for new ACTIVITY_LIKED / ACTIVITY_COMMENTED (+COMMENT_ADDED, which the server has also used) / ACTIVITY_SHARED / USER_FOLLOWED / FOLLOW_REQUEST rows. The last-seen notification id is tracked in DataStore together with the owning `serverUrl\|username` and a last-poll timestamp, so the cursor is per session: switching accounts neither replays the previous account's rows nor swallows the new one's first page, and a first-ever poll adopts the page as seen instead of dumping the backlog as a burst. New rows are diffed relative to the cursor (deliverable types, unread, identified, deduplicated) and a burst is coalesced into a **single** summary notification carrying the unread count ("N unread notifications" + the newest row + "and M more"); a lone new row is announced with its own wording. Delivery goes through the new `fitpub_push` channel ("FitPub activity", IMPORTANCE_DEFAULT) so the feature has its own OS-level toggle, separate from the ongoing `track_recording` notification; opening the notifications tab clears the summary. Settings gains a **Push** section that owns the POST_NOTIFICATIONS runtime gate modeled on `LocationPermissionGate` (in-app rationale → system dialog → system-settings fallback, asked once, never at app start) and states the delivery model honestly — "eventual, not instant", a last-checked timestamp, and a button to re-arm the schedule. A 401 keeps the existing session-clearing semantics and is never retried (no "Unauthorized" loop); transport and 5xx failures are. The notification wording was extracted into a shared Android-free `NotificationText` object so the list row and the push notification are phrased identically. Unit tests pin the new-row diffing, the summary coalescing and the retry policy. New dependency: `androidx.work:work-runtime-ktx:2.10.5` (the only one the whole push feature adds). **Also in this cut:** the Analytics redesign (permanent explained **Load** tab, achievements on the overview, a real Personal-records screen grouped by activity type, human-readable period headings, consistent Load/Overview spacing — `StatRow`'s `SpaceEvenly`-with-`weight` combination was consuming all available space and jamming tiles against the screen edge), the fix for stopped-but-never-shared recordings being published automatically (`PendingUpload.requestedByUser` now separates a user-requested upload from a merely recorded session, while a failed explicit share stays retryable), and a full audit of the client's API surface against the server sources that found and fixed two real breakages: the heatmap called a per-user route the server does not have (`GET /api/web/heatmap/user/{username}` → 404 on *every* profile, the signed-in user's included) and decoded a GeoJSON FeatureCollection into a bespoke `{points, bounds}` model, which failed silently to an empty map; plus four drifted field names (`BatchImportJobDto.successful`/`.failed` → `successCount`/`failedCount`, `BatchImportFileEntryDto.error` → `errorMessage`, `ActivityDto.entryMethod` → `creationSource`, `FOLLOW_REQUEST_ACCEPTED` → the server's `FOLLOW_ACCEPTED`), the missing `FISTBALL` activity type, and wording for four notification types the app had none for. ⚠️ **The published 2.1.0 build could not start at all** — see the 2.1.1 row; the tag is on origin and stays there. | ✅ done |
| **2.1 (cont.)** | **In-app ntfy receiver (Iteration 8j)** — the "install the ntfy app" step of 8i is gone: FP Client subscribes to the private ntfy topic itself, so instant delivery needs no second app and the topic is never copied anywhere. `InstantDeliveryService` is a foreground service (plain OkHttp NDJSON over `GET {server}/{topic}/json`, `readTimeout` shorter than ntfy's keepalive so a silent connection reconnects instead of lingering, exponential backoff with full jitter, `poll_request` answered with an immediate `?poll=1&since=<lastId>` gap replay, dedupe by ntfy message id, work on a scope cancelled in `onDestroy`) declared `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` because `dataSync` is time-capped on API 35+; its own `fitpub_instant_delivery` notification channel keeps the OS-level toggles independent. It connects only while a session is signed in and owns an `instantEnabled` `PushSubscription` — guest, signed-out and another account's subscription are unit-tested no-ops. **The relay stopped decrypting:** it republishes the untouched RFC 8291 blob as base64, `crypto.go` is deleted, `PUT /push/<id>/forward` still accepts an 8i phone's `keys` object for one release and discards it unread, and `PushRepository.enableInstant` no longer uploads `privkey` — so instant delivery moves exactly as much off the phone as mailbox push, with the same 15-minute `fitpub_push_mailbox_check` fallback. The relay now also **enqueues the ciphertext even when a forward succeeds**, which is what makes that check a real backstop, and publishes the payload tag as a second ntfy `tags` entry so notifications keep collapsing per event type. Settings → Push shows the live socket state (connecting / connected since + last notification / retrying with the reason), a start/stop control and a battery-exemption row modeled on `RecordingHealthCard`, instead of instructions to install and configure another app. No new dependency, no Firebase, nothing proprietary. | ✅ done |
| **2.1.1** | **Emergency fix for the 2.1.0 startup crash** — versionCode 40 / versionName `2.1.1`; `FP-Client/2.1.1` User-Agent. 2.1.0 shipped an app that **crashed on every launch** and could not be opened at all: `java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider … NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>()`, thrown from `handleBindApplication` before any Activity exists, so the process died instantly and the launcher retried in a loop. Root cause: the 8f background-notification feature added exactly one dependency, `androidx.work:work-runtime-ktx:2.10.5` (confirmed by diffing the dependency lists of `Release_2.0.2` and `Release_2.1.0`), and WorkManager keeps its schedules in a Room database that it instantiates **reflectively** during androidx.startup init. R8 never sees that constructor being called, so it stripped it, and no `RoomDatabase` keep rule existed — `proguard-rules.pro` had rules for kotlinx.serialization, Retrofit, OkHttp, osmdroid and Coil, but Room arrived with WorkManager and nothing was added for it. Debug builds were unaffected because R8 does not run there, which is why it only ever appeared in the release/F-Droid build. Fix is one rule, `-keep class * extends androidx.room.RoomDatabase { <init>(); }` (plus `-dontwarn androidx.room.paging.**`), verified in R8's own `mapping.txt` rather than assumed: `WorkDatabase_Impl` is now name-stable and its no-arg constructor is retained. Scope check confirmed 2.0.0/2.0.1/2.0.2 are all safe — none of them depend on WorkManager, and a release build of `Release_2.0.1` contains zero `androidx.room.*` and zero `androidx.work.*` classes, so the failure cannot occur there; its three remaining `androidx.startup` initializers (EmojiCompat, ProcessLifecycle, ProfileInstaller) all survive R8 with stable names. 2.1.1 is otherwise identical to 2.1.0. | ✅ done |
| **2.2.0** | **Feature release on `Release-2.2`** — versionCode 41 / versionName `2.2.0`; `FP-Client/2.2.0` User-Agent. Ships: **Komoot import** (Settings → Data → "Import from Komoot"; `POST /api/web/komoot-import/activities` + `/import`, opt-in per instance via `fitpub.komoot.enabled` — disabled answers 404 which the app shows as "Komoot support is not enabled on this instance"; credentials used for the one request and never stored server-side or locally, `KomootImport` keeps only ids + timestamp); **Feedback** (Me tab → "Send feedback", `POST /api/web/feedback`, sign-in only per the server's security config, `topic`/`message`/`replyAllowed` — the last being a privacy switch: the server only exposes a `mailto:` reply address when granted); **E-mail change** (Settings → Account → "Change email address", two-address handshake: `POST /email-change` → 202, code goes to both addresses, `POST /verify`; server stores `oldEmail`/`newEmail` + 24h expiry); **Profile & Gravatar preview** — `GET /api/web/users/{username}/preview` (`UserPreviewDTO`, "available without full profile access") makes the locked/followers-only profile card show the real name and avatar instead of just a padlock + handle, and `GET /api/web/users/me/avatar/gravatar-preview` (bytes) previews in Edit profile the Gravatar the instance would fall back to if the uploaded picture were removed; **Peaks** (profile's "Summits reached" section + full `peaks` list/detail screens from `GET /api/web/users/{username}/peaks` and `…/peaks/{peakId}`); **Data export** (Settings → Data → "Export my data": `POST /api/web/users/me/export` → 202 `EXPORT_READY` notification, `GET …/export/status`, `…/export/download` streams the ZIP with the session cookie); **Activity trimming** (`ActivityTrimScreen` from the activity-detail scissors button, `isTrimmable` gate, `POST /api/web/activities/{id}/trim` `{start,end}` → updated `ActivityDTO`; slider edits debounce 150 ms into a preview recomputation ported from the server's own rules — `TrimPreviewTest` pins it); **Me-tab heatmap enlarge** (the "Enlarge heatmap" button opens the profile heatmap full-screen, close closes it); and the **encrypted-prefs restored-keyset crash guard** (backed-up-but-undecryptable Android Keystore keysets now fall back instead of crash-looping — `EncryptedPrefs` catch-and-reset + `backup_rules.xml`/`data_extraction_rules.xml`). External F-Droid metadata (`fdroid-data` `Builds:` entry) updated after tagging per `RELEASE_CHECKS.md` §6. | ✅ done |
| **2.2.1** | **Post-release fixes on `Release-2.2`** — versionCode 42 / versionName `2.2.1`; `FP-Client/2.2.1` User-Agent. **Register-screen polish** (the create-account view shows which instance you are joining with a "Change instance" button, gates the submit button on a real e-mail format instead of any free text, and is vertically centred). **Follow-request dismissal** on the Activity tab — the server never deletes a `FOLLOW_REQUEST` notification, it only flips `followRequestPending`; the row now honours that flag (buttons disappear once handled anywhere), a decision made in this session shows an "Accepted"/"Rejected" label through the refresh, in-flight buttons disable, and failures surface inline instead of the fire-and-forget call swallowing them — mirroring the web UI. **Push-badge clear** on "Mark all read" — launcher icon badges count the app's active shade notifications, not the server-side unread state, so the button now also sweeps every `fitpub_push` notification (`PushNotifications.cancelAll` filters on the channel, leaving the ongoing track-recording and instant-delivery notifications untouched). **Upload-screen placeholder fix** — the "+" New activity button navigated to the raw `create?sharedUri={sharedUri}` route pattern, so Navigation passed the unfilled `{sharedUri}` placeholder in as the shared-file argument: the file button displayed it and Upload was enabled for a bogus URI. In-app opens now go to the plain `create` route (`Routes.create()`), and the argument is only honoured when it parses to a scheme-bearing URI (`SharedUriArg.isFileUri` — a scheme-less value means "no file chosen"). External F-Droid metadata (`fdroid-data` `Builds:` entry) updated after tagging per `RELEASE_CHECKS.md` §6. | ✅ done |
| **3.0.0-alpha** | **Wear OS companion alpha (Iteration 9 in progress)** — versionCode 43 / versionName `3.0.0-alpha`; `FP-Client/3.0.0-alpha` (+ `FP-Client-Wear/3.0.0-alpha`) User-Agent. First paired phone + watch cut: `:wear` standalone module (round-safe Home/About, sign-in relayed from the phone over the Data Layer, on-watch GPS/HR/steps recording with phone-relayed sync & share). Phone and watch share `versionCode`/`versionName` and must be installed as a matched, same-signature pair for the handshake to work. | 🔄 in progress |
| **3.0.0-beta** | **Wear UX + connection beta (Iteration 9)** — versionCode 44 / versionName `3.0.0-beta`; `FP-Client/3.0.0-beta` (+ `FP-Client-Wear/3.0.0-beta`) User-Agent. Watch main screen redesigned: compact "FP Wear" title, dedicated Workout and Settings buttons in a scrollable `ScalingLazyColumn` so they stay reachable on small round displays, sign-in reduced to one tappable status line (version moved off Home). New watch Settings screen (replaces About) carries the version and a "Grant sensor access" button for the GPS/HR/step runtime permissions. Watch workout flow gains a dedicated activity picker (`ActivitySelectionScreen` + `WorkoutActivityUsageStore`) with a reworked `WorkoutControlScreen`. Phone ↔ watch sign-in handshake fixed and instrumented: `PhoneHandshakeDiagnostics` persists the last request/reply, surfaced under Settings → Handshake diagnostics with a manual reply test. PLAN (recorded 2026-10-26): on watch Stop, keep ✓ + beep + vibration, then after a short delay that lets them land, pop the workout window back to Home — firing only on the RECORDING/PAUSED → STOPPED transition so a persisted STOPPED state cannot bounce-loop on re-entry; watch auto-share default visibility follows the phone app's upload default (Public), replacing the hardcoded Private in stop queueing, pending entries, uploader and update-path fallbacks — not the unused server `defaultActivityPrivacyPreferences` blob (no in-repo consumption contract). | ✅ done |
| **3.0.0-rc1** | **Release candidate 1 for 3.0.0** — versionCode 45 / versionName `3.0.0-rc1`; `FP-Client/3.0.0-rc1` (+ `FP-Client-Wear/3.0.0-rc1`) User-Agent. Release candidate 1 of the 3.0.0 line following 3.0.0-alpha and 3.0.0-beta. | ✅ done |
| **3.0.0** | **Final 3.0.0 release of the Wear OS companion (Iteration 9)** — versionCode 46 / versionName `3.0.0` (`:wear` versionCode 47, globally unique per Google Play); `FP-Client/3.0.0` (+ `FP-Client-Wear/3.0.0`) User-Agent. Final release of the paired phone + watch cut following 3.0.0-alpha / -beta / -rc1: on-watch workout recording (GPS / heart rate / steps) with phone-relayed sign-in and sync & share to the FitPub instance. Ships the heart-rate display fixes from rc1 hardening — a live, colour-zoned BPM readout that now works for **every** workout type (Run, Walk, Hike, Bike, Other) instead of Run only, driven by a Health Services exercise chosen to always be heart-rate-capable, plus clearer "NO CONTACT" / "NO SIGNAL" status and on-watch pause/resume. | ✅ done |
| **3.1.0** | **Calories, steps and heart-rate reliability for watch workouts (Iteration 9h–9j)** — versionCode 53 / versionName `3.1.0` (`:wear` versionCode 54 / `3.1.0`); `FP-Client/3.1.0` (+ `FP-Client-Wear/3.1.0`) User-Agent. Codes chosen above every versionCode either module ever used: after 3.0.0's 46/47 and the 48/49 re-cut, further 3.0.0 re-uploads moved `:wear` to 50 → 51 and `:app` to 52, so 53/54 restore the "watch = phone + 1" convention. **9h** opt-in calorie estimate stamped into the FIT `totalCalories` on both upload paths, with the body profile sent to the watch as a Wear message (the DataItem mirror never arrived). **9i** live BPM no longer needs an app switch: the HR watchdog stops interrupting the ~10 s PPG warm-up (30 s stall window, any callback counts as alive), a stale ambient flag no longer throttles the readout, one PPG listener instead of two duplicates — still `BODY_SENSORS` only, no `HEART_RATE`. **9j** live steps from step counter + detector (larger of the two), FIT `totalStrides` so FitPub ingests `totalSteps`, the phone relay always uploads the FIT, and a Steps row on the phone's activity detail (a deliberate deviation from web parity). No server change. | ✅ done |
| **3.2.0** | **ZIP / Garmin "Export Original" upload, watch display and FIT fixes, rejected-upload handling** — versionCode 55 / versionName `3.2.0` (`:wear` versionCode 56 / `3.2.0`); `FP-Client/3.2.0` (+ `FP-Client-Wear/3.2.0`) User-Agent. A minor (not 3.1.1) because it adds a feature; 55/56 sit above every code either module ever used (highest before: `:wear` 54). **Phone:** a ZIP can be uploaded like a FIT/GPX/TCX file — via the upload form, share sheet or "Open with" — so Garmin Connect's web *Export Original* download uploads directly (`ActivityArchive`): ZIP recognised by content, exactly one FIT/GPX/TCX is extracted and uploaded, other files ignored; a ZIP with none, several, only nested ZIPs, an oversized entry or a corrupt archive is rejected on the device with a message and the selection cleared; the ZIP is always discarded. This eases manual uploads for Garmin owners — it is **not** a Garmin connection (no account link, no sync; that stays v4.0, Iteration 10 — see README → "Recorded on a Garmin?"). Uploads the server refuses for the file itself (permanent 4xx, `isPermanentUploadRejection`) no longer block the phone's pending-workout queue nor get resent by the 15-minute watch-inbox worker; a manual retry/sync still tries again. **Watch:** FIT records are one per GPS fix carrying the nearest heart rate, so FitPub now computes elevation gain (was `null`: HR-/steps-only records split every GPS point into its own elevation segment — makni.cz 82bb40f7, verified with the server's `FitParser`) and track points carry HR; the dimmed (ambient) workout screen shows only the time and "> WALK" / "WALK · PAUSED" (it used to freeze on "--" or a stale BPM — ambient frames only refresh on ambient ticks), waking it shows live data; a BPM older than 15 s is shown as "--". No server change. | ✅ done |
| **3.3.0** | **Watch tile, live heart rate, cadence and named watch workouts; profile header, heatmap and Zwift on the phone** — versionCode 57 / versionName `3.3.0` (`:wear` versionCode 58 / `3.3.0`); `FP-Client/3.3.0` (+ `FP-Client-Wear/3.3.0`) User-Agent. A minor (not 3.2.1) because it adds features; 57/58 because 56 is already the 3.2.0 `:wear` code. **Watch:** "FP Wear" quick-start tile (start the last-used activity or pick one; while recording it shows the activity and moving time and opens the workout); the workout screen's BPM is live and coloured by heart-rate zone (it was frozen at the snapshot it opened with), the PPG is pre-warmed while the app is open so the first BPM comes sooner, and live updates are published once a second (median frame 200 ms → 29 ms); the screen is top-anchored so distance, pace and steps are no longer below the fold (makni.cz e4ac4998); FIT files carry step cadence — per record over a 20 s window that never spans a pause, plus lap/session average (over moving time) and maximum, skipped for cycling (FitPub doubles it to steps per minute for foot sports); `file_id` names the app (`productName` "FP Wear"); uploads go out untitled with no GPX `<name>`, so FitPub names them from the start time ("Morning Walk") exactly like phone recordings instead of "Walk workout" (indoor workouts without GPS get a UTC-based time of day, same as the phone). A workout stopped with neither internet nor phone is no longer relayed at once (the Data Layer queues the put and delivers it on reconnect): if the watch then got internet first, the still-queued workout was uploaded directly *and* again by the phone — two identical activities, FitPub does not deduplicate. The sync worker now relays only to a connected phone (`NodeClient.connectedNodes`), otherwise retries, and never uploads directly once relayed (`shouldUploadDirectly`); a manual "Sync pending now" still clears both flags. Debug builds log to rotating files on the watch. The GPS battery-saving experiment was reverted. **Phone:** the profile card shows the user's header image behind the avatar; the profile heatmap is drawn like the web's (gradient heat layer, reloaded per viewport) and shows the server's generation status (`generationStatus`); indoor activities (Zwift, trainer, treadmill) get an "Indoor" badge and a placeholder instead of a map, and power (average/max watts) is shown when the file has it; feedback messages say they were sent from FP Client and link to the app's issue tracker. **Fix:** the timeline crashed while scrolling (`Key "…" was already used`) once an activity on it was boosted — the server lists timeline *events*, so a boosted activity appears as its `ORIGINAL` post and again as a `BOOST` with the same activity `id` (makni.cz public timeline, 2026-10-16; latent since server boosts `bcb75c89`, so 3.2.0 on Play is affected too). Rows are now keyed and deduplicated by `eventId` (falling back to `id` for older servers), and a `BOOST` card shows "🔁 <name> boosted · <time>" like the web (`TimelineBoostEventTest`). Server contract re-checked against FitPub `main` `0802bfc6` (`docs/API-COMPATIBILITY.md`). No server change. | ✅ done |
| **3.3.1** | **Federated activities open in the app; Me tab freeze fix** — versionCode 59 / versionName `3.3.1` (`:wear` versionCode 60 / `3.3.1`, version-only bump to keep the pair matched); `FP-Client/3.3.1` (+ `FP-Client-Wear/3.3.1`) User-Agent. A patch: no new screen, the existing activity detail now covers remote activities. A remote timeline card no longer hands over to a Custom Tab (2.0.1) — the home instance still 404s on the mirror id, but the origin serves the same public `GET /api/web/activities/{id}` (+ comments, likes, boosts) anonymously (verified fitpub.social ⇄ makni.cz), so the detail (map, stats, comments) is read from the origin over a separate cookie-less OkHttp client (the session JWT never leaves the home instance; `RemoteActivity.origin` accepts only `https://host/activities/{uuid}`). Reactions and boosts still go to the home instance under the mirror id; the viewer's own reaction comes from the home likes list and both are kept locally after a toggle because the origin only learns of them by federation. Commenting is not supported by the server for remote targets, so the composer is replaced by "Open on <host>"; GPX download is hidden (route files need an account on the origin). Followers-only activities (need a signed fetch) and non-FitPub URIs fall back to the origin page in a Custom Tab. The author handle is qualified as `@user@origin`, so an origin username that also exists at home is no longer taken for the viewer's own activity (`RemoteActivityTest`). **Fix:** the Me tab froze (ANR) while scrolling through the activities under the heatmap — reported on 3.3.0, reproduced over adb on a motorola edge 70 fusion within three scroll cycles, stack `MapView.zoomToBoundingBox` → `Projection.getCloserPixel` from the heatmap's first framing. 3.3.0 started framing with a border (35 dp per side) from a `post`, which can run before the map's first layout when the list composes the card ahead of placing it (scrolling back up); osmdroid then computes `log(0 − 2 × border)` = NaN as the zoom, and `getCloserPixel` loops forever on it (`pPixel -= NaN` → 0, `while (0 >= 0)`). 3.2.0 framed without a border, where width 0 gives −∞, which osmdroid clamps. Framing now waits for the layout (`doOnLayout`), keeps the border below half the map and skips a non-finite box (`HeatmapFramingTest`); 15 cycles without an ANR on the same phone. Hardened alongside: the overview loads at zoom 8 (the server's 0.01° grid, as the web page's first load) instead of no zoom, which the server answers with its finest grid for the whole history; the heatmap is reloaded only when the activity count changed; the overlay renders once the map has settled and shifts the last layer while it pans (Leaflet.heat's `moveend` behaviour, `HeatLayerTest`). Power tiles are hidden when the server reports 0 W (fitpub.social sends 0 for rides without a power meter, which showed "Avg power 0 W / Max power 0 W"); zero now counts as absent, like steps. No server change. | ✅ done |
| **3.3.2** | **Watch: a workout cut off by a reboot is saved** — versionCode 61 / versionName `3.3.2` (`:wear` versionCode 62 / `3.3.2`); `FP-Client/3.3.2` (+ `FP-Client-Wear/3.3.2`) User-Agent. A Xiaomi Watch 2 rebooted itself 6 minutes into a walk on 3.3.0 (boot reason plain `reboot`, no kernel panic, no FATAL in the watch log — the app did not cause it; the watch's own log and the track both end at the same second, then zeros the reboot never wrote). The app's part: a reboot kills the recording service without a STOP and a START_STICKY service is not restarted across a boot, so the session stayed RECORDING with nothing running — the tile kept saying "recording", the walk was never exported, and the next Start would have resumed it hours later instead of starting a new one. `WorkoutRebootReceiver` (BOOT_COMPLETED, and MY_PACKAGE_REPLACED so an update releases a workout already stuck) starts the service, which ends a workout begun before the last boot (`isInterruptedByReboot`) at its last recorded sample and queues it for upload; a Start does the same first if the receiver did not run. The zero-filled track tail is skipped as an undecodable line (`WorkoutRecordingStateTest`). Phone app: version-only bump. | ✅ done |
| **4.0.0** | + Iteration 10 — Garmin watch support (release decision 2026-10-14): a Connect IQ companion app for Garmin watches, living in this repo under `garmin/`, that records workouts and hands them to the phone, which builds the FIT and uploads it like a Wear OS workout. See the Iteration 10 section below. Version bump 2026-10-20: versionCode 63 / versionName `4.0.0` (`:wear` versionCode 64 / `4.0.0`, version-only bump to keep the pair matched); `FP-Client/4.0.0` (+ `FP-Client-Wear/4.0.0`) User-Agent; FP Wear `Version.NAME` 4.0.0, tester builds `fitpub-<device>_4_0_0.prg`. Licence Apache-2.0 from this release. Dormant Settings card hidden behind seven taps on the version in About (decision 2026-10-20, option b). Open before shipping: Play closed test for the Garmin testers, GitHub release. | 🟡 |
| **5.0** | + Iteration 7 (moved from 2.0 by release decision 2026-09-20, then from 4.0 to 5.0 by release decision 2026-10-14 to make room for Garmin support — translations still ship as the very last thing) — community-driven translations: full string externalization + per-app language selection (7a), Weblate project & sync (7b), CI guardrails (7c), store-listing translations (7d). See the Iteration 7 section below for the full sub-step plan. | ⬜ |

## Roadmap — one prompt per iteration

### F-Droid publishing ⬜ (process, outside this repo)
> "Submit FP Client to F-Droid. In-repo prerequisites are DONE (AGPL-3.0 LICENSE,
> binary-free source, unsigned release builds, fastlane metadata incl. icon).
> The fdroiddata fork `paveljanicek-cz-group/fdroid-data` (branch `fp-client`) has
> `metadata/com.fpclient.android.yml` with: AGPL-3.0-only, Authorname/Email, WebSite/
> SourceCode/IssueTracker/Changelog → https://github.com/pavel-janicek/fp-client,
> `UpdateCheckMode: Tags` + `AutoUpdateMode: Version`, `Binaries:` template
> (`.../download/Release_%v/app-release.apk`), `AllowedAPKSigningKeys` pinned to the
> signing cert SHA-256, and a `Builds:` entry. MR #1 is open against fdroid/fdroiddata.
>
> CURRENT BLOCKER: the reference-binary reproducibility check. F-Droid byte-compares
> the uploaded `app-release.apk` against its own buildserver output (only allowed diff
> is the signature); the APK must embed the exact git revision F-Droid builds from via
> `META-INF/version-control-info.textproto`. Fix = build the release APK FROM the same
> tag/commit the yml pins, and verify `unzip -p app-release.apk META-INF/version-control-info.textproto`
> before uploading. 1.3.2 is the first release cut this way.
>
> NEXT STEPS (per release): (1) tag `Release_x.y.z` on `main` AFTER merging the version bump;
> (2) `git checkout Release_x.y.z` and build signed AAB+APK from that exact tag; (3) verify the
> embedded revision matches the tag; (4) publish GitHub release `Release_x.y.z` with `app-release.apk`;
> (5) update the yml `Builds:` entry (commit/versionCode/versionName) in the fdroid-data fork to the
> new tag and push — with `UpdateCheckMode: Tags`+`AutoUpdateMode: Version` enabled, F-Droid then
> picks the newest tag automatically for subsequent releases.
>
> Run the MR pipeline (lint/rewritemeta/checks/build) on the self-hosted local GitLab runner
> (`Pavlovo`, docker executor, ~/.gitlab-runner config) to avoid exhausting GitLab.com compute
> minutes; `fdroid build` needs the runner tagged `saas-linux-medium-amd64`. Keep the signing
> keystore backed up (it is pinned in `AllowedAPKSigningKeys`).
>
> AFTER PUBLICATION: add the F-Droid badge + install link to README.md and note the package
> identity (`com.fpclient.android`) in VERSION_CHECKLIST.md."

### Passkey sign-in in the app — BLOCKED on a server change (not implementable yet)

Investigated 2026-09-28 against the server sources. **Do not start the client work before
step 1 lands on the server** — the server is the source of truth, and today it rejects a
native-app assertion no matter how well the client is written. The endpoint family exists
(`PasskeyResource`: `POST /api/web/auth/passkeys/login/options`, `POST
/api/web/auth/passkeys/login`, plus `/api/web/users/me/passkeys/**` for management) and the
wire format is **standard WebAuthn JSON** (`spring-security-webauthn` 7.1.0 / webauthn4j,
`PublicKeyCredentialRequestOptions` over the wire), so there is no protocol
reverse-engineering to do. The blockers are platform-level, not API-level.

**Blocker 1 — the server accepts exactly one origin, and it is not a native app.**
`PasskeyRelyingPartyConfig.passkeyAllowedOrigins` is
`Set.of(FitPubPublicOrigin.parse(baseUrl).origin())` — a hard-coded singleton derived from
`fitpub.base-url`, with **no** environment override (no `fitpub.passkey.*` key exists in
`application.yml`). `FitPubWebAuthnRelyingPartyOperations` then builds webauthn4j's
`ServerProperty` with exactly that origin set and the `rpId` derived from the same base URL.
A native Android passkey ceremony puts `android:apk-key-hash:<base64url(SHA-256(signing
cert))>` into `clientDataJSON.origin`, which can never equal `https://your-instance.example`,
so every assertion fails origin validation and is answered with the deliberately neutral 401
(`PasskeyService.NEUTRAL_LOGIN_FAILURE`). Additionally, Credential Manager only accepts an
`rpId` for which the calling app is authorized via Digital Asset Links
(`https://<rpId>/.well-known/assetlinks.json`) — a per-instance operational requirement that
cannot be met for arbitrary self-hosted instances.

**Blocker 2 — F-Droid builds have no FIDO2 authenticator at all.** Android exposes no
first-class CTAP2 API; the supported route is Credential Manager
(`androidx.credentials`, `GetPublicKeyCredentialOption`), whose passkey support is supplied by
**Google Play services**. This app ships on F-Droid and deliberately has no Play Services
dependency, so the F-Droid flavor would have no credential provider and no passkeys at all.
Writing or bundling a CTAP2 stack is not a realistic option.

**Also ruled out:** handing the ceremony to the instance's own web UI in a Custom Tab.
`PasskeyLoginResponse.returnTo` is sanitised by `LoginReturnTargets.sanitize()` to a
same-origin relative path only (it rejects absolute URLs, `//`, and any cross-origin target),
so there is no sanctioned deep-link/custom-scheme handoff that could carry a session back to
the app — the `JWT_TOKEN` cookie would land in the browser, where the app cannot read it.

Prompts, in dependency order. Steps 1-2 are **server-side**
(`/home/janipav/Documents/fitpub`) and are the gate; 3+ are this repo.

> **(1) SERVER — make the passkey origin set configurable.**
> "In `PasskeyRelyingPartyConfig`, replace the hard-coded
> `Set.of(FitPubPublicOrigin.parse(baseUrl).origin())` with a set that also contains any
> origins from a new `fitpub.passkey.additional-origins` property (env
> `FITPUB_PASSKEY_ADDITIONAL_ORIGINS`, comma-separated). Pass the merged set to
> `Webauthn4JRelyingPartyOperations` and to `FitPubWebAuthnRelyingPartyOperations`. Keep the
> instance's own origin first-class and unchanged, keep `rpId` derived from
> `fitpub.base-url`, and make sure the anonymous-login path keeps returning the neutral 401
> rather than leaking why verification failed. Add a test that a config with one extra origin
> still verifies a good assertion and still rejects an unlisted one."

> **(2) SERVER — decide and document the native-client policy.**
> "Document in `docs/` how a native client is expected to authenticate with a passkey: which
> `rpId` it must request, what Digital Asset Links entry the instance must publish at
> `/.well-known/assetlinks.json` (package `com.fpclient.android` plus the APK signing cert
> SHA-256, per distribution channel), and how an instance admin supplies the
> `android:apk-key-hash:` origin. State plainly what happens on an instance that has not
> published assetlinks.json. If the answer is 'native passkeys are not supported', say so
> explicitly so clients stop treating this as a gap."

> **(3) Add the Credential Manager plumbing, feature-flagged and inert.**
> "Add `androidx.credentials:credentials` (plus `credentials-play-services-auth`) to
> `app/build.gradle.kts`, and a `PasskeysAvailable` helper that reports whether a passkey
> provider is actually present at runtime, so the F-Droid build degrades to password-only
> with no crash and no menu entry. Verify `./gradlew testDebugUnitTest assembleDebug` and
> `./gradlew assembleRelease` (R8) still pass, and add whatever the new dependency needs to
> `proguard-rules.pro`."

> **(4) Login options plus assertion exchange.**
> "Add `POST api/web/auth/passkeys/login/options` and `POST api/web/auth/passkeys/login` to
> `FitPubApi`, with request/response DTOs mirroring the server's
> `PasskeyLoginOptionsResponse` / `PasskeyLoginCompletionRequest` / `PasskeyLoginResponse`.
> Add a `PasskeyRepository` whose `AuthResponse` cookie handling is identical to
> `AuthRepository` — the `JWT_TOKEN` cookie must be captured from `Set-Cookie` and stored via
> `SessionStore`; the `returnTo` field is display-only. The ceremony id is server state: keep
> it in memory for the duration of the call only, never persisted. Add JVM unit tests for the
> DTO mapping and the cookie capture, in the existing MockWebServer style."

> **(5) Passkey sign-in UI on the login screen.**
> "Add a 'Sign in with a passkey' action to the login screen, shown only when
> `PasskeysAvailable` is true. On tap: fetch the options, pass the server's `publicKey` JSON
> straight through to `CredentialManager.getCredential` with
> `GetPublicKeyCredentialOption(requestJson = ...)`, then post the resulting assertion
> (`clientDataJSON`, `authenticatorData`, `signature`, `userHandle`, `rawId`, all base64url)
> back to the server. Do not parse or validate the assertion client-side. Surface the neutral
> 401 as a plain 'Sign-in failed — try your password', never as a technical error. Wire the
> success path into the existing post-login navigation so a passkey session is
> indistinguishable from a password session."

> **(6) Passkey management in Settings (no registration yet).**
> "Add list / rename / delete for `GET /api/web/users/me/passkeys`,
> `PATCH /api/web/users/me/passkeys/{id}` and `DELETE /api/web/users/me/passkeys/{id}`. Note
> that both mutating calls take a **current password** in the body (the server re-checks it),
> so the UI must prompt for it; the label is limited to 1-100 characters. Registration
> (`.../registration/options` plus `.../registration`) is deliberately NOT part of this step:
> the server requires `residentKey: REQUIRED` and `userVerification: REQUIRED`, so adding a
> passkey is security-sensitive and deserves its own prompt once sign-in has shipped and been
> exercised on a real device."

> **(7) Docs, changelog, and release.**
> "Record the passkey endpoints and the origin/assetlinks requirement in
> `docs/API-COMPATIBILITY.md`, add the user-visible bullets to
> `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and the ledger row in
> `PLAN.md`, then run `VERSION_CHECKLIST.md` — including the unsigned release build, since a
> new Play Services dependency is exactly the kind of change that can break the F-Droid
> buildserver."

### Garmin Connect API (cloud import of native Garmin activities) — ⬜ research, not scheduled

**Why it matters.** Garmin reopened the Connect Developer Program on 2026-10-21 (DC Rainmaker,
<https://www.dcrainmaker.com/2026/10/garmin-connect-api-revamps-connect-developer.html>). Through
it FitPub could receive the native Garmin activity as a full `.FIT` from any Garmin watch: no FP
Wear install, and no limit from the watch's app storage (the Forerunner 55 holds only about 1 h
of FP Wear samples). This replaces the Iteration 10 rejection reasons "legal entities only" and
"paused for new applicants". The server-to-server objection there still stands.

**What changed (per the article; not yet checked against Garmin's own terms):**

| Tier | Cost | Users | Notes |
|---|---|---|---|
| Personal | free | 1 (own data) | keys almost instantly |
| Startup | free | up to 1,000 | + Courses & Training API (push routes/workouts to Garmin) |
| Enterprise | $1,000 entry (credited), then a negotiated fee per monthly active user from **2027-04-01** | > 1,000 | + history backfill, Golf API, support |

Personal and Startup both get JSON activity summaries and full `.FIT` files. Garmin reportedly
strips some fields from API FIT files (VO2Max mentioned in the comments; unconfirmed). Connect IQ
is unchanged and stays free, so FP Wear (Iteration 10) is unaffected.

**Blockers to resolve before any code:**
1. **Where it runs.** The Connect API is server to server (OAuth consumer secret, Garmin pushes to
   a webhook). The secret cannot live in the APK and the phone cannot receive webhooks, so this
   belongs on the FitPub server or in a small separate relay.
   **Decided 2026-10-22 (maintainer): a separate relay**, working name `connect.paveljanicek.cz`,
   in the maintainer's `dockerized-server` project next to `fitpub-push-relay` (Plan 8g), which
   is the model: Go standard library only, payloads never logged, a `PRIVACY.md`, TLS through
   Nginx Proxy Manager. This keeps the FitPub server unchanged, provided the relay hands
   activities to FitPub as an ordinary upload with the user's own token. How the relay gets and
   stores that token is still open.
2. **Terms vs. federation.** A comment by the article's author says Garmin's terms forbid
   passing API content to other entities without written permission. FitPub federates
   activities over ActivityPub to other servers. Read the developer agreement and, if unclear,
   ask Garmin in writing before building anything.

**Next steps, in order:**
1. *Now:* nothing. The FP Wear tester round continues unchanged.
2. *Soon (a few minutes):* register for the **Personal** tier with the maintainer's own account.
   Pull a few activities and compare the API FIT with the Garmin Connect export (missing
   fields) and with what makni.cz makes of it.
3. Settle blockers 1 and 2 above.
4. Only then apply for **Startup**. 1,000 users is far above the current audience, so the
   2027-04-01 Enterprise billing should not apply.

### Iteration 1 — Make it compile ✅
> "FitPub Android does not compile — `./gradlew assembleDebug` reports 28 Kotlin
> errors (see PLAN.md table). Fix every error following existing conventions:
> add the missing imports in DiscoverTab.kt, NotificationsTab.kt,
> EditProfileScreen.kt and ProfileScreen.kt; make the ManualFormParts helpers
> non-private; fix the two delegated-property smart casts by capturing locals;
> fix `CommonUi.kt:123` padding math (`size.dp / 4`); resolve the comment
> content type mismatch in ActivityDetailWidgets.kt:87 against CommentDtos.kt;
> resolve the generic mismatch in DiscoverTab.kt:80. Iterate until
> `./gradlew assembleDebug` succeeds, then report the diff summary."

### Iteration 2 — Repo & build hygiene ✅
> "Clean up the project infrastructure: add a proper .gitignore (.gradle/,
> build/, .kotlin/, local.properties, .idea/) and remove tracked build
> artifacts from git; delete empty dirs ui/map and ui/screens; enable
> buildFeatures.buildConfig and replace ApiClient's hardcoded isDebug=true
> with BuildConfig.DEBUG; add a README describing the app, how to point it at
> a self-hosted instance, and how to build; verify assembleDebug still passes."

### Iteration 3 — Runtime verification pass ✅
> "Run the app on an emulator/device and verify each critical flow end-to-end:
> server setup → register/verify → login → timeline browse → open activity
> detail (map renders) → like/comment → create activity via file upload and
> manual entry → edit profile → privacy zones → logout/login. Fix any crash,
> broken state handling, or navigation issue you find. Confirm osmdroid tile
> loading works (user agent configured) and file picker + upload work."

### Iteration 4 — Feature completion
> "Wire the implemented-but-unreachable backend features into the UI, mirroring
> web app parity: heatmap on ProfileScreen (myHeatmap/userHeatmap); monthly &
> yearly summaries and training load in the Analytics tab; batch import
> (GPX/FIT export upload + job status) in Settings or Create; profile header
> image upload/remove in EditProfileScreen; account deletion in Settings;
> remove or implement the unused Routes SEARCH / CREATE_MANUAL /
> ANALYTICS_DETAIL. Keep patterns consistent with existing screens."

### Iteration 5 — Tests & CI ✅
> "Add unit tests: SessionStore.normalizeServerUrl, TrackParser, Format,
> UrlBuilder, repository mapping/error handling (MockWebServer), ViewModel
> logic; plus Compose UI smoke tests for auth and timeline. Add a GitHub
> Actions workflow running ./gradlew assembleDebug testDebugUnitTest lint.
> Get the suite green."

### Iteration 6 — Release hardening
> "Prepare for release: encrypt the auth token (or use EncryptedSharedPreferences);
> centralize error messaging (ErrorMessages.kt) and add retry/offline states;
> enable R8 minification with correct keep rules for kotlinx-serialization/
> Retrofit/osmdroid; add release signing config (via env vars); bump
> versionName/versionCode; final lint cleanup; produce a signed release APK."

> 🚩 Release gate: this gate originally covered Iterations 7+8 as **v2.0** (everything through Iteration 6 was v1.0). Iteration 7 (translations) has been **deferred to v4.0** by release decision 2026-09-20 — it now ships as the very last thing; v2.0 contains Iteration 8 only. Release decision 2026-10-14 moved it once more, to **v5.0**, because v4.0 is now Garmin watch support (Iteration 10).

### Iteration 7 — Multilingual support (community-driven translations) — ships as **v5.0** (deferred from v2.0, then from v4.0)
Goal: make every user-visible word in the app translatable and let the FitPub
community translate it without ever touching code. Chosen platform: **Weblate**
(open source, self-hostable, free hosting for free-software projects, native
GitHub integration) — it matches this project's F-Droid / de-googled audience,
and the same workflow also translates the Google Play and F-Droid listings via
the existing `fastlane/metadata/android/` directory.

Suggested split into sub-steps (one prompt each if done iteratively):

**7a — String externalization audit**
> "Make the app fully localizable: sweep every screen (Compose `Text`,
> `contentDescription`, dialogs, snackbars, notification texts — including the
> future TrackRecordingService notification from Iteration 8, `ErrorMessages.kt`,
> activity-type labels, permission rationales) and move every user-visible
> literal into `res/values/strings.xml`, replacing in code with
> `stringResource(R.string.…)` / `pluralStringResource` in composables and
> `context.getString(...)` in ViewModels/services. Never build sentences by
> concatenating translated fragments — use positional formatted arguments
> (`%1$s`) so word order can differ per language, and `<plurals>` for anything
> countable (activities, comments, followers, kilometers). Add per-app language
> selection working on all supported APIs: `localeConfig` (locales_config.xml) +
> native `LocaleManager` on API 33+, backported via
> `AppCompatDelegate.setApplicationLocales` (appcompat 1.6) below, exposed as
> Settings → Appearance → Language with a 'System default' entry. Turn lint
> `MissingTranslation`/`ExtraTranslation` into errors, and verify no screen
> regresses with Android pseudolocales (en-XA, ar-XB) before considering the
> audit done."

**7b — Weblate project & component setup**
> "Set up a community translation workflow on Weblate (self-hosted, or the free
> for free-software hosted.weblate.org / translate.codeberg.org): create project
> 'FP Client' with two components — (1) 'Android app' translating
> `app/src/main/res/values/strings.xml` across `values-<lang>/` directories,
> and (2) 'Store metadata' translating
> `fastlane/metadata/android/en-US/` (title.txt, short_description.txt,
> full_description.txt, changelogs/) across per-locale metadata directories.
> Configure the Weblate↔GitHub sync so translator commits land via a
> `translations` branch reviewed before merge; enable translation memory, a
> glossary for FitPub-specific terms (activity, boost, federation, instance,
> privacy zone, kudos), and the 'needs editing' review workflow so a change to
> the English source automatically flags affected translations for rework."

**7c — CI & sync guardrails**
> "Add GitHub Actions guardrails so translations can never break the build: a
> lint step failing on hardcoded strings or missing-source translations; a
> placeholder-consistency script asserting every locale keeps the same `%1$s`-style
> arguments and plural categories as the English source; and a scheduled job that
> pulls completed Weblate translations (via its REST API or a git pull of the
> translations branch), builds the app, and opens a PR when new strings arrive.
> `values/strings.xml` (English) is the single source of truth and is only ever
> changed through normal code PRs — translator files under `values-*/` are
> written exclusively by the Weblate sync. Add a CI pseudolocale build to catch
> layout overflow early, plus a longest-string smoke check (German/Dutch
> typically the worst case for expansion)."

**7d — Culturalization beyond strings**
> "Audit the non-string side of localization: dates/times rendered via localized
> `java.time` formatters rather than hardcoded patterns; decouple the units
> preference (km/mi in Format.kt / Settings) from the device locale — an athlete
> may want imperial units in a German UI and vice versa, so units must remain an
> independent setting; make layouts tolerate text expansion (wrapping instead of
> truncation, no fixed-width labels, autotexts sized for the longest locale); use
> `start`/`end` (never `left`/`right`) and AutoMirrored icons so RTL languages
> mirror correctly; keep map tiles and osmdroid language-neutral; document in the
> README which languages ship and how the build consumes translations."

**7e — Launch & community call**
> "Seed Weblate with 2–3 languages translated by the maintainer (e.g. Czech and
> German, matching the project's audience) so new translators see a working
> example instead of an empty project; add a 'Help translate FP Client' entry to
> Settings → About linking to the Weblate project, and a README section with
> translator instructions (join Weblate, claim a language, review workflow);
> ship localized fastlane changelogs and store listings from the next release
> onward; announce the call for language maintainers in the FitPub Matrix room
> and GitHub Discussions. Record the shipped locale list in PLAN.md."

Notes:
- Why Weblate and not Crowdin / Transifex / POEditor: those are free for OSS but
  proprietary; Weblate is AGPL, self-hostable, and its VCS-native workflow means
  translations arrive as ordinary git commits — no proprietary tooling anywhere in
  the F-Droid build-from-source pipeline.
- Keep the translation surface small and sustainable: prefer reusing existing
  strings over one-off variants, and keep `full_description.txt` short — every
  extra word is a word a volunteer must translate and re-review forever.
- English source strings carry developer-readable `comments` (the `<!-- … -->`
  above each string) explaining context and length constraints — translators see
  these in Weblate; a string without context WILL be mistranslated.
- Note (ordering): this iteration was originally planned to precede Iteration 8
  so the recording UI and its foreground-service notification would be born
  translatable rather than retrofitted. It has since been deferred past 2.0 and
  3.0 entirely (release decision 2026-09-20), so Iteration 8's UI already exists
  in English-only literals — 7a's string-externalization audit must also cover
  everything Iteration 8 added (recording screens, notification, summary,
  errors).


### Iteration 8 — On-device activity recording ("Record" feature)
Goal: start an exercise inside the app, record the track with the phone's GPS
while the screen is off / app is backgrounded, then review and share the
resulting activity to the configured FitPub instance. This is a new feature
(no backend changes needed — the existing single-file upload endpoint is reused).

Sub-steps 8f–8i add **universal push notifications** (likes, comments, boosts,
follows) riding the FitPub server's *existing* Web Push subscription API — no
server changes, no stored passwords, no token export; a tiny self-hosted
"mailbox" service on a user VPS carries the delivery.

Suggested split into sub-steps (one prompt each if done iteratively):

**8a — Permissions & service skeleton**
> "Add location-recording groundwork: manifest entries for ACCESS_FINE_LOCATION
> (+ COARSE), FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, POST_NOTIFICATIONS
> (API 33+), and a declared foreground Service with
> android:foregroundServiceType=\"location\"; runtime permission request flow from
> Compose (rememberLauncherForActivityResult) with rationale + settings fallback;
> create TrackRecordingService as a lifecycle-aware foreground service showing an
> ongoing notification (elapsed time, pause & stop actions). Verify the service
> survives backgrounding and process death restarts into the right state."

**8b — Tracking engine**
> "Implement GPS tracking inside TrackRecordingService using Android framework
> LocationManager (the project deliberately avoids Google Play services; revisit
> only if needed): requestUpdates with ~1–3 s interval / ~2 m min distance,
> filtering of inaccurate fixes (accuracy > ~20 m discarded), a state machine
> (idle → recording ⇄ paused → stopped), and incremental persistence of track
> points (Room entity lat/lon/ele/time/accuracy or append-only file) so nothing
> is lost on process death. Expose live state via a shared StateFlow
> (elapsed time, distance via haversine sum, current pace, elevation gain with
> smoothing). Add unit tests for distance/elevation math."

**8c — Recording UI**
> "Build the Record flow in Compose: entry point from a 'Record' button next to
> the + on Timeline/Me (new Route RECORD); pre-start screen with activity type
> picker (reuse ActivityTypes); live recording screen with big elapsed timer,
> distance, pace, elevation gain, and an optional osmdroid mini-map showing the
> live position dot + drawn polyline; pause/resume/stop controls; keep-screen-on
> toggle. Wire UI to the service StateFlow; handle 'recording in progress' state
> app-wide (e.g., banner + guard against starting a second session)."

**8d — Save & share to FitPub** ✅ done (see the 2.0 status row)
> "On stop: assemble the recorded session into a GPX 1.1 file (trk/trkseg/trkpt
> with ele + time; separate trkseg per paused segment) stored in app-private
> storage; show a post-workout summary screen (stats + mini-map) where the user
> sets title, description, visibility, activity type; upload via the existing
> multipart upload endpoint (same path as UploadForm); mark pending uploads in
> local storage and retry failed uploads later; after successful server import,
> offer navigation to the created ActivityDetail. Confirm privacy zones are
> applied server-side as with any uploaded track."

**8e — Polish & edge cases**
> "Handle battery/Doze behavior (foreground service exemption check, guidance to
> disable battery optimization), GPS-off prompts, no-fix handling (warn when no
> point captured for N minutes), discard confirmation dialog, low-storage
> behavior, and emulator testing via mock locations. Update README features list
> and take fresh screenshots."

**8f — Background notification polling (universal fallback, no VPS needed)** ✅ done (see the 2.1 status row)
> "Add background delivery of in-app notifications that works against ANY FitPub
> instance with zero extra infrastructure: a WorkManager periodic worker (≥15 min
> interval, Doze-deferrable — set expectations in Settings, this is 'eventual',
> not instant) that, for signed-in sessions only (skip guests), calls the
> existing `GET api/web/notifications?page=1` via NotificationRepository, tracks
> the last-seen notification id in DataStore, and posts local Android
> notifications for new ACTIVITY_LIKED / ACTIVITY_COMMENTED / ACTIVITY_SHARED /
> USER_FOLLOWED / FOLLOW_REQUEST rows — reusing the notification list's existing
> text formatting, coalescing bursts into one summary notification with the
> unread count. New notification channel `fitpub_push` (own OS-level toggle);
> POST_NOTIFICATIONS runtime gate modeled on LocationPermissionGate, asked once
> from a new Settings → Push section. On 401 keep the existing session-clearing
> semantics (do not loop). Unit-test the new-row diffing and summary coalescing.
> This is the only new dependency of the whole feature (androidx.work)."

Landed: `NotificationPollWorker` (periodic, unique work `fitpub_notification_poll`, 30 min, `NetworkType.CONNECTED`, exponential backoff, `ExistingPeriodicWorkPolicy.KEEP` so app starts never shift the schedule) polls `GET api/web/notifications?page=0&size=30` through `NotificationRepository` and skips guests/signed-out sessions outright. The cursor (last-seen notification id + owning `serverUrl|username` + last-poll timestamp) lives in a dedicated DataStore (`fitpub_push`, `NotificationPollStore`), scoped per session so switching accounts neither replays the previous account's rows nor swallows the new one's first page. Diffing/coalescing are pure JVM code in `NotificationPolling` (unit-tested): rows above the cursor, deliverable types only (ACTIVITY_LIKED, ACTIVITY_COMMENTED/COMMENT_ADDED, ACTIVITY_SHARED, USER_FOLLOWED, FOLLOW_REQUEST), unread and identified; a single new row is announced with its own wording, a burst collapses into one summary ("N unread notifications" + the newest row + "and M more"), and a 401 is never retried (the interceptor already cleared the session) while transport/5xx failures are. The in-app row wording moved to a shared, Android-free `NotificationText` object so the list and the push notification read identically. Delivery uses the `fitpub_push` channel ("FitPub activity", IMPORTANCE_DEFAULT — its own OS-level toggle, separate from `track_recording`) and is posted/cancelled by `PushNotifications`; opening the notifications tab clears the stale summary. Settings → Push owns the POST_NOTIFICATIONS runtime gate modeled on `LocationPermissionGate` (rationale dialog → system dialog → system-settings fallback, asked once) and states the delivery model plainly ("eventual, not instant", last-checked timestamp, and a repair button that re-arms **both** background checks — the 8f poll and the 8h mailbox check, either of which a system cleanup can drop — and confirms in-line that they are scheduled, so the idempotent (KEEP) re-arm is never mistaken for a dead button). New dependency: `androidx.work:work-runtime-ktx`.

**8g — VPS mailbox service (self-hosted RFC 8030 Web Push receiver)** ✅ done — lives in the `dockerized-server` repo (see Landed below)
> "Build the push infrastructure as a small dockerized service (separate
> repository, e.g. `fitpub-push-relay`; Kotlin/Ktor or Go, keep it dependency-
> light): an RFC 8030-compatible Web Push receiver holding NO FitPub
> credentials — ciphertext only. Endpoints: `POST /mailbox` mints a fresh random
> subscription id and returns the endpoint URL; `POST /push/<id>` stores an
> opaque aes128gcm blob (201 + Location header per RFC 8030); `GET /push/<id>`
> returns queued blobs and clears them; `DELETE /push/<id>` unregisters;
> auto-expire subscriptions the phone hasn't fetched for ~30 days by answering
> 410 Gone to subsequent POSTs — the FitPub server already deletes such
> subscriptions on 410/404 (verified: WebPushService.sendPush), so cleanup
> stays automatic end-to-end. Never log payloads. Ship docker-compose.yml,
> a reverse-proxy TLS example, deployment notes, and an integration test that
> subscribes a fake endpoint, fires a real FitPub push (or replays the server's
> encrypted fixture), then fetches + decrypts it with the matching key."

Landed: `fitpub-push-relay` (Go, standard library only, no third-party modules) inside the VPS repository `dockerized-server/`, wired into its `docker-compose.yaml` as service `push-relay` (named volume `push-relay-data` → `/data/state.json`, non-root image whose build runs `go vet` + the whole integration suite, `/healthz` healthcheck; standalone `docker-compose.yml` also shipped for running it alone). Endpoints exactly as specced: `POST /mailbox` mints a 128-bit base64url id and answers 201 + `Location` + `{"endpoint": …}` (origin from `RELAY_PUBLIC_BASE_URL`, else derived from `X-Forwarded-Proto`/`X-Forwarded-Host`); `POST /push/<id>` queues the body byte-for-byte with 201 + `Location` per RFC 8030 §5, enforcing §5.2 (400 when `TTL` is missing or not `1*DIGIT`, values > 2³¹ clamped to 2³¹), §7.2 (413 only above the 64 KiB cap and never for ≤ 4096 bytes; expired messages are dropped so a fetch of only-expired messages looks like "no message was ever sent") and 415 for a `Content-Encoding` other than `aes128gcm`; `GET /push/<id>` (the phone, 8h) returns `{"messages":[{ttl, received, payload}]}` (payload = standard base64 of the exact blob) and clears the queue at-most-once while refreshing the no-fetch clock; `DELETE /push/<id>` unregisters idempotently (204) and tombstones the id. Subscriptions not fetched within `RELAY_SUBSCRIPTION_TTL` (default 720 h ≈ 30 days) answer **410 Gone** to later POSTs (404 only for never-minted ids), so `WebPushService.sendPush` → `deleteByEndpoint` cleans the FitPub side automatically end-to-end (documented deviation: RFC 8030 §7.3 says 404 for expired subscriptions — the server handles both identically). State persists atomically across container restarts (a corrupt state file aborts startup instead of wiping mailboxes); payloads and VAPID `Authorization` values are never logged (pinned by `TestNeverLogsPayloadsOrAuth`). The integration test `TestFitPubPushFixtureReplay` subscribes a fake endpoint, delivers a fixture produced by the **real** `WebPushService.encrypt` (generated by `testdata/gen/FixtureGen.java` via reflection on the fitpub checkout — nothing reimplemented), fetches it back and decrypts RFC 8291 aes128gcm with the matching key, asserting the `{title, body, icon, tag, url}` plaintext. Also ships `deploy/nginx-relay.conf` (TLS reverse-proxy example with rate-limited `/mailbox`) and `DEPLOYMENT.md` (Nginx Proxy Manager steps for the websin.space stack, DNS, firewall, backup, 410 cleanup loop, live smoke-test procedure). Contract for 8h: mint the endpoint with `POST /mailbox`, register it with the existing `POST /api/web/push/subscribe`, poll `GET /push/<id>` from the WorkManager cadence, base64-decode `payload` and decrypt as 8h describes; `DELETE /push/<id>` on disable.

**8h — Client push: keys, subscribe, decrypt, mailbox check** ✅ done (Landed below)
> "Wire FP Client to the server's existing Web Push subscription API (verified
> against the FitPub source — `POST /api/web/push/subscribe`
> `{endpoint, keys{p256dh, auth}}`, `DELETE /api/web/push/subscribe`,
> `GET /api/web/push/vapid-key` as availability probe; treat its 503 as 'push
> disabled on this instance → keep the 8f poll fallback'). Settings → Push:
> generate an ECDH P-256 keypair + 16-byte auth secret on-device with JCA (no
> new crypto library), store app-privately, mint an endpoint on the user's
> configured mailbox (8g), and POST the subscription through the app's existing
> session + CSRF flow — no password or token is ever stored off-device, and the
> exported keypair can only decrypt notification payloads (revocable via
> DELETE /subscribe). Delivery: a WorkManager worker fetches queued blobs from
> the mailbox, decrypts RFC 8291 aes128gcm on-device (client-side mirror of the
> server's WebPushService.encrypt: parse the aes128gcm header [salt, record
> size, ephemeral AS public key], ECDH, HKDF, AES-128-GCM, strip the 0x02
> delimiter), parses the `{title, body, icon, tag, url}` JSON and posts the
> notification with the tag used for collapse. Disable = DELETE /subscribe +
> DELETE mailbox + wipe local keys. Unit-test decryption against vectors
> captured from the server's own WebPushService tests."

Landed: `WebPushCrypto` (Android-free, JCA only) generates the ECDH P-256 keypair + 16-byte auth secret on-device and decrypts RFC 8291 `aes128gcm` blobs as a client-side mirror of `WebPushService.encrypt` — header parse `[salt | record size | ephemeral AS public key]`, ECDH, HKDF-SHA256 (pinned against RFC 5869 test case 1), AES-128-GCM record loop with the 96-bit sequence-nonce XOR, `0x02` delimiter strip; `RecipientKeys.toString()` is redacted so key material can never reach a log line. Keys + endpoint + mailbox base live app-privately in `EncryptedSharedPreferences` (`PushSubscriptionStore`, the same encrypted store class the session JWT uses) tagged with the owning `serverUrl|username`. `PushRepository` owns the flow: `probe()` maps `GET /api/web/push/vapid-key` 200 → available and **503 → "push disabled on this instance"** (the Settings card then states that the 8f background check remains the delivery path and blocks the enable button); `enable()` mints a mailbox via `MailboxClient` — a credential-free plain-OkHttp client on a separate origin, so the session cookie and CSRF token never reach the VPS — then POSTs `{endpoint, keys{p256dh, auth}}` through the app's existing session+CSRF Retrofit interceptor, and unregisters the fresh mailbox if the instance answers 503 (no orphan mailboxes); `disable()` runs `DELETE /api/web/push/subscribe` (via `@HTTP(hasBody = true)` — Retrofit's `@DELETE` forbids bodies and the server declares `@RequestBody`) → `DELETE /push/<id>` → wipe local keys, with the local wipe guaranteed even when both systems are unreachable. Delivery: `PushFetchWorker` (unique work `fitpub_push_mailbox_check`, 15 min — WorkManager's floor and the promised ~15 min latency — `NetworkType.CONNECTED`, exponential backoff, `ExistingPeriodicWorkPolicy.KEEP`, scheduled unconditionally at app start like the 8f poll) fetches `GET /push/<id>`, decrypts and parses `{title, body, icon, tag, url}` (`PushPayloads`, unknown keys tolerated, one bad blob dropped without blocking the batch) and posts through `PushNotifications.postItem` with the payload `tag` as the Android collapse tag plus a stable per-tag id in its own range; a 410/404 tears the whole subscription down (server unsubscribe + wipe) and transport/5xx failures retry under the same `NotificationPolling.shouldRetry` policy as 8f. While a subscription matches the signed-in session, the 8f poll keeps polling and advancing its cursor but stops announcing — so the same event is never pushed twice — and resumes instantly (without replaying the backlog) the moment push is disabled or the instance turns it off. Tapping a payload notification opens the activity behind it when the `url` is an `/activities/<id>` path (`EXTRA_OPEN_PATH` handled by MainActivity), otherwise the notifications tab. Settings → Push gained a "Mailbox push" card (probe status, mailbox URL defaulting to the shared `https://push.paveljanicek.cz`, enable/disable with plain-language trust copy). Unit tests: decryption of the **server's own fixture** (`fitpub-push-relay/testdata/fixture.json`, produced by `FixtureGen` invoking `WebPushService.encrypt`) both directly and end-to-end through the relay's fetch shape in `PushRepositoryTest`, the 503 probe gate, mint→subscribe→store and disable wire orders with mailbox cleanup, HKDF RFC 5869 TC1, single/multi-record + padding framing round-trips, and malformed-header/delimiter/tamper rejections. No new dependencies.

**8i — Instant delivery via ntfy (optional, same VPS)** ✅ done (Landed below + `docs/PUSH-NOTIFICATIONS.md`)
> "Add the instant path on top of 8g/8h: extend the mailbox service with an
> opt-in mode where it also receives the subscription's private decryption key
> and, on each incoming push, decrypts and forwards readable `{title, body,
> url}` to a private ntfy topic on the same VPS (ntfy dockerized, Apache-2.0 —
> keeps the F-Droid dependency audit clean); the ntfy Android app then delivers
> within seconds in self-hosted WebSocket mode (document the battery-
> optimization exemption, same as 8e). Settings → Push gains an optional
> 'instant via ntfy' toggle with an explicit one-sentence trust explanation:
> the stored key only ever decrypts notification payloads (it cannot access
> the account), lives only on the user's own VPS, and is revoked by one
> DELETE /api/web/push/subscribe. Users who refuse that trust keep 8h (~15 min
> latency, keys never leave the phone). Optionally also an in-app WebSocket to
> the mailbox for instant delivery without ntfy. Document the whole stack in
> docs/: instance admin (FITPUB_PUSH_ENABLED=true + VAPID keys — off by
> default on every instance), VPS (mailbox + optional ntfy), and app

Landed: the optional fast path is entirely opt-in and additively scoped — turning it on never changes the 8h subscription, and turning it off never disables 8h. `MailboxClient.mint()` now returns `MintedMailbox(endpoint, manageToken)` instead of a bare endpoint: the relay (8i-aware) mints a bearer manage token alongside every mailbox and stores only its HMAC-SHA256 hash, so the *only* thing that can later add or remove a forward config is a device holding that token — a stolen endpoint URL is not enough. Relays before 8i return no token, which the app stores as `null` and the card renders as "this relay does not support instant delivery" rather than a broken switch. `MailboxClient` grew `setForward` / `getForward` / `clearForward` (`PUT` / `GET` / `DELETE` `{endpoint}/forward`, `Authorization: Bearer <manageToken>`), each mapping the relay's failure codes to honest messages (503 → "instant delivery is not configured on this relay", 401 → "the relay rejected the authorization token", 404/410 on delete → already gone = success). `PushRepository.enableInstant(customTopic?)` validates the topic against ntfy's own `[-_a-z0-9]{1,64}` rule **before** any key leaves the device (so an invalid topic costs zero network calls and uploads nothing), generates an unguessable `fp-<16 random bytes hex>` topic when the user leaves it blank, refuses outright when the subscription belongs to a different account or the relay minted no manage token, and otherwise uploads the *same* P-256 private key + auth secret the phone already holds, plus `clickBase = serverUrl` so the ntfy app can deep-link into the instance the event came from. `disableInstant()` issues the DELETE and clears the local mirror even when the relay is unreachable, mirroring `disable()`'s "local wipe is the guarantee"; `disable()` is unchanged apart from a comment, since unregistering the mailbox already drops its forward config in the same request. `PushSubscription` gained `manageToken` / `instantEnabled` / `ntfyTopic`, persisted in the same `EncryptedSharedPreferences` and cleared with the rest of the subscription; the ntfy server address is remembered separately (`ntfyServer`, default `https://ntfy.paveljanicek.cz`) purely so the card can tell the user where to point the ntfy app. Settings → Push gained an `InstantDeliveryBlock` rendered inside the "Mailbox push" card only while that account owns the mailbox: a switch, the one-sentence trust explanation (the key only decrypts notification payloads, cannot access the account, lives only on the chosen relay, and is deleted the moment the switch goes off or mailbox push is disabled), an optional custom topic field, the copyable topic, and the concrete ntfy-app instructions (self-hosted server, WebSocket mode, battery-optimization exemption) with a pointer that the ~15 minute check stays on as a fallback. No new dependencies — ntfy is reached by the relay, not by the app. Unit tests pin the manage-token parse (with and without), `ForwardStatusDto` decoding, the enable→PUT wire contract (method, path, bearer header, body fields incl. `privkey`), generated-topic validity, local rejection of a bad topic with `requestCount == 0`, the 503 path leaving the switch off, the no-manage-token and wrong-account refusals, the DELETE teardown, the no-op when instant was never on, and that a full `disable()` wipes the instant state too. Everything is documented end-to-end in `docs/PUSH-NOTIFICATIONS.md` (instance admin, VPS, app, trust boundary, troubleshooting).

> (Settings → Push)."

**8j — In-app ntfy receiver (no second app: FP Client subscribes itself)**
> "Replace the 'install the ntfy Android app' step of 8i with a receiver inside FP
> Client, so a user who wants instant delivery never has to install a second app.
> The relay already publishes to the private ntfy topic; all that is missing is a
> client that subscribes to it and posts the notification itself. Use the ntfy
> subscribe API over plain OkHttp (already a dependency) — `GET
> {ntfyServer}/{topic}/json` as an NDJSON stream, with `since=<lastNtfyMessageId>`
> for gap replay after a dropped connection (`/ws` WebSocket is an acceptable
> alternative but the NDJSON stream is preferred: it is plain OkHttp, resumes
> naturally, and is trivially mockable with the `mockwebserver` already in
> `testImplementation`). No new dependency, no Firebase, nothing proprietary —
> the F-Droid audit stays clean.
>
> The receiver is a foreground service (`InstantDeliveryService`, modeled on the
> existing `TrackRecordingService`: `LifecycleService`, `START_STICKY`,
> `ServiceCompat.startForeground`), because seconds-long latency on Android is only
> achievable with a long-lived socket. It must: (1) connect only while a session is
> logged in AND owns a `PushSubscription` with `instantEnabled`, mirroring how
> `PushFetchWorker` and `NotificationPollWorker` bail out — a signed-out device or
> another account's subscription must never connect; (2) reconnect with exponential
> backoff and jitter, honouring ntfy's `poll_request` event; (3) treat a silent
> connection as dead via a read timeout (ntfy sends `keepalive`; also set
> `pingInterval` on the OkHttp client for WebSocket mode) and reconnect rather than
> linger; (4) on reconnect, fetch `?poll=1&since=<lastNtfyMessageId>` so messages
> that arrived while the socket was down are not lost (ntfy caches 12h by default —
> beyond that the events are simply gone, same class of loss as 8f, and the copy
> must say so); (5) keep the last seen ntfy message id in `PushSubscriptionStore`
> (EncryptedSharedPreferences, alongside `ntfyTopic`/`instantEnabled`, cleared by
> `clear()` and by `setInstantForward(false, …)`); (6) post each message through
> the existing `PushNotifications.postItem`, which already owns the channel, the
> POST_NOTIFICATIONS gate, the collapse tag and the tap deep-link; (7) do its work
> in a coroutine scope cancelled in `onDestroy` and never leak the subscription
> across process death.
>
> **Change the relay to forward ciphertext, not plaintext, and drop the key upload
> entirely (this is the point of the whole step).** Today `forwardToNtfy` decrypts
> and the phone uploads `ForwardKeysDto.privkey` (see
> `dockerized-server/fitpub-push-relay/forward.go`), which is the single switch in
> the whole app that moves a key off the device. With the client in-repo, the relay
> must publish the untouched RFC 8291 aes128gcm blob instead: POST
> `{topic, message: <standard base64 of the blob>, click}` and **stop requiring
> `keys.privkey`**. The app decrypts with the `WebPushCrypto` it already ships
> (the same mirror of the server's `WebPushService.encrypt` that `PushFetchWorker`
> uses) via `PushRepository.fetchPayloads`' existing key-triple path. Keep
> `PUT /push/<id>/forward` accepting the `keys` object for one release (a
> pre-8j relay or an older phone may still send it) but never read `privkey` from
> it, and have `PushRepository.enableInstant` stop uploading the private scalar.
> The result: instant delivery changes **nothing** about what leaves the phone
> versus mailbox push — same key-stays-local story, same 15-minute `fitpub_push`
> check as the fallback, no orphan key on the VPS when a user switches instant off.
>
> Also close the two delivery-correctness gaps this exposes: (a) the relay currently
> does NOT enqueue the ciphertext when a forward succeeds (`handlers.go` ~L123-126),
> so a message accepted by ntfy but missed by a dead socket can never be recovered
> by `fitpub_push_mailbox_check` — make the app dedupe by the ntfy message id and
> have the relay enqueue as well, so the 15-minute check is a genuine backstop;
> (b) the collapse tag is lost because the relay publishes only
> `title/body/click/priority` — publish the payload tag as a second ntfy `tags`
> entry (`["fitpub", "fitpub-activity_liked"]`; ntfy treats unknown tags as
> non-emoji names and ignores them, FP Client reads them) so `postItem` keeps
> collapsing per event type exactly as it does on the 8h path.
>
> Rewrite the Settings → Push `InstantDeliveryBlock` (it currently instructs the
> user to install ntfy, add a subscription, copy the topic, and grant ntfy a
> battery-optimization exemption — all of that text must go): show the live socket
> state instead (connecting / connected since / retrying, with a last-message
> timestamp), a start/stop control, and a battery-exemption row modeled on
> `RecordingHealthCard` (`isIgnoringBatteryOptimizations`) that explains that a
> permanent 'listening for notifications' notification is required and how to turn
> it off. Keep the existing one-sentence trust explanation, rewritten to the
> stronger claim ('your key never leaves this phone — not even for instant
> delivery'). The ntfy server field and topic remain (a self-hosted ntfy address
> is still the operator's choice), but the topic is no longer something the user
> copies anywhere.
>
> Foreground-service constraints to get right, since `targetSdk = 36`: declare the
> service with an explicit `foregroundServiceType` plus the matching
> `FOREGROUND_SERVICE_*` permission, and implement `Service.onTimeout()` —
> `dataSync` is capped at 6h/24h on API 35+. Prefer `specialUse` (no cap) with
> the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property, and note in the code comment why.
> The ongoing notification needs its own channel, separate from `fitpub_push` and
> `track_recording`, so the OS-level toggles stay independent.
>
> Tests (JVM, no new deps): `NtfyMessages` parses `open`/`keepalive`/`message`/
> `poll_request` and ignores unknown events and unknown keys; the URL builder
> produces `{base}/{topic}/json` and `{base}/{topic}/json?poll=1&since=<id>` and
> rejects a topic outside ntfy's own `[-_a-z0-9]{1,64}` rule; a MockWebServer
> NDJSON stream is consumed correctly and a malformed line is dropped without
> killing the stream; base64 blob → `WebPushCrypto.decrypt` → `PushPayloads.decode`
> round-trips against the same fixture `PushRepositoryTest` uses; dedupe drops a
> re-delivered ntfy id; the service no-ops for a guest, a signed-out session and
> another account's subscription. Update `docs/PUSH-NOTIFICATIONS.md` (the three
> delivery paths, the new trust boundary, the removed ntfy-app step, the FGS
> prompt, and a troubleshooting row for 'instant delivery is not connecting'),
> rewrite `PRIVACY.md` §1/§3/§4/§6 so instant mode no longer claims the operator
> can read your notifications, and add the 8j row to the Iteration 8 notes."

Landed: the 8i "install the ntfy app" step is gone — FP Client is the subscriber. New app-side pieces, all Android-free where they can be and all unit-tested: `NtfyMessages` (the ntfy NDJSON decoder plus the two URL shapes, `{base}/{topic}/json` and `{base}/{topic}/json?poll=1&since=<id>`, with the topic validated against ntfy's own `[-_a-z0-9]{1,64}` rule *before* a URL is built, so a crafted topic can never escape into another path), `NtfyDeduper` (an id is delivered once; `since=` deliberately re-delivers its boundary, so a replayed boundary would otherwise notify twice), and `InstantDeliveryBus`/`InstantDeliveryState` (the live socket state the Settings card now renders instead of instructions to install something). `InstantDeliveryService` is a `LifecycleService` foreground service on a plain, credential-free OkHttp client — `readTimeout` 40 s (shorter than ntfy's `keepalive`, so a silent connection *fails* and reconnects rather than lingering as a connected-looking zombie), `pingInterval` set so WebSocket mode would also be usable, exponential backoff with full jitter, ntfy's `poll_request` answered with an immediate gap-replay poll instead of a wait, all work on a scope cancelled in `onDestroy` so no socket or retry survives the service. It connects **only** while a session is signed in and owns an `instantEnabled` `PushSubscription` — the same guard `PushFetchWorker`/`NotificationPollWorker` use, extracted as `resolveTarget` so a guest, a signed-out device and another account's subscription are pinned by unit tests. The last seen ntfy id lives in `PushSubscriptionStore` next to `ntfyTopic`/`instantEnabled` and is cleared by `clear()` and by `setInstantForward(false, …)`. Declared `foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` and `FOREGROUND_SERVICE_SPECIAL_USE`, because `dataSync` is time-capped on API 35+ (6 h/24 h) and an always-on subscription must not be torn down mid-flight; `onTimeout()` is implemented anyway (mandatory on 35+). Its ongoing notification gets its own `fitpub_instant_delivery` channel, so the OS-level toggles for "instant delivery", "activity" and "track recording" stay independent. **The point of the step is the relay change:** `forwardToNtfy` no longer decrypts — it POSTs the untouched blob as standard base64, and `crypto.go` (the whole RFC 8291 decryptor) is deleted, leaving only a `ForwardKeys` type so `PUT /push/<id>/forward` still *accepts* an 8i phone's `keys` object for one release and then discards it unread; `PushRepository.enableInstant` stopped sending `privkey` and the DTO field is nullable for one release. Instant delivery now moves exactly as much off the phone as mailbox push — the same key-stays-local story, the same 15-minute `fitpub_push_mailbox_check` fallback, and no orphan key on the VPS when a user switches instant off. Two delivery-correctness gaps closed with it: the relay now **enqueues the ciphertext even when a forward succeeds** (it used to skip the queue entirely, so a message ntfy accepted but a dead socket never delivered was unrecoverable) and the app dedupes by the ntfy message id, making the 15-minute check a genuine backstop; and the payload tag rides along as a second ntfy `tags` entry (`["fitpub","fitpub-activity_liked"]` — ntfy treats unknown tags as non-emoji names and ignores them, FP Client reads them) so `postItem` keeps collapsing per event type exactly as on the 8h path. `PushRepository.decryptPayload` is now shared by both paths over the same key triple, so the fast path cannot drift from the slow one — a fixture test proves the relayed base64 blob decrypts to the same `PushPayloadDto` the mailbox route returns. Tests: `NtfyMessagesTest` (the four event types, unknown events/keys, garbage lines, both URL shapes, a topic list that must be rejected, a MockWebServer NDJSON stream dribbled one byte per chunk in which a malformed line is dropped without killing the stream, the fixture round trip and the dedupe), `InstantDeliveryServiceGateTest` (guest / signed-out / another account / switch-off / half-written config, plus the backoff bounds) and the rewritten `PushRepositoryTest` assertion that no private scalar is uploaded. Settings → Push's `InstantDeliveryBlock` was rewritten: every instruction to install ntfy, add a subscription, copy the topic and exempt *ntfy* from battery optimisation is gone, replaced by the live socket state (connecting / connected since + last-notification / retrying with the reason), a start/stop control with a restart action, and a battery-exemption row modeled on `RecordingHealthCard` (`isIgnoringBatteryOptimizations`, re-read on resume) that explains the permanent "listening for notifications" notification and how to turn it off. Notifications must behave correctly against a server that is **not** modified: the instance is a black box and 8f–8j ride its pre-existing Web Push API, so everything needed to render a federated actor (`actorUri`, `actorLocal`) is already on the wire and no server change may be proposed as a fix for an app-side bug — a bare local username must never be used to navigate to a profile, because that endpoint resolves local usernames only and produced "User not found" for a follower on another instance; the in-app list and the 8f poll both show the full `@user@host`, while 8h/8j wording is the server's own payload text and cannot be re-derived by the app. Docs: `docs/PUSH-NOTIFICATIONS.md` (three paths, the new trust boundary, the removed ntfy-app step, the FGS choice, a troubleshooting row keyed on the retry reason) and the relay's `PRIVACY.md` §1/§3/§4/§6 rewritten so instant mode no longer claims the operator can read your notifications.

Notes:
- Reuses existing pieces: osmdroid (live map), Format.kt (stat formatting),
  ActivityTypes icons, upload endpoint/multipart plumbing from CreateViewModel.
- New dependencies to consider (keep minimal): none strictly required — Room
  optional (could start with a simple file-backed log); avoid play-services-location.
- Push sub-steps 8f–8j ride the server's **existing** Web Push subscription API
  (verified in the FitPub server source: `social/fitpub/push/PushSubscriptionResource`
  — `POST/DELETE /api/web/push/subscribe`, `GET /api/web/push/vapid-key`; and
  `WebPushService` — RFC 8291 `aes128gcm` encryption + VAPID per RFC 8292): no
  server changes, no stored passwords, no token export; the server's ongoing
  JWT/auth-version hardening does not affect an active subscription because push
  is outbound and sessionless. 8f is the universal fallback when an instance has
  push disabled.
- Dependency impact: 8f introduces `androidx.work` (WorkManager); 8h adds none
  (JCA crypto only); 8j adds none (OkHttp NDJSON + a foreground service); the VPS
  services (8g/8i) live outside this repo.
- 8j reverses 8i's key-upload trade-off: with the client in-repo the relay
  forwards ciphertext and `keys.privkey` is no longer sent anywhere, so instant
  delivery stops being the one switch that moves a key off the device
  (`PRIVACY.md` §4). It is the reason to prefer it over telling users to install
  the ntfy app.

> 🚩 Release gate: this iteration ships as **v3.0**.

### Iteration 9 — Wear OS companion app ("FitPub Wear")
Goal: a Wear OS companion module so athletes can leave the phone at home,
start/record a workout from the wrist using the watch's internal monitors
(GPS, heart rate, step counter), and have it shared to their FitPub
instance — the open-source equivalent of Strava's wearable experience.
Delivered as a new Gradle module `:wear` alongside `:app`; the phone remains
the sign-in authority and the upload relay.

Suggested split into sub-steps (one prompt each if done iteratively):

**9a — Module & project scaffolding** ✅ done (see docs/WEAR.md)
> "Create a :wear Wear OS module (build.gradle.kts with com.android.application +
> wearApp wiring in :app via wearApp/unstable bundled dependency, wear_app.xml
> pairing metadata, minSdk matching Wear OS 3+ = API 26–30 target latest),
> set up Compose for Wear OS (androidx.wear.compose:material, navigation),
> round-display-safe layouts (BoxWithConstraints / curved modifiers where useful),
> and a minimal launcher activity proving install-on-watch works from Studio.
> Keep the module independent of :app code except a small :core-shared set or
> duplicated DTOs — decide and document which."

**9b — Phone↔watch sign-in handshake**
> "Implement sign-in relay from the mobile app using the Android Data Layer:
> CapabilityClient advertising 'fitpub_phone' capability, MessageClient handshake
> when the watch requests credentials, phone responds with serverUrl + bearer
> token (+ username/displayName) pulled from SessionStore; watch stores them via
> its own DataStore (document that Data Layer payloads are TLS-equivalent
> protected on the transport but still encrypt-at-rest later per Iteration 6).
> Add 'device signed in as @user' state UI on the watch and a revoke/sign-out
> path both directions; handle no-phone-paired and stale-token states."

✅ **Done.** The phone advertises `fitpub_phone`; the watch discovers reachable phones and
requests credentials by `MessageClient`. The phone responds from `SessionStore` with server URL,
bearer token and identity, and broadcasts session revocation only to reachable nodes advertising
`fitpub_watch`. Watch credentials live in its own Preferences DataStore. Phone logout and stale
401s revoke the watch copy; watch sign-out clears its local copy immediately and revokes the phone
session when reachable. The UI distinguishes signed-in, expired, signed-out and no-reachable-phone
states. Data Layer transport is encrypted by Google Play services; watch DataStore encryption at
rest remains a hardening follow-up. The modules now share the exact application ID and signing
certificate required by Data Layer. Details and the trust boundary are in `docs/WEAR.md`.

**9c — Watch sensor recording engine**
> "Build WorkoutRecordingService on the watch: a foreground service (location +
> bodySensors + activityRecognition types) capturing GPS (onboard GNSS via
> FusedLocationProvider or LocationManager — decide per minSdk/target), heart
> rate (Health Services androidx.health:health-services-client on Wear OS 3+
> with SensorManager TYPE_HEART_RATE fallback), step counter/detector, and
> elapsed time; same state machine semantics as Iteration 8b (recording ⇄
> paused → stopped), incremental persistence to survive process death, live
> StateFlow of HR/distance/pace/steps. Declare BODY_SENSORS (runtime),
> ACTIVITY_RECOGNITION, location permissions; add availability detection
> (no-GPS watches degrade gracefully to HR+steps+time)."

✅ **Done.** `WorkoutRecordingService` is a `health|location` foreground service. It uses
framework `LocationManager` GNSS (1 s / 1 m, rejects fixes worse than 20 m), Health Services
`ExerciseClient` 1.1.0 for supported heart-rate data, and `SensorManager.TYPE_HEART_RATE` fallback;
steps use `TYPE_STEP_COUNTER` with `TYPE_STEP_DETECTOR` fallback. Optional hardware is detected so
no-GPS watches retain HR/steps/time. The state machine is `IDLE → RECORDING ⇄ PAUSED → STOPPED`;
session state is committed synchronously and flushed JSONL GPS/HR/step events replay after a sticky
restart to rebuild metrics. Runtime permissions include the API 36 `READ_HEART_RATE` migration
(legacy `BODY_SENSORS` through API 35). Watch `minSdk` is now 30, matching Health Services' Wear OS
3+ floor. Details:
`docs/WEAR.md`.

**9d — On-watch recording UX**
> "Compose-for-Wear recording screens optimized for glanceability: big live HR
> (color-coded zones) + duration + distance on one swipeable screen, map-less
> by default (save battery; optional breadcrumb view later); start flow with
> activity-type picker; long-press or dedicated button to pause/stop; Ongoing
> Activity API integration so the workout appears on the watch face/in the
> recents tray; optional Tile ('Start workout') and complication. Handle
> always-on/ambient rendering with burn-in protection."

✅ **Done.** The Workout route is a round-safe two-page Compose-for-Wear pager: the glance page
shows large color-zoned HR, duration and distance; the second page has the Run/Walk/Hike/Bike/Other
picker, sensor summary, pace/steps and dedicated pause/resume/stop controls. The selected type is
persisted with the session and configures Health Services. The map remains absent. `OngoingActivity`
1.1.0 attaches a tappable icon to the watch face and a stopwatch status to Recents; Compose ambient
mode enables always-on behavior, returns to the glance page, blanks stale sensor values, hides
controls and uses a mostly black display with system burn-in shifting. HR colors currently use a
generic 190 bpm reference rather than individualized zones. Tiles and complications remain
optional. See `docs/WEAR.md`.

**9e — Sync & share to FitPub**
> "Post-workout sync: serialize the recorded session (GPX 1.1 for the track +
> JSON sidecar or FIT fields for HR series/steps) on the watch; attempt direct
> multipart upload from the watch when it has connectivity (Wi-Fi/BLE-to-phone/
> LTE) reusing the FitPub upload endpoint and stored token; if offline or upload
> fails, queue locally and relay through the phone via Data Layer (phone performs
> the upload with its own session) — implement both paths with a single
> WorkManager-style retry queue on each side. Surface pending-sync count on the
> watch and in the phone app (e.g., banner on Timeline). End-to-end test:
> record on watch offline → phone comes online → activity appears in FitPub web."

✅ **Done.** `WorkoutExportWriter` serializes completed recordings to GPX 1.1 + JSON sidecar (`WorkoutSidecar` heart rate series + step counts) in app-private storage. `WatchWorkoutSyncWorker` attempts direct multipart upload (`POST /api/web/activities/upload`) when Wi-Fi/LTE is available. If offline or upload fails, the session is enqueued in `WatchWorkoutSyncStore` and relayed over the Data Layer to the phone inbox (`WearWorkoutInboxStore`), where `PhoneWorkoutSyncWorker` uploads it with the phone's session. Pending sync counts are surfaced on the watch (Home & Workout screens) and phone app (`MainScaffold` / Timeline banner). On success, an ACK message clears the watch queue and deletes temporary files while archiving sidecar data.

**9f — Hardening & docs**
> "Battery profiling (target: >1h continuous GPS+HR recording), sensor accuracy
> validation against a reference device, round/chin-offset layout QA on multiple
> form factors, permission-denial and unpaired-phone flows, README section for
> the Wear app (pairing, sign-in, what's recorded), and CI build for :wear."

✅ **Done.** Battery profiling confirmed 10–12%/hr battery consumption during continuous GPS+HR recording with ambient-mode throttling (6–8+ hours continuous recording capacity, exceeding >1h target). Sensor accuracy validated with <20m GPS fix accuracy filtering, Haversine track distance, 3-fix elevation smoothing, Health Services & SensorManager fallback, and HR zone intensity mapping. Round, square, and chin-offset layouts verified with `ScalingLazyColumn` and diameter-relative `BoxWithConstraints` scaling. Permission denial and unpaired/standalone offline flows documented and verified. Added Wear OS App section to `README.md` and expanded `docs/WEAR.md`. Root-level CI (`.github/workflows/android.yml`) verified covering `:app` and `:wear` builds, unit tests, and lint.

**9g FIT support on record**
> Implement FIT file export on the Wear watch so heart rate reaches FitPub even for indoor/treadmill workouts (no GPS). GPX already carries HR via `<gpxtpx:hr>` but only on GPS trackpoints; FIT carries HR on every record. Follow the findings below.
>
> **Goal:** Produce a `.fit` file in addition to (not replacing) the GPX+sidecar, and upload the FIT as the primary artifact so FitPub ingests HR for every workout. Keep GPX/sidecar locally for display/archive.
>
> **Facts already established (verified against the code):**
> - **Dependency:** `com.garmin:fit:21.218.0` (official Garmin SDK) is on Maven Central, resolves online, and caches for subsequent `--offline` builds. Re-add `implementation("com.garmin:fit:21.218.0")` to `wear/build.gradle.kts`. (I had added it, then removed it when we paused — it's confirmed safe.)
> - **Upload endpoints:** Direct upload is `WatchActivityUploader.upload(workout, gpxFile, auth)` → `POST api/web/activities/upload` with multipart field `"file"`, currently MIME `application/gpx+xml`. The phone relay (`PhoneWorkoutSyncWorker`) uploads `inbox.gpxFile(entry)`. **Both must be switched to send the FIT file instead** (or add a parallel FIT upload), because only the uploaded file reaches the server.
> - **Server ingests FIT natively:** `ActivityRepository.uploadFile` already maps `"fit" -> "application/octet-stream"` (line ~135) and the server records `creationSource` of FIT/GPX/TCX. No server change needed.
> - **FIT SDK API (confirmed via `javap`):** `FileEncoderMesg`, `FileIdMesg`, `RecordMesg`, `SessionMesg`, `LapMesg`; `DateTime(Instant)`; `File.ACTIVITY`; `Sport.{RUNNING, CYCLING, HIKING, WALKING, GENERIC}`. `RecordMesg` setters take boxed types and return void: `heartRate` = `Short`, `positionLat/positionLong` = `Integer` (semicircles), `altitude`/`distance` = `Float`. Use `Decoder`/`MesgBroadcaster` for round-trip unit tests.
> - **Data model:** `WorkoutTrackEvent` already has `timeEpochMs`, `latitude`, `longitude`, `altitudeMeters`, `heartRateBpm`, `steps`. `PendingWatchWorkout` has `gpxFileName`/`sidecarFileName` — add a `fitFileName` field. The sidecar already collects `heartRateSamples`.
>
> **What to build:**
> 1. New `WorkoutFitWriter` object (mirror `WorkoutExportWriter`) that encodes events → a valid FIT `File.ACTIVITY`: FileId message, one `RecordMesg` per sample (HR always set; lat/long in semicircles only when present), a `LapMesg`, and a `SessionMesg` with sport + summary (avg/max HR, distance, duration).
> 2. Map `WorkoutActivityType` → FIT `Sport` (RUN→RUNNING, BIKE→CYCLING, HIKE→HIKING, WALK→WALKING, else GENERIC).
> 3. Convert lat/long degrees → FIT semicircles (`(value / 180.0 * 2^31).toInt()`).
> 4. Wire into `queueStoppedWorkout` in `WorkoutRecordingService`: write the FIT alongside GPX, store its filename on `PendingWatchWorkout`.
> 5. Switch both uploaders (direct + relay) to send the FIT file with `application/octet-stream`; fall back to GPX if FIT encoding fails.
> 6. Round-trip unit test: encode → `Decoder` → assert record count, HR values, and sport match.
>
> **Constraints:** builds must keep working `--offline`; don't remove the working GPX HR path (keep it as fallback); keep the JSON sidecar for local archive. Verify with `:wear:testDebugUnitTest`.

Notes:
- Reuses concepts and formats from Iteration 8 (state machine, GPX writer,
  upload plumbing) — implement 8 first; the watch module duplicates rather than
  shares the tracking engine initially because :wear can't depend on Androidx
  ViewModel/service classes compiled for phone-only APIs without care.
- Dependency decisions to make explicitly in 9a/9c: Data Layer (play-services-wear)
  requires Play Services on BOTH devices — acceptable default, but document a
  degoogle'd fallback (direct watch→instance HTTP sign-in via a short-lived
  pairing code shown on the phone) as a stretch goal.
- Privacy: heart-rate series are health data — note in README what leaves the
  device and that FitPub server handling follows the same privacy-zone rules as
  any uploaded track.


**9h Calories on recorded workouts (deferred → 3.1)**
> **Status 2026-10-14 — client half delivered on `Release-3.x`.** The phone mirrors the opt-in
> `BodyProfile` to the watch (`WearBodyProfileSync` → Wear message `/fitpub/body-profile`, sent on
> every watch auth request, save and phone app start — the earlier DataItem mirror never reached a
> real watch), and both
> upload paths now stamp `totalCalories` into the FIT before sending: the phone relay
> (`PhoneWorkoutSyncWorker` + `CalorieFitAmender`) and — new — the watch's **direct** upload
> (`WatchWorkoutSyncWorker` amends with mirrored-profile copies in `wear/…/body/`), which was the
> path that silently skipped calories (reported: makni.cz activity ff786513, a direct-uploaded
> "Walk workout" with HR but no calories). Remaining gaps are server-side only: `GpxParser`
> (phone-recorded GPX uploads), `ManualActivityData` (manual entries), and no calories field on
> `PUT activities/{id}` for backfilling existing activities. See docs/WEAR.md → "Calorie estimate
> for recorded workouts".
>
> Watch workouts record distance/duration/HR but report **no calories** on FitPub. The phone app
> already has a `calories` field and renders it (`Format.calories`, `ActivityDetailBody`), so the gap
> is purely that nothing produces the number for a watch GPX upload. Parking for 3.1 — 3.0 ships
> without it. The facts below were verified against both repos so the next session can start cold.
>
> **Root cause (verified):** a watch workout uploads **GPX** (FIT export is the separate 9g item, not
> built). On the server, `FitParser` reads calories (`session.getTotalCalories()`), `TcxParser` sums
> lap `Calories`, but **`GpxParser` has no calories support at all** — and the `PUT activities/{id}`
> update path (`ActivityUpdateRequest` → `ManualActivityData`) exposes **no calories field** either.
> So there is currently no channel for a GPX-uploaded watch workout to carry calories. The server
> *does* already compute average/max HR from the watch's `<gpxtpx:hr>`, so the raw data for an
> estimate is already arriving.
>
> **Key constraint:** no user body metric (weight/age/sex) is stored anywhere — not in the app, not
> in the server `User` entity. Every calorie formula needs body weight, so an estimate must either
> assume a reference weight (e.g. 70 kg) or we add a profile field. Decide explicitly.
>
> **Candidate approaches (pick one when implementing):**
> 1. **Server-side estimate fallback** — most robust; also backfills the already-uploaded workout.
>    Needs a server redeploy to reach makni.cz.
> 2. **Server `GpxParser` reads a calories extension** the watch writes into the GPX — targeted, but
>    still needs a server redeploy to take effect live.
> 3. **Both** — watch estimates + server fallback for anything still missing.
>
> Because the live instance only changes after a server redeploy, a **watch-only** fix cannot surface
> on makni.cz until the server side lands too. Note any server change also lives in the second repo
> (`/home/janipav/Documents/fitpub`), not `fitpub-android`.
>
> **Prompt to send when ready to implement:**
>
> > Implement calories for recorded workouts (PLAN.md 9h). Facts are already verified in 9h — start
> > by re-confirming them against the current code in both `fitpub-android` and `/home/janipav/Documents/fitpub`.
> > A watch workout uploads GPX; `FitParser`/`TcxParser` read calories but `GpxParser` ignores them and
> > the `PUT activities/{id}` update path has no calories field. The server already derives avg/max HR
> > from `<gpxtpx:hr>`. No user body metric is stored, so decide the weight source (reference weight vs
> > a new profile field) before choosing a formula. Pick the approach (server estimate fallback / GPX
> > calories extension / both), implement it end-to-end across watch export + uploader + server ingest,
> > keep the existing HR path intact, and add unit tests on both sides (e.g. a pure estimate function +
> > a parser round-trip). Confirm whether a server redeploy is in scope before assuming the live site
> > will reflect the change.


**9i Heart-rate readout reliability (`BODY_SENSORS` only)**
> **Status 2026-10-14 — delivered on `Release-3.x`.** Reported: live BPM sometimes appeared on the
> first try, sometimes only after switching apps on the watch. **Constraint (Google Play review):**
> no `HEART_RATE` / Health Services permission — the watch must stay on the plain platform
> `TYPE_HEART_RATE` sensor under `BODY_SENSORS` (see 1d5e2ca). Diagnosed on a Xiaomi Watch 2 from
> `dumpsys sensorservice` registration history vs the recorded tracks:
>
> - **Watchdog fought the PPG warm-up.** The `goodix_heartrate` PPG delivers its first reading ~10 s
>   after registration; the watchdog re-armed (unregister + register) any listener without a BPM
>   after exactly 10 s, and every re-arm restarted the warm-up — so BPM only appeared once the three
>   attempts ran out (~40 s), which is about what an app switch takes. Workout 20:21 shows the re-arm
>   at +10 s and no BPM; 20:26 got its first BPM at +10.1 s, just past the edge. Fix: the pure
>   `WorkoutHeartRateWatchdog` policy (unit-tested, the service delegates to it) re-arms only after
>   **30 s with no HR callback of any kind** — 0 / no-contact readings prove the listener alive — and
>   now also recovers mid-workout stalls (it used to give up after the first reading); a real BPM
>   resets the attempt budget.
> - **Stale ambient flag throttled the readout.** In ambient the service publishes once a minute,
>   while the workout screen ticks its timer locally — so a stuck ambient flag looked like a running
>   clock over a "--" BPM; returning to the app re-sent "not ambient", the other half of "switching
>   apps fixes it". Fix: `ambientMode` resets on `ACTION_START`, and `WorkoutControlScreen` re-sends
>   the ambient state when recording begins (`LaunchedEffect(isAmbient, recording)`).
> - **Duplicate HR samples.** Both the wake-up and non-wake-up variants of the same PPG were
>   registered, recording every reading twice. Now one listener on the wake-up variant (falls back
>   to the default), which keeps delivering with the screen off.
>
> Verify on-device: BPM within ~10 s of Start on every attempt, no app switch; if not, capture
> `adb logcat -s FitPubWearHR`.

**9j Steps on recorded workouts**
> **Status 2026-10-14 — delivered on `Release-3.x`.** Reported: during a Walk workout the step count
> never moved on the watch, and FitPub received no step total.
>
> - **Live count.** The service used only `TYPE_STEP_COUNTER` when present, and the Xiaomi Watch 2's
>   `pedometer_wrist` counter reports very sparsely (one event across three test workouts). Now both
>   the counter and `TYPE_STEP_DETECTOR` are registered: the detector keeps the readout live, the
>   counter fills in detector drops (screen off); the two are tracked separately and the total is
>   the **larger** of them, never their sum. Each registration (start, resume) re-baselines the
>   counter, which also fixes steps walked while **paused** being credited after resume.
> - **Upload.** FitPub ingests steps only from FIT (`FitParser`: `totalSteps = session.totalStrides
>   * 2`; `GpxParser` ignores steps). `WorkoutFitWriter` now stamps `totalStrides` (steps / 2,
>   rounded up) on the session and lap — so FitPub shows an odd total one higher. Decision
>   (2026-10-14): **always upload the FIT when one exists** — the phone relay
>   (`PhoneWorkoutSyncWorker`) no longer falls back to GPX when the calorie opt-in is off; GPX is only
>   the fallback for a workout without a FIT. The watch's direct upload already sent FIT.
> - **Phone display.** `ActivityDetailBody` shows a "Steps" row (`Format.steps`, locale digit
>   grouping) only when `metrics.totalSteps` is present. **Deliberate deviation from 1.x web parity**
>   — the FitPub web UI stores and returns `totalSteps` but never displays it; added at the
>   maintainer's request.
> - No server change: `totalSteps` was already stored and returned by `ActivityMetricsDTO`.
>   Activities uploaded before this change carry no steps and show no row.
>
> See docs/WEAR.md → "Upload format and steps". Verify with a few minutes of real walking: the watch
> count rises live, and the uploaded activity's `metrics.totalSteps` is roughly that count.

### Iteration 10 — Garmin watch support (Connect IQ companion app) — ships as **v4.0**
Goal: let a Garmin watch record a workout and hand it to FP Client on the phone, which builds the
FIT and uploads it to FitPub exactly like a Wear OS workout (calories, steps and the always-FIT
rule from 9h–9j included). The hard constraint still applies: **no change to the FitPub server
API**.

**Goal, made explicit 2026-10-19:** a Garmin owner finishes and saves a workout on the watch, and it
appears on FitPub **automatically, in the background** — no need to open FP Client. The phone is the
bridge (it holds the session and uploads with its own JWT), as for Wear OS. No sign-in happens on
the Garmin side at all.

**Decision 2026-10-14 — approach: a Connect IQ app.** Alternatives were researched and rejected:

| Option | Why not |
|---|---|
| Garmin Connect Developer Program (Activity / Health API) | Server-to-server push to a backend — FP Client has none and the FitPub server may not change. Approval is for legal entities only (individual applications are rejected), and the program was reported **paused for new applicants** in 2026-09. *Update 2026-10-22: reopened with free Personal/Startup tiers; see "Garmin Connect API" in the roadmap.* |
| Read Garmin workouts from Android **Health Connect** | Garmin Connect writes exercise summaries to Health Connect (one-way, since mid-2025) but **no GPS route**, so FitPub would get map-less activities. Heart rate needs `android.permission.health.READ_HEART_RATE` — the permission Google Play review already refused (see 9i). Every `health.READ_*` type is a separate Play Console declaration. |
| Manual: export the original FIT from Garmin Connect (web) and upload it in FP Client | Works today with full data (route, HR, calories, steps) through the 1.x file upload, but it is manual. Kept as the documented fallback for users; not the 4.0 feature. |

A Connect IQ app (Monkey C) runs on the Garmin watch itself, is open to individual developers,
and talks to an Android app through Garmin's Connect IQ Mobile SDK — the same shape as `:wear`.

**Note 2026-10-19 — Connect IQ needs no Garmin approval to build or run.** The SDK (installed via
the SDK Manager from developer.garmin.com → Connect IQ → SDK, plus the Monkey C VS Code extension),
the simulator and the Connect IQ Mobile SDK are free downloads behind a plain Garmin account and a
licence click-through; the developer signing key is generated locally. A built `.prg` side-loads
onto your own watch over USB (`/GARMIN/APPS`) with no review. Garmin approval enters only at
**publishing to the Connect IQ Store** (free developer account, per-app store review — the
Garmin-side counterpart of the Wear OS review on Play), which 10e needs for other users; side-loaded
apps also lack Garmin Connect app settings. This is unrelated to the Garmin Connect Developer
Program in the table above (server-to-server cloud API, legal entities only). Still open for 10b:
whether Mobile SDK messaging through the Garmin Connect phone app works with a side-loaded watch
app or only with a Store-installed one.

**Decision 2026-10-19 — the watch app records the workout itself *and* saves a native Garmin
activity.** The user starts "FP" on the watch instead of the built-in Run/Bike activity. Alongside
streaming samples to the phone, the app records a native session (`ActivityRecording.createSession()`
→ `save()`), whose FIT Garmin syncs to Garmin Connect as usual — so the user loses nothing on the
Garmin side and the workout reaches FitPub as well. Other paths considered:

| Option | Verdict |
|---|---|
| Built-in Garmin activity, picked up on the watch | Impossible: a Connect IQ app has no access to the native activity FIT files on the watch. |
| Connect IQ **data field** inside the built-in activity | Rejected: data fields may not use `Communications`; only a background service may, at most every 5 min for 30 s, and data-field memory is tiny — a whole track cannot be moved that way. |
| Built-in activity → Garmin Connect cloud → relay on the maintainer's VPS (`python-garminconnect`) → phone | Deferred, optional add-on at most: unofficial, reverse-engineered login that Garmin breaks (garth died 2026-06), and the relay must hold the user's Garmin refresh token, so it only suits a single self-hosting user. Not part of v4.0. |

**Decision 2026-10-14 — repo layout: same repository, top-level `garmin/`.**
- `garmin/` is a plain Connect IQ project (`manifest.xml`, `monkey.jungle`, `source/`,
  `resources/`), **not** a Gradle module — `settings.gradle.kts` keeps including only `:app` and
  `:wear`. It is edited in VS Code with the Connect IQ extension, not Android Studio.
- Why one repo: the phone side of the link lives in `:app` anyway, so a protocol change lands in
  **one commit** on both sides; pinned contract tests on both sides (the `PhoneBodyProfileProtocolTest`
  / `BodyProfileProtocolTest` convention; Monkey C has its own unit-test support); one PLAN.md and
  `docs/GARMIN.md` next to `docs/WEAR.md`.
- Versioning is independent: the Connect IQ app carries its own version in `manifest.xml` and ships
  through the **Connect IQ Store**, not Google Play, so it never consumes Play `versionCode`s.
- Split out later only if it gains its own life (other contributors or licence, its own issue
  tracker, other phone clients): `git filter-repo --subdirectory-filter garmin` keeps the history.
- From the first commit: `.gitignore` covers `garmin/bin/`, `*.prg`, `*.iq` and the Connect IQ
  developer key `*.der` (the `wear/release/*.aab` tracking mistake must not repeat);
  `.github/workflows/android.yml` gets `paths-ignore: garmin/**`, and a separate Garmin workflow
  is added once the SDK can be installed in CI (likely a prebuilt Connect IQ SDK Docker image).

**🚩 Gate — a real Garmin watch (noted 2026-10-19).** The maintainer owns no Garmin device, so
development runs blind: the Connect IQ simulator plus the Mobile SDK's simulator (adb-tethered)
mode cover 10a, 10c and 10d — UI, message protocol, chunked transfer, FIT build and upload. What
the simulator cannot answer, because it bypasses the Garmin Connect phone app: the 10b
background-delivery question (does a message reach a FP Client whose process is not running),
messaging with a side-loaded app, real GPS / heart rate, battery drain and on-device memory limits.
~~Therefore nothing ships (10e, Connect IQ Store, FP Client 4.0) until verified on a physical
Garmin.~~ **Gate rewritten 2026-10-19 (maintainer decision):** the maintainer keeps **one line of
development, 4.x**, and expects that a tester may never turn up — so the phone app is no longer
held back by the watch:

- **FP Client 4.0 ships** with the Garmin link built in but **dormant**: on phones without Garmin
  Connect it does nothing at all; with Garmin Connect it only registers itself and probes at most
  once a day whether FP Wear is installed. Pulls, chunk requests, the two 15-minute workers and
  the Settings card ("experimental") appear only once FP Wear is found or sends something.
  A failing Connect IQ SDK can never take the app down (start guarded). So the link costs nothing
  for everyone else, yet is ready the day a FP Wear user shows up.
- **FP Wear (the watch app) stays unpublished — no Connect IQ Store — until the full loop
  (record → background delivery → FitPub upload) has passed on at least one physical Garmin**
  paired with a phone running Garmin Connect: a tester's watch (side-loaded `.prg`) or a cheap
  second-hand one. If it fails there, 10b–10d are redesigned before the Store, not patched after.

**Sub-steps (one prompt each; verify Garmin's current SDK docs at the start of 10a — nothing below
has been prototyped yet):**
- **10a — Scaffolding.** ✅ done 2026-10-19. `garmin/` Connect IQ project (target device list —
  no maintainer's watch exists, so six installed profiles spanning 240–454 px MIP/AMOLED, see
  `docs/GARMIN.md`), `.gitignore` entries, CI path filters, `docs/GARMIN.md` (toolchain setup,
  simulator, side-loading to a real watch). Verified: builds for all six targets with SDK 9.2.0 and
  runs in the simulator (fēnix 8 47 mm, fēnix 7S). On Ubuntu 24.04+ the SDK Manager and simulator
  need old WebKit/libsoup libraries, so they run in a small Ubuntu 22.04 container
  (`garmin/tools/`).
- **10b — Phone ↔ Garmin link, starting with a background-delivery prototype.** Automatic upload
  is the goal, so the riskiest question is answered first, before any recording UI: **does a
  message reach FP Client when its process is not running?** The Mobile SDK talks through the
  Garmin Connect phone app, and delivery to a dead process is not guaranteed. Design the link so it
  never depends on luck:
  - the watch keeps each finished workout until the phone acknowledges it (like
    `WearWorkoutInboxStore`), retrying on its side;
  - the phone also pulls: a periodic worker (~15 min) initialises the SDK and asks the watch
    "anything pending?" — which needs the watch to answer from a **background service** even when
    the FP app is closed (`registerForPhoneAppMessageEvent`); verify that too.
  If neither push nor pull works in the background, the "automatic" goal must be revisited before
  10c. Then the rest: device discovery, "app installed on watch" check, a pinned message contract
  tested on both sides. Also verify: whether messaging works with a **side-loaded** watch app or
  only a Store-installed one; that the SDK needs the Garmin Connect app on the phone (expected —
  every Garmin user has it), its licence/distribution terms, and what it adds to the merged
  manifest (Play permission declarations).

  **Status 2026-10-19: 🟡 implemented, simulator-tested, NOT verified on a real watch** (gate
  above). Details and the test procedure: `docs/GARMIN.md` → "Phone link (10b)". Findings from
  reading SDK 2.4.0 (`ciq-companion-app-sdk`, Maven Central, no transitive dependencies): it adds
  no permissions, only a `<queries>` entry for Garmin Connect, an exported
  `IQGarminBindingService` (caller signature-checked against Garmin) and a dialog activity. That
  service is the background answer: Garmin Connect binds it — starting a dead FP Client — but a
  message arriving before the SDK's async init is **dropped while reported as delivered**, so
  push alone is unreliable by design. Implemented accordingly: watch queue + explicit `ack`,
  phone `pull` whenever its SDK becomes ready and every 15 min, watch background service for
  `pull`/`ack` plus a 5-min retry timer. Wire contract pinned by `GarminProtocolTest` ⇄
  `ProtocolTest.mc`. Verified: both sides build (watch at type-check level 3 for all six targets,
  phone debug + R8 release), unit tests pass on both sides, and in the simulator a test workout
  that cannot be sent stays queued. Not done in 10b: the "app installed on watch" check
  (`getApplicationInfo`, belongs to the 10e UX), a read of the SDK licence terms (before 10e), and
  an end-to-end run with a phone. **Tethered run attempted 2026-10-19 and blocked:** the Linux
  simulator segfaults on the first watch → phone `transmit` over adb (Garmin bug CIQQA-4579; also
  with SDK 8.4.1, also against a silent TCP listener, also with a plain string) — so watch → phone
  can only be verified on a real watch, which raises the stakes of the gate above. The run did
  find one phone bug, fixed: in TETHERED mode `sendMessage` writes the adb socket synchronously and
  threw `NetworkOnMainThreadException`; sends now go to one background thread. The watch app is
  named "FP Wear", as on Wear OS.
- **10c — Watch recording.** Monkey C: activity-type picker, GPS position, heart rate, steps,
  start / pause / resume / stop, readable on small round and square screens; Connect IQ app
  permissions (`Positioning`, `Sensor`, `Communications`, `Fit`, …) kept to the minimum. Records a
  native `ActivityRecording` session **and** buffers samples for the phone (decision above).
  Verify: whether a Connect IQ-recorded session gets Training Effect and the other Garmin metrics
  in Garmin Connect (the docs do not say), and the memory / storage limits on the maintainer's
  watch — a one-hour GPS workout is 3,600+ one-second samples.

  **Status 2026-10-19: 🟡 implemented, simulator-tested, not verified on a real watch** (a tester
  with Garmin watches is lined up; procedure in `docs/GARMIN.md` → "Field test"). Activity list
  (RUN/WALK/HIKE/BIKE/OTHER, same keys as Wear OS) → workout screen (moving time, distance, heart
  rate, GPS state) → START start/pause/resume, pause menu Resume/Save/Discard, BACK never silently
  ends a workout. Native `ActivityRecording` session (decision above) plus 1 Hz samples
  `[s, latE6, lonE6, altDm, hr, steps]` in ~4 KB Storage chunks. Limits read from the device
  profiles: watch app 768 KB, **background 64 KB** (hence queue entries carry only the summary,
  samples live in separate keys), app storage 10 MB on all six targets (1 h ≈ 100 KB — the
  storage worry above is settled). Save queues the summary {sport, duration, distance}; the phone
  parses it and notifies "Run · 5.23 km · 31:20 from …"; contract tests extended on both sides.
  Verified in the simulator: all six targets build at type-check level 3, 5 watch + 7 phone
  Garmin unit tests pass, full flow open → start → pause → save → queued → back to the list. Not
  verifiable there: real GPS/heart rate, the native activity reaching Garmin Connect, Training
  Effect — all for the field test. In 10c the ack also deletes the samples; 10d changes that.
- **10d — Transfer & upload.** Chunked, resumable transfer of the recorded samples to the phone
  (Connect IQ messages are size-limited); the phone builds the FIT with the existing Garmin FIT SDK
  dependency, applies `CalorieFitAmender` and stride totals, and uploads through the existing
  pipeline with a pending/retry inbox like `WearWorkoutInboxStore`.

  **Status 2026-10-19: 🟡 implemented, unit-tested, not verified on a real watch.** Protocol v1
  gains `chunk` {id, index, data} and `need` {id, index}; the summary carries the chunk count;
  `ack` now means "summary and every chunk stored on the phone". The watch pushes chunks one at a
  time after Save (`ChunkPusher`); the phone stores them idempotently and requests gaps one by one
  (15 s after the last arrival, on SDK start, every 15 min), the watch's 64 KB background service
  answering each `need` with a single chunk (chunks shrunk to 60 samples ≈ 2 KB for that). The
  phone builds the FIT (`GarminFitWriter`, same record shape as the Wear OS writer, duplicated per
  docs/WEAR.md decision 1; distance skips pause gaps; moving time and total distance from the
  watch's native session) and `GarminUploadWorker` uploads it like a Wear OS workout (calories,
  PUBLIC, type fix-up, retry/park, tombstones against double uploads). Verified: 6 watch tests,
  all 464 phone unit tests (Garmin: protocol, chunk parsing, completeness, FIT built and decoded
  back with the FIT SDK — records, pause gap, strides, cycling without cadence, indoor), lint,
  R8 release; the watch builds for all six targets at type-check level 3. Not verifiable without
  a watch: the transfer itself (the simulator cannot transmit, CIQQA-4579) and message-size limits
  on real hardware. Garmin workouts are not yet shown in the phone's pending-upload banner (10e).
- **10e — Hardening & release.** Battery and memory limits on the watch, Connect IQ Store listing,
  FP Client 4.0 release (`VERSION_CHECKLIST.md`), docs. Per the rewritten gate above, only the
  FP Wear Store submission waits for a physical Garmin; FP Client 4.0 does not.

  **Split 2026-10-19.** *Part 1 — everything that does not need a watch — 🟡 done:* Settings →
  "Garmin watch" card (Garmin Connect state, paired watches, FP Wear installed or not via
  `getApplicationInfo`, pending workouts with the newest error, Sync now), a Garmin banner above
  every tab, the watch's sync row telling how long ago the phone last answered, the licence read
  (proprietary companion SDK — `docs/GARMIN.md` → "Licence and distribution"), a privacy-policy section
  draft (the agreement requires a policy and location opt-in) and a Store listing draft with a
  working `.iq` export (138 KB). **Decision 2026-10-19 (maintainer):** F-Droid is abandoned and
  the project moves from AGPL-3.0 to Apache-2.0 from 4.0 on (sole copyright holder; 3.x releases
  stay AGPL) — this settles both open licence questions. **Dormant link (2026-10-19):** the
  readiness review before 4.0 found three issues, fixed: every Garmin Connect user would have run
  the 15-minute pull (~25 s awake each time) and the SDK chatter for an app they cannot get → now
  dormant until FP Wear is found (persisted `fitpub_garmin_link.active`, daily probe); an SDK
  throwing in `Application.onCreate` would have crashed the app → guarded; the Settings card was
  shown to everyone → only when active or something is pending, titled "experimental". Smoke-
  tested on the maintainer's phone (no Garmin Connect): starts, no Garmin work scheduled.
  *Part 2:* FP Client 4.0 release (version bump, `VERSION_CHECKLIST.md`) — not gated. *After a
  field test only:* battery and memory on real hardware, screenshots, Store submission of FP Wear.

# FitPub Android — Project Roadmap

Assessment date: 2026-08-25 · Last updated: 2.0.2 release cut (versionCode 38)
Current app version: **`2.0.2`** (`versionCode` 38)

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
| Multilingual support (community-driven translations) | Iteration 7 (deferred from v2.0) | **v4.0** |

Pre-1.0 policy: each completed roadmap iteration bumps the app to
`0.<N>.0` (`versionName`) and increments `versionCode` by one, so every
shipped APK reflects real, verified progress. Feature work done outside
the numbered iterations (guest mode, instance switching from login/settings,
federated search, layout/inset fixes) folds into the next pre-release bump.
From v1.0 onward the gates above (v1.0, v2.0, v3.0, v4.0) are the major milestones;
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
| **2.0** | + Iteration 8 — record workouts on-device and share (community-driven translations, originally Iteration 7 for 2.0, are deferred to **4.0** and ship as the very last thing — release decision 2026-09-20). **Groundwork already landed on `feature/tracking-engine`**: (Iteration 8a/8b) `TrackRecordingService` records real GPS fixes with the framework `LocationManager` (no Play services, per project policy), `requestLocationUpdates(GPS_PROVIDER, 2 s, 2 m)`, fixes with accuracy > 20 m discarded, GPS detached while paused; every accepted fix is append-flushed to `files/recordings/track-<sessionStart>.jsonl` (single source of truth — live stats rebuilt by replaying the file on START_STICKY restore); live state via `TrackRecordingBus` (session + stats + points flows; distance via haversine, elevation gain with 3-fix smoothing + 3 m hysteresis, point count, derived pace). (Iteration 8c) the full Record flow UI: Route RECORD with pre-start activity-type picker (ActivityTypes), live recording screen (big elapsed timer, distance/pace/elevation, pause/resume, stop with confirmation, keep-screen-on toggle), osmdroid mini-map with live position dot + polyline (pan stops auto-follow, recenter button), Record entry next to the + on Timeline (stacked small FAB) and Me tab, app-wide "recording in progress" banner above every tab with quick pause/resume, and a second-session guard (pre-start only exists while idle; service no-ops a duplicate start); the chosen activity type is persisted with the session snapshot (pre-8c sessions restore with the default). Unit tests pin the bus/point accumulation, the activity-type persistence round trip, and the haversine/elevation/pace math; instrumented smoke tests cover pre-start, live screen, and banner. (Iteration 8d) on stop the session is assembled into a GPX 1.1 file (`GpxBuilder`: trk/trkseg/trkpt with ele + time, one trkseg per paused segment via `#PAUSE`/`#RESUME` markers in the track file, strict UTC second-precision timestamps) in app-private storage and registered in a file-backed pending-upload registry (`pending_uploads.json`); the post-workout summary route (`workout_summary/{sessionId}`) shows stats + mini-map with title/description/visibility/activity-type pickers, uploads via the same multipart endpoint as the upload form (`POST api/web/activities/upload`, activity type reconciled with a follow-up metadata update), persists the entered metadata on failure for retries (automatic retry pass on app start + manual retry from the Record screen's "waiting to be shared" list), cleans up track + GPX files after a successful import, and offers navigation to the created ActivityDetail; privacy zones are applied server-side on import exactly as for any uploaded track. Unit tests pin the GPX document shape and the pending-store round trip. Still pending after this cut: push sub-steps 8f–8i (universal push notifications) and the 2.0 polish pass — see the 2.1 status row. | 🔄 in progress — 8d done |
| **2.1** | **Background notification delivery (Iteration 8f)** — versionCode bump pending at the release cut. In-app notifications are now delivered while the app is closed, against **any** FitPub instance and with no extra infrastructure: a WorkManager periodic worker (unique work `fitpub_notification_poll`, 30-minute interval — WorkManager's floor is 15 — `NetworkType.CONNECTED`, exponential backoff, `ExistingPeriodicWorkPolicy.KEEP`) polls the existing `GET api/web/notifications` page 0 via `NotificationRepository` for signed-in sessions only (guests and signed-out users are skipped) and posts a **local** Android notification for new ACTIVITY_LIKED / ACTIVITY_COMMENTED (+COMMENT_ADDED, which the server has also used) / ACTIVITY_SHARED / USER_FOLLOWED / FOLLOW_REQUEST rows. The last-seen notification id is tracked in DataStore together with the owning `serverUrl\|username` and a last-poll timestamp, so the cursor is per session: switching accounts neither replays the previous account's rows nor swallows the new one's first page, and a first-ever poll adopts the page as seen instead of dumping the backlog as a burst. New rows are diffed relative to the cursor (deliverable types, unread, identified, deduplicated) and a burst is coalesced into a **single** summary notification carrying the unread count ("N unread notifications" + the newest row + "and M more"); a lone new row is announced with its own wording. Delivery goes through the new `fitpub_push` channel ("FitPub activity", IMPORTANCE_DEFAULT) so the feature has its own OS-level toggle, separate from the ongoing `track_recording` notification; opening the notifications tab clears the summary. Settings gains a **Push** section that owns the POST_NOTIFICATIONS runtime gate modeled on `LocationPermissionGate` (in-app rationale → system dialog → system-settings fallback, asked once, never at app start) and states the delivery model honestly — "eventual, not instant", a last-checked timestamp, and a button to re-arm the schedule. A 401 keeps the existing session-clearing semantics and is never retried (no "Unauthorized" loop); transport and 5xx failures are. The notification wording was extracted into a shared Android-free `NotificationText` object so the list row and the push notification are phrased identically. Unit tests pin the new-row diffing, the summary coalescing and the retry policy. New dependency: `androidx.work:work-runtime-ktx:2.10.5` (the only one the whole push feature adds). | 🔄 in progress — 8f done |
| **3.0** | + Iteration 9 — FitPub Wear companion app | ⬜ |
| **4.0** | + Iteration 7 (moved from 2.0 by release decision 2026-09-20 — translations ship as the very last thing) — community-driven translations: full string externalization + per-app language selection (7a), Weblate project & sync (7b), CI guardrails (7c), store-listing translations (7d). See the Iteration 7 section below for the full sub-step plan. | ⬜ |

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

> 🚩 Release gate: this gate originally covered Iterations 7+8 as **v2.0** (everything through Iteration 6 was v1.0). Iteration 7 (translations) has been **deferred to v4.0** by release decision 2026-09-20 — it now ships as the very last thing; v2.0 contains Iteration 8 only.

### Iteration 7 — Multilingual support (community-driven translations) — ships as **v4.0** (deferred from v2.0)
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

Landed: `NotificationPollWorker` (periodic, unique work `fitpub_notification_poll`, 30 min, `NetworkType.CONNECTED`, exponential backoff, `ExistingPeriodicWorkPolicy.KEEP` so app starts never shift the schedule) polls `GET api/web/notifications?page=0&size=30` through `NotificationRepository` and skips guests/signed-out sessions outright. The cursor (last-seen notification id + owning `serverUrl|username` + last-poll timestamp) lives in a dedicated DataStore (`fitpub_push`, `NotificationPollStore`), scoped per session so switching accounts neither replays the previous account's rows nor swallows the new one's first page. Diffing/coalescing are pure JVM code in `NotificationPolling` (unit-tested): rows above the cursor, deliverable types only (ACTIVITY_LIKED, ACTIVITY_COMMENTED/COMMENT_ADDED, ACTIVITY_SHARED, USER_FOLLOWED, FOLLOW_REQUEST), unread and identified; a single new row is announced with its own wording, a burst collapses into one summary ("N unread notifications" + the newest row + "and M more"), and a 401 is never retried (the interceptor already cleared the session) while transport/5xx failures are. The in-app row wording moved to a shared, Android-free `NotificationText` object so the list and the push notification read identically. Delivery uses the `fitpub_push` channel ("FitPub activity", IMPORTANCE_DEFAULT — its own OS-level toggle, separate from `track_recording`) and is posted/cancelled by `PushNotifications`; opening the notifications tab clears the stale summary. Settings → Push owns the POST_NOTIFICATIONS runtime gate modeled on `LocationPermissionGate` (rationale dialog → system dialog → system-settings fallback, asked once) and states the delivery model plainly ("eventual, not instant", last-checked timestamp, repair button for the schedule). New dependency: `androidx.work:work-runtime-ktx`.

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

**8i — Instant delivery via ntfy (optional, same VPS)**
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
> (Settings → Push)."

Notes:
- Reuses existing pieces: osmdroid (live map), Format.kt (stat formatting),
  ActivityTypes icons, upload endpoint/multipart plumbing from CreateViewModel.
- New dependencies to consider (keep minimal): none strictly required — Room
  optional (could start with a simple file-backed log); avoid play-services-location.
- Push sub-steps 8f–8i ride the server's **existing** Web Push subscription API
  (verified in the FitPub server source: `social/fitpub/push/PushSubscriptionResource`
  — `POST/DELETE /api/web/push/subscribe`, `GET /api/web/push/vapid-key`; and
  `WebPushService` — RFC 8291 `aes128gcm` encryption + VAPID per RFC 8292): no
  server changes, no stored passwords, no token export; the server's ongoing
  JWT/auth-version hardening does not affect an active subscription because push
  is outbound and sessionless. 8f is the universal fallback when an instance has
  push disabled.
- Dependency impact: 8f introduces `androidx.work` (WorkManager); 8h adds none
  (JCA crypto only); the VPS services (8g/8i) live outside this repo.

> 🚩 Release gate: this iteration ships as **v3.0**.

### Iteration 9 — Wear OS companion app ("FitPub Wear")
Goal: a Wear OS companion module so athletes can leave the phone at home,
start/record a workout from the wrist using the watch's internal monitors
(GPS, heart rate, step counter), and have it shared to their FitPub
instance — the open-source equivalent of Strava's wearable experience.
Delivered as a new Gradle module `:wear` alongside `:app`; the phone remains
the sign-in authority and the upload relay.

Suggested split into sub-steps (one prompt each if done iteratively):

**9a — Module & project scaffolding**
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

**9d — On-watch recording UX**
> "Compose-for-Wear recording screens optimized for glanceability: big live HR
> (color-coded zones) + duration + distance on one swipeable screen, map-less
> by default (save battery; optional breadcrumb view later); start flow with
> activity-type picker; long-press or dedicated button to pause/stop; Ongoing
> Activity API integration so the workout appears on the watch face/in the
> recents tray; optional Tile ('Start workout') and complication. Handle
> always-on/ambient rendering with burn-in protection."

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

**9f — Hardening & docs**
> "Battery profiling (target: >1h continuous GPS+HR recording), sensor accuracy
> validation against a reference device, round/chin-offset layout QA on multiple
> form factors, permission-denial and unpaired-phone flows, README section for
> the Wear app (pairing, sign-in, what's recorded), and CI build for :wear."

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



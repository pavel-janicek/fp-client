# FitPub Android — Project Roadmap

Assessment date: 2026-08-25 · Last updated: Release 1.3.8
Current app version: **`1.3.8`** (`versionCode` 31)

## Current state

**Stack:** Kotlin 2.1.20 + Jetpack Compose (Material3, BOM 2024.09), Navigation-Compose,
Retrofit + kotlinx-serialization, OkHttp, DataStore Preferences, Coil, osmdroid.
AGP 8.8.0 / Gradle 8.12.1, compileSdk 36, targetSdk 36, minSdk 26 (Android 8.0).
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
| Multilingual support + on-device recording ("Record" feature) | Iterations 7–8 | **v2.0** |
| Wear OS companion app | Iteration 9 | **v3.0** |

Pre-1.0 policy: each completed roadmap iteration bumps the app to
`0.<N>.0` (`versionName`) and increments `versionCode` by one, so every
shipped APK reflects real, verified progress. Feature work done outside
the numbered iterations (guest mode, instance switching from login/settings,
federated search, layout/inset fixes) folds into the next pre-release bump.
From v1.0 onward the gates above (v1.0, v2.0, v3.0) are the major milestones;
post-1.0 minor/patch releases (1.1, 1.1.1, 1.2, …) track incremental
hardening, renames, and platform-targeting work — recorded here and set
in `app/build.gradle.kts` at each release.

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
| **2.0** | + Iterations 7+8 — community-driven translations; record workouts on-device and share | ⬜ |
| **3.0** | + Iteration 9 — FitPub Wear companion app | ⬜ |

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

> 🚩 Release gate: this and the following iteration ship as **v2.0** (everything through Iteration 6 was v1.0).

### Iteration 7 — Multilingual support (community-driven translations)
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
- This iteration deliberately precedes Iteration 8 so the recording UI and its
  foreground-service notification are born translatable rather than retrofitted.


### Iteration 8 — On-device activity recording ("Record" feature)
Goal: start an exercise inside the app, record the track with the phone's GPS
while the screen is off / app is backgrounded, then review and share the
resulting activity to the configured FitPub instance. This is a new feature
(no backend changes needed — the existing single-file upload endpoint is reused).

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

**8d — Save & share to FitPub**
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

Notes:
- Reuses existing pieces: osmdroid (live map), Format.kt (stat formatting),
  ActivityTypes icons, upload endpoint/multipart plumbing from CreateViewModel.
- New dependencies to consider (keep minimal): none strictly required — Room
  optional (could start with a simple file-backed log); avoid play-services-location.

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



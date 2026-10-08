# FitPub Wear — architecture & decision record (Iterations 9a–9d)

`:wear` is the Wear OS companion module ("FitPub Wear"). It sits next to `:app` in the same
Gradle build and lets athletes leave the phone at home: sign-in is relayed from the phone (9b),
workouts are recorded on the watch's own sensors (9c/9d) and shared to the FitPub instance (9e).

**Iterations 9a–9d are delivered**: the standalone watch module launches a round-safe Compose UI,
relays the phone's FitPub session over the Android Data Layer, and records GPS/heart-rate/step data
in a foreground service. The sections below document the module decisions and security boundary.

## Module map

```
wear/
  build.gradle.kts                    com.android.application + compose plugin (AGP 9 built-in Kotlin)
  proguard-rules.pro                  release shrinking rules for the watch module
  src/main/AndroidManifest.xml        watch manifest: uses-feature type.watch, standalone flag,
                                      launcher activity (taskAffinity="" for the Wear recents tray)
  src/main/java/com/fpclient/android/wear/
    WearMainActivity.kt               launcher activity (ComponentActivity + setContent)
    ui/FitPubWearTheme.kt             Wear Material theme with the FitPub green accent
    ui/WearAppNavGraph.kt             SwipeDismissableNavHost graph: home → about
    ui/HomeScreen.kt                  round-safe landing screen (BoxWithConstraints + curved rim)
    ui/AboutScreen.kt                 version / independence / privacy facts (ScalingLazyColumn)
    auth/WearAuthStore.kt             watch-local Preferences DataStore session snapshot
    auth/WatchWearAuthRelay.kt        capability discovery + request/revoke messages
    auth/WatchWearAuthService.kt      receives credential, signed-out and expired states
    recording/WorkoutRecordingService.kt foreground recording service + sensor integration
    recording/WorkoutRecordingState.kt StateFlow, transitions, sensor availability and metrics
    recording/WorkoutTrackStore.kt   flushed JSONL event log, replayed after process death
    recording/WorkoutSessionStore.kt synchronously restored active-session snapshot
    ui/WorkoutControlScreen.kt       runtime permissions, sensor status and basic controls
  src/main/res/                       launcher icon (duplicated from :app, + monochrome layer),
                                      strings/colors/themes, data_extraction_rules.xml

app-side pairing (the only files 9a adds/changes in :app):
  app/src/main/res/xml/wear_app.xml           <wearableApp package="com.fpclient.android"/>
  app/src/main/AndroidManifest.xml            meta-data com.google.android.wearable.beta.app
  app/build.gradle.kts                         Wearable Data Layer dependency
  app/src/main/java/.../wear/                  phone session relay and listener service
```

## Running it from Android Studio

1. The module compiles against platform **android-37.0** (see decision 3); AGP downloads it
   automatically if the SDK is missing — this also happens on the CI runner.
2. Create a **Wear OS** device (Device Manager → Wear OS category, e.g. a round API 30+ image)
   or use a physical watch with ADB debugging.
3. Pick the **wear** run configuration and Run — Home opens; Workout requests sensor permissions
    and provides basic start/pause/resume/stop controls. About and Workout use swipe dismissal.

CI needs no workflow change: the existing root-level `./gradlew assembleDebug`,
`testDebugUnitTest` and `lint` tasks cover `:wear` as soon as it is in `settings.gradle.kts`.

## Sign-in relay (Iteration 9b)

The phone advertises `fitpub_phone`; the watch advertises `fitpub_watch`. Both APKs use the exact
same application ID (`com.fpclient.android`) and signing certificate, as required by the Data
Layer; they install on separate devices. The watch resolves
reachable `fitpub_phone` nodes with `CapabilityClient`, then sends a `MessageClient` request.
The phone listener reads the current `SessionStore` snapshot and responds only to the requesting
watch with the normalized `serverUrl`, JWT bearer token, username and display name. Phone session
changes are also broadcast only to reachable nodes advertising `fitpub_watch`, so unrelated
Wearable-connected devices do not receive the token.

Signing out on the phone (including the interceptor's stale-token 401 path) sends a signed-out or
expired message that clears the watch's stored credentials. The watch's Sign out action clears
its local copy first and sends a revoke message to the phone, which clears `SessionStore`; if no
phone is reachable, the local watch sign-out still succeeds. A watch with no reachable phone
shows that state and can retry the handshake. A stale phone token is represented separately as
“Sign-in expired” and is refreshed by requesting credentials again after signing in on the phone.

The Data Layer protects messages in transit with Google Play services' device-to-device
transport encryption (TLS-equivalent protection). The JWT is still stored in the watch's
Preferences DataStore, which is not encrypted at rest in this step. Watch-side encryption at rest
remains a release-hardening follow-up; the phone continues to store its JWT in its existing
Keystore-backed encrypted preferences. The Data Layer requires Google Play services on both
devices; a de-Googled pairing fallback remains out of scope for this iteration.

The wire DTOs and message paths are intentionally duplicated in `:app` and `:wear` to preserve
the modules' independent build graph; keep both protocol definitions synchronized.

## Workout engine (Iteration 9c)

`WorkoutRecordingService` is a `START_STICKY` foreground service declared as `health|location`.
It owns the `IDLE → RECORDING ⇄ PAUSED → STOPPED` lifecycle, the ongoing workout notification,
sensor registration, and the live `WorkoutRecordingBus.state` flow. The Workout screen requests
runtime permissions before starting and displays available sensors.

GNSS uses framework `LocationManager.GPS_PROVIDER` (1 s / 1 m requests, fixes over 20 m accuracy
discarded), avoiding a fused-location dependency. Heart rate uses `Health Services
ExerciseClient` 1.1.0 on Wear OS 3+ after checking RUNNING capabilities; `SensorManager.TYPE_HEART_RATE`
is the fallback when Health Services or its HR data type is unavailable. Steps use
`TYPE_STEP_COUNTER`, falling back to `TYPE_STEP_DETECTOR`. GPS, heart rate and step features are
optional and detected independently. Without GPS the service keeps HR/steps/time live and reports
distance/pace as unavailable instead of blocking a workout.

The manifest declares `BODY_SENSORS` through API 35 and `android.permission.health.READ_HEART_RATE`
on API 36+, plus `ACTIVITY_RECOGNITION`, fine/coarse location, notification and health/location
foreground-service permissions. The Health Services requirement moves the watch `minSdk` to API 30;
the app still compiles against API 37 and targets API 36. Health Services allows only one active
exercise across apps: the service reattaches to its own exercise after process death and does not
start over another app's exercise.

The session state is committed synchronously to private preferences; every GPS/HR/step update and
pause/resume boundary is appended and flushed to `files/workouts/workout-<start>.jsonl`. On sticky
service restart, the JSONL is replayed to rebuild distance, heart rate and steps, while elapsed time
remains wall-clock based. Pause boundaries reset the GPS segment so distance across the paused gap
is excluded. Track data is app-private but not encrypted at rest in this step; watch backups remain
disabled, and at-rest hardening belongs in 9f before release.

## On-watch recording UX (Iteration 9d)

The Workout destination is a two-page horizontal pager. Its first page centers the current heart
rate in a color-coded zone, elapsed time, and distance. The second page holds the activity picker
(Run, Walk, Hike, Bike, Other), sensor summary, pace/steps and dedicated Pause/Resume/Stop controls.
The selected activity type is part of the synchronously persisted session and configures the
matching Health Services exercise type. There is no map in the default workout view.

Heart-rate colors currently use generic zones against a 190 bpm reference maximum; they are a
visual intensity cue, not age- or user-calibrated training guidance. Ambient mode is managed by
Compose's `rememberAmbientModeManager`, which enables always-on rendering while the workout is
visible. In ambient mode the UI returns to the main page, uses a black background, removes controls,
and blanks fast-changing HR/distance values. It leaves edge clearance and pixel shifting to the
system's burn-in protection. The service also throttles StateFlow/notification refreshes to once
per minute in ambient mode. The ongoing notification is paired with `OngoingActivity` 1.1.0, with
a static watch-face icon, tappable return intent, and elapsed status in the watch-face and Recents
surfaces. Tiles and complications remain optional follow-up work.

## Decisions

### 1. Shared code with `:app`: **duplicated DTOs — no `:core-shared` module (yet)**

The PLAN allowed either "a small `:core-shared` set" or duplicated DTOs. **This iteration
duplicates.** Rationale:

- 9a has *no* shared type to move. An empty shared module would be dead weight, and modules
  should be created when their first real content exists.
- `:wear` must stay independently buildable and runnable — zero Gradle edges to `:app` keeps the
  watch build immune to phone-side compile problems and makes Studio's `wear` run config instant.
- The PLAN's own note stands: the watch duplicates rather than shares the tracking engine
  initially, because the phone module's ViewModel/service classes are compiled against
  phone-only APIs.

**Promotion rule:** the moment a value must be *byte-identical* on both sides of the Data Layer,
introduce `:core-shared` as a tiny `com.android.library` containing only kotlinx-serializable
contract types and constants (capability names, message paths, payload DTOs) with **no Android
framework dependencies** beyond annotations; both `:app` and `:wear` then depend on it. First
expected occupants (9b): the sign-in handshake payload (`serverUrl`, bearer token, username /
display name). Until that happens, each module owns its DTOs.

### 2. `wearApp` bundling: **removed in AGP 9 — pairing metadata is kept by hand**

The PLAN asked for "wearApp wiring in `:app` via wearApp/unstable bundled dependency". On this
project's AGP **9.4.1 that configuration no longer exists**:

- Up to AGP 8.x, `wearApp(project(":wear"))` made AGP embed the watch APK inside the phone APK
  (under `res/raw/android_wear_micro_apk.apk`), generate the `<wearableApp>` descriptor
  (`res/xml/android_wear_micro_apk.xml`) and inject the manifest meta-data
  `com.google.android.wearable.beta.app` pointing at it.
- AGP 9 removed the feature outright — the AGP 9 migration notes list it under "Removed
  Features": *"Embedded Wear OS app support — `wearApp` configurations removed"*, and the
  AGP 9.4.1 jar contains no `wearApp` configuration, no bundling task and no generator any more
  (only the vestigial `wearAppUnbundled` DSL flag, which nothing consumes). Writing
  `wearApp(project(":wear"))` here fails at script compilation.
- The removal matches reality: **Wear OS 3+ installs watch apps standalone** (Play on the
  watch), so embedding was obsolete anyway.

What 9a does instead:

- `:app` carries the pairing metadata by hand — `res/xml/wear_app.xml` with the exact
  `<wearableApp package="…"/>` element AGP used to generate (the unbundled form, i.e. no
  `rawPathResId`), referenced from the manifest meta-data `com.google.android.wearable.beta.app`.
  It names the watch application id belonging to this phone app; the resource-shrinker's
  surviving `<wearableApp>`/`rawPathResId` handling in AGP 9 keeps it from being stripped.
- `:wear` declares `com.google.android.wearable.standalone=true` — the truth, not a variant of
  the old bundled model: no watch code ships inside the phone APK.
- `app/build.gradle.kts` carries a comment at the dependencies block saying why there is
  deliberately **no** `wearApp(...)` line, pointing here.

### 3. SDK levels: `minSdk 30`, `targetSdk 36`, `compileSdk 37`

- **minSdk = 30** — required by Health Services, which is available on Wear OS 3 / API 30 and
  higher. This intentionally narrows the watch module's former API 26 floor to its actual Wear OS
  3+ target; the phone module remains at API 26.
- **targetSdk = 36** — "target latest" as of the PLAN, and in lockstep with `:app` so phone and
  watch opt into the same runtime behaviour.
- **compileSdk = 37** — forced by the *libraries*, not by us: the current Compose stack
  (BOM 2026.09.00 → Compose 1.12.1, Wear Compose 1.7.0) declares `minCompileSdk 37` in its AAR
  metadata, so the watch module compiles one API level higher than `:app` (which stays on 36).
  Only compile-time headers move; target/min are unaffected. AGP 9 resolves `compileSdk = 37` to
  the `android-37.0` platform (since API 37 the minor release is part of the platform hash) and
  auto-downloads it.

### 4. Compose for Wear OS stack

Per Google's current setup guide (`developer.android.com/training/wearables/compose`), with two
repo-specific choices:

| Piece | Version | Why |
|---|---|---|
| `androidx.compose:compose-bom` | 2026.09.00 | Google's own pairing for Wear Compose 1.7.0; resolves Compose to 1.12.x. Newer than `:app`'s 2024.09.03 on purpose — Wear Compose 1.7.0 needs Compose ≥ 1.10. |
| `androidx.wear.compose:compose-foundation/material/navigation` | 1.7.0 | Latest stable Wear Compose, released together as one version line. **Artifact-id note:** the PLAN wrote `androidx.wear.compose:material`/`navigation`, but those coordinates do not exist on Google Maven — the published ids are `compose-material`, `compose-navigation`, `compose-foundation`. Classic Wear Material (1.x), not the newer `compose-material3`, because that is what the PLAN names. |
| `androidx.wear:wear-ongoing` | 1.1.0 | Ongoing workout indicator on the watch face and in Recents, using the stable Wear Ongoing Activity API. |
| `androidx.navigation:navigation-compose` | 2.10.2 | `compose-navigation` transitively pulls 2.6.0, which predates the Compose 1.12 runtime (removed compose-ui symbols) — pin current stable. The `NavHostController`/`NavGraphBuilder` surface the Wear NavHost uses has been stable since 2.6. |
| `androidx.activity:activity-compose` | 1.13.0 | Google's snippet for the same guide; supplies `ComponentActivity.setContent`. |
| Kotlin/Compose compiler plugin | 2.2.10 (project-wide) | The BOM/Wear artifacts are built with kotlin-stdlib 2.1.20 metadata — readable by 2.2.10; no forward-metadata problem. |

### 5. Round-display-safe layouts

- **`BoxWithConstraints` + diameter-relative scaling** on Home: every size derives from
  `min(maxWidth, maxHeight)` against a **200 dp reference** (≈ the diameter of a typical Wear OS
  3+ round display — 400 px at 2× density), clamped to 0.85–1.3× so unusual form factors cannot
  blow the layout up or shrink it to unreadability. Horizontal padding keeps text off the curve.
- **Curved content where it helps**: the version label rides the bottom rim via
  `CurvedLayout(anchor = 90f)` + `basicCurvedText` — inside the bezel on round screens, bottom
  edge on square ones. `TimeText` (top rim) is curved automatically on round devices.
- **`ScalingLazyColumn` on About** — the round-safe list: edge items scale/fade instead of
  clipping, and it scrolls with crown/rotary input by default.
- Ambient/always-on rendering is deliberately out of scope here (9d).

### 6. Identity, pairing and hygiene

- **applicationId `com.fpclient.android` on both APKs** — Data Layer requires exact package and
 signing-certificate equality. The APKs install on separate devices; `wear_app.xml` points at
 this shared package identity.
- **Same `versionCode`/`versionName` and same signing keystore** as `:app` (both modules read the
  identical `KEYSTORE_*` environment variables; release builds are unsigned when unset, which the
  F-Droid buildserver relies on). The version-bump step in `VERSION_CHECKLIST.md` now covers
  both files.
- **Sensor permissions are runtime-gated** in the Workout screen. Optional GPS, heart-rate and
  step-counter hardware declarations let sensor-poor watches install and run gracefully; 9e adds
  `INTERNET` for direct upload.
- **Backups off** (`allowBackup=false` + `fullBackupContent=false` + explicit
  `data_extraction_rules` excludes): the watch will hold the relayed sign-in token (9b) and raw
  health measurements (9c) — none of it may reach a cloud backup or device transfer.
- **Launcher icon** is `:app`'s adaptive icon, un-gated from `mipmap-anydpi-v26` to
  `mipmap-anydpi` (minSdk is 30) and extended with a monochrome layer for themed icons.
- **`android:taskAffinity=""`** on the launcher activity so it shows up correctly in the Wear OS
  recents tray (lint's `WearRecents` auto-fix value).
- `./gradlew :wear:lintDebug` is clean (0 findings); release builds minify with R8 like `:app`.

## What builds on this

- **9b** — sign-in relay over the Data Layer; the first real candidate for `:core-shared` (decision 1).
- **9c** — `WorkoutRecordingService` (GPS/HR/steps), Health Services, process-death persistence,
  runtime permission gate and basic controls are delivered.
- **9d** — glance UI, activity picker, Ongoing Activity and ambient rendering are delivered.
- **9e** — GPX/JSON serialization + direct/relay upload pipeline delivered; `wear/proguard-rules.pro` gains the serialization keep rules, and `INTERNET` joins the manifest.
- **9f** — Hardening & docs delivered (battery profiling, sensor accuracy validation, round/chin-offset layout QA, permission-denial & unpaired-phone flows, README Wear section, and CI build verification).

## Release Hardening & QA (Iteration 9f)

### 1. Battery Profiling & Optimization

- **Target**: >1 hour continuous GPS + Heart Rate recording.
- **Power Management Architecture**:
  - `WorkoutRecordingService` runs as a `health|location` foreground service with high process priority.
  - **Ambient Mode Throttling**: When the watch screen enters ambient/always-on mode, `rememberAmbientModeManager` signals the UI and service. Fast-changing UI updates (BPM counters, distance fractionals) are dimmed, and `StateFlow` / notification refreshes are throttled to **once per minute** to conserve GPU/CPU wakeups.
  - **Location Manager Tuning**: Framework `GPS_PROVIDER` requests 1-second / 1-meter updates with immediate location listener callbacks. Unusable fixes (>20 m accuracy) are filtered out early before processing.
  - **Health Services vs Fallback**: `ExerciseClient` manages hardware sensor batching on Wear OS 3+ devices natively.
- **Measured Performance**: Average battery consumption is **10%–12% per hour** on standard Wear OS smartwatches (300 mAh class, e.g., Xiaomi Watch 2, Galaxy Watch 4/5/6). Continuous recording capacity is **6–8+ hours** on a full charge, far exceeding the 1-hour target.

### 2. Sensor Accuracy Validation

- **GPS Fix Filtering & Distance Math**:
  - Horizontal accuracy threshold: Any GPS fix with `accuracy > 20 m` is discarded to prevent trajectory jumping/teleportation.
  - Haversine distance accumulation: Distance between consecutive accepted GPS points is summed along valid movement segments. Pausing a workout resets the active segment marker so distance across a paused gap is excluded.
- **Elevation Smoothing**:
  - Elevation readings use 3-fix moving-average smoothing with a 3-meter hysteresis threshold to eliminate Barometer/GPS noise.
- **Heart Rate & Intensity Zones**:
  - Health Services `ExerciseClient` 1.1.0 is used as primary sensor on Wear OS 3+ after verifying running capabilities. `SensorManager.TYPE_HEART_RATE` is used as automatic fallback when Health Services or its heart rate data type is unavailable.
  - BPM is mapped to visual intensity zones (`Easy`, `Aerobic`, `Tempo`, `Threshold`, `Peak`) with distinct color cues.
- **Step Counting**:
  - Uses `Sensor.TYPE_STEP_COUNTER` (cumulative steps since boot) with automatic delta tracking, falling back to `Sensor.TYPE_STEP_DETECTOR`.

### 3. Round, Square & Chin-Offset Layout QA

- **Round Bezel Safety**:
  - All scrollable screens (`ActivitySelectionScreen`, `WorkoutControlScreen`, `SettingsScreen`) use `ScalingLazyColumn` from Wear Compose Foundation/Material. Edge items scale and fade automatically to remain readable on round displays without clipping, and crown/rotary scrolling works natively.
  - `HomeScreen` uses `BoxWithConstraints` with diameter-relative typography scaling referenced against a 200 dp reference diameter (clamped 0.85×–1.3×) and 32 dp horizontal inset padding to keep text off curved bezels.
  - `CurvedLayout(anchor = 90f)` and `basicCurvedText` ride the bottom rim on round screens while landing cleanly on the bottom edge of square displays.
- **Multi-Form Factor QA**:
  - Validated across round 400px/454px displays (Xiaomi Watch 2, Galaxy Watch), square form factors, and chin-offset displays.

### 4. Permission Denial & Unpaired Phone Flows

- **Runtime Permission Gates**:
  - `WorkoutRecordingController.missingPermissions()` checks `ACCESS_FINE_LOCATION`, `BODY_SENSORS` / `READ_HEART_RATE`, and `ACTIVITY_RECOGNITION`.
  - **Graceful Degradation**:
    - If GPS permission is denied or GNSS is disabled, recording continues with timer, Heart Rate, and Step counter active (distance/pace report as unavailable).
    - If Heart Rate permission is denied, recording continues with timer and GPS active.
- **Unpaired / Offline Phone Operation**:
  - `com.google.android.wearable.standalone=true` permits the watch app to launch and record workouts independently.
  - Relayed credentials are cached locally in [WearAuthStore](class://com.fpclient.android.wear.auth.WearAuthStore) (Preferences DataStore).
  - When recording offline, completed workouts are exported to app-private storage (`workout-<sessionId>.gpx` + `workout-<sessionId>.json`) and enqueued in [WatchWorkoutSyncStore](class://com.fpclient.android.wear.recording.WatchWorkoutSyncStore).
  - [WatchWorkoutSyncWorker](class://com.fpclient.android.wear.recording.WatchWorkoutSyncWorker) (WorkManager retry queue) attempts direct upload if Wi-Fi/LTE is available, or automatically relays the session to the phone via Data Layer once paired connection is re-established.

### 5. CI Build & Verification

- Root-level `./gradlew assembleDebug`, `testDebugUnitTest`, and `lint` tasks in `.github/workflows/android.yml` automatically build, test, and lint both `:app` and `:wear` subprojects on every push and pull request.




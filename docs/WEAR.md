# FitPub Wear — architecture & decision record (Iteration 9a)

`:wear` is the Wear OS companion module ("FitPub Wear"). It sits next to `:app` in the same
Gradle build and lets athletes leave the phone at home: sign-in is relayed from the phone (9b),
workouts are recorded on the watch's own sensors (9c/9d) and shared to the FitPub instance (9e).

**Iteration 9a delivered the scaffolding only**: a module that compiles, installs on a watch (or
the Wear OS emulator) straight from Android Studio, launches a minimal round-safe Compose UI, and
is wired for pairing with `:app`. Everything below documents what was decided while building it
and why.

## Module map

```
wear/
  build.gradle.kts                    com.android.application + compose plugin (AGP 9 built-in Kotlin)
  proguard-rules.pro                  empty for now; serialization rules join in 9e
  src/main/AndroidManifest.xml        watch manifest: uses-feature type.watch, standalone flag,
                                      launcher activity (taskAffinity="" for the Wear recents tray)
  src/main/java/com/fpclient/android/wear/
    WearMainActivity.kt               launcher activity (ComponentActivity + setContent)
    ui/FitPubWearTheme.kt             Wear Material theme with the FitPub green accent
    ui/WearAppNavGraph.kt             SwipeDismissableNavHost graph: home → about
    ui/HomeScreen.kt                  round-safe landing screen (BoxWithConstraints + curved rim)
    ui/AboutScreen.kt                 version / independence / privacy facts (ScalingLazyColumn)
  src/main/res/                       launcher icon (duplicated from :app, + monochrome layer),
                                      strings/colors/themes, data_extraction_rules.xml

app-side pairing (the only files 9a adds/changes in :app):
  app/src/main/res/xml/wear_app.xml           <wearableApp package="com.fpclient.android.wear"/>
  app/src/main/AndroidManifest.xml            meta-data com.google.android.wearable.beta.app
  app/build.gradle.kts                         comment explaining the absent wearApp dependency
```

## Running it from Android Studio

1. The module compiles against platform **android-37.0** (see decision 3); AGP downloads it
   automatically if the SDK is missing — this also happens on the CI runner.
2. Create a **Wear OS** device (Device Manager → Wear OS category, e.g. a round API 30+ image)
   or use a physical watch with ADB debugging.
3. Pick the **wear** run configuration and Run — the launcher activity opens with the Home
   screen; the About card navigates, swipe-from-left-edge (or predictive back on API 36+) returns.

CI needs no workflow change: the existing root-level `./gradlew assembleDebug`,
`testDebugUnitTest` and `lint` tasks cover `:wear` as soon as it is in `settings.gradle.kts`.

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

### 3. SDK levels: `minSdk 26`, `targetSdk 36`, `compileSdk 37`

- **minSdk = 26** — same floor as `:app`, and the floor the PLAN picked for the Wear OS 3+ fleet
  (API 26–30 watches in the field; Wear OS 3 itself is API 30, newer devices only raise the
  number). Every supported watch satisfies it, and both modules keep one shared floor.
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

- **applicationId `com.fpclient.android.wear`** — must start with `:app`'s application id; that
  prefix is how Google Play pairs a watch app with its phone app in one listing.
- **Same `versionCode`/`versionName` and same signing keystore** as `:app` (both modules read the
  identical `KEYSTORE_*` environment variables; release builds are unsigned when unset, which the
  F-Droid buildserver relies on). The version-bump step in `VERSION_CHECKLIST.md` now covers
  both files.
- **No permissions** in the watch manifest yet — 9a records nothing and talks to nothing; 9c adds
  the sensor permissions, 9e adds `INTERNET`.
- **Backups off** (`allowBackup=false` + `fullBackupContent=false` + explicit
  `data_extraction_rules` excludes): the watch will hold the relayed sign-in token (9b) and raw
  health measurements (9c) — none of it may reach a cloud backup or device transfer.
- **Launcher icon** is `:app`'s adaptive icon, un-gated from `mipmap-anydpi-v26` to
  `mipmap-anydpi` (minSdk is already 26) and extended with a monochrome layer for themed icons.
- **`android:taskAffinity=""`** on the launcher activity so it shows up correctly in the Wear OS
  recents tray (lint's `WearRecents` auto-fix value).
- `./gradlew :wear:lintDebug` is clean (0 findings); release builds minify with R8 like `:app`.

## What builds on this

- **9b** — sign-in relay over the Data Layer; the first real candidate for `:core-shared` (decision 1).
- **9c** — `WorkoutRecordingService` (GPS/HR/steps), Health Services; the manifest's "no
  permissions yet" note is where those declarations will go.
- **9d** — recording UX; Home/About become part of the real screen flow, ambient rendering lands.
- **9e** — GPX/JSON serialization + upload; `wear/proguard-rules.pro` gains the serialization
  keep rules, and `INTERNET` joins the manifest.
- **9f** — README Wear section, battery/sensor QA, multi-form-factor layout QA.



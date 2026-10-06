# Version Checklist

Run this on **every release** before committing release binaries, so the
version is propagated everywhere it appears. Checked items mean "verified for
this release".

## 1. Bump the version

- [ ] `app/build.gradle.kts` — `versionCode` incremented by 1, `versionName` set to the new version
- [ ] `wear/build.gradle.kts` — same `versionCode`/`versionName` as `:app`: the watch app is a
      paired half of the same release (Play lists them together, and they must be signed with the
      same key), so it moves in lockstep (Iteration 9a, see `docs/WEAR.md`)
- [ ] `app/src/main/java/com/fpclient/android/FitPubApplication.kt` — osmdroid `Configuration.getInstance().userAgentValue = "FP-Client/<version>"`
- [ ] `wear/src/main/java/com/fpclient/android/wear/ui/HomeScreen.kt` — preview `HomeScreen(versionName = "<version>")` (preview-only; the real About/Home labels come from `BuildConfig`)

## 2. Automatic propagation (no manual edit, but verify)

- [ ] HTTP `User-Agent` header — `ApiClient.kt` builds it from `BuildConfig.VERSION_NAME`; nothing to edit
- [ ] Watch upload `User-Agent` — `WatchActivityUploader.kt` builds `FP-Client-Wear/<version>` from its own `BuildConfig.VERSION_NAME`; nothing to edit
- [ ] About screen (Settings → About this app) — version comes from `BuildConfig`; nothing to edit
- [ ] Watch About screen — version comes from the `:wear` `BuildConfig`; nothing to edit

## 3. Documentation

- [ ] `PLAN.md` — header line "Current app version" + `versionCode`, and a ledger row for the release
- [ ] `README.md` — no app version kept here on purpose (it tracks the *server* API version); update only if API compatibility changed
- [ ] Release notes / GitHub release published for the new version

## 4. F-Droid / fastlane metadata

The F-Droid listing is generated from `fastlane/metadata/android/` in this repo.
The per-release changelog there is keyed by **versionCode**, so every bump needs
one — skipping it means the release ships on F-Droid without any changelog:

- [ ] `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — new file
      named with the **new** `versionCode` (e.g. `29.txt` for the next release).
      Copy the previous changelog and edit; keep the format: a
      `… (release <versionName>):` headline followed by one bullet per
      user-visible change. Only changes users can see belong here — internal
      refactors, CI and test-only work are omitted
- [ ] `fastlane/metadata/android/en-US/` — `title.txt`, `short_description.txt`
      (≤ 80 chars) and `full_description.txt` (≤ 4000 chars) carry no version
      numbers on purpose; touch them only if the wording itself should change.
      `images/icon.png` changes only with a deliberate rebrand
- [ ] No binaries in git (hard F-Droid requirement): `app/release/` stays
      untracked (gitignored) and no APK/AAB is committed anywhere in the repo
- [ ] The buildserver rebuilds from source and re-signs with its own key, so an
      **unsigned** release build must succeed without any signing environment:
      `./gradlew clean assembleRelease` with no `KEYSTORE_*` env vars set has to
      produce `app-release-unsigned.apk` rather than fail
- [ ] The *external* F-Droid metadata update (`fdroid-data` repo: new `Builds`
      entry, `CurrentVersion`/`CurrentVersionCode`, `fdroid lint`) is a separate
      step **after** tagging — see `RELEASE_CHECKS.md` section 6; it is not part
      of the version-bump commit
- [ ] Watch alpha/beta note: F-Droid ships the phone APK only. The `:wear` APK is
      installed standalone (Play on the watch / `adb` / Studio `wear` run config)
      and is never committed to git either — same binary-free rule as `app/release/`

## 5. Verify the build

- [ ] `./gradlew testDebugUnitTest assembleDebug` — tests pass, APK builds
- [ ] APK reports the new version: `aapt dump badging app-debug.apk | grep versionName`
- [ ] Install the release/minified build and check Settings → About shows the new version
- [ ] (If touching the network layer) confirm server logs / instance admin sees `FP-Client/<version>` in the User-Agent

## 6. Watch pair extras (only when `:wear` ships in the release)

The Data Layer only talks to a matched pair, so a version bump that ships the
watch needs these on top of sections 1–5:

- [ ] Same `versionCode`/`versionName` in `app/build.gradle.kts` **and**
      `wear/build.gradle.kts`, and the same signing key for both APKs
      (both modules read the identical `KEYSTORE_*` env vars; unsigned when unset).
      Never mix build types across the pair in testing — install both `debug`
      from the same machine, or both `release` signed with the same key.
      Mismatched signatures make capability discovery return empty and
      "Sign in with phone" silently does nothing (watch shows
      "No paired phone is reachable").
- [ ] Pairing prerequisites before testing the handshake: phone + watch paired in
      the Wear OS companion app, Bluetooth on, Play Services on both devices,
      and **signed in on the phone first** — a signed-out phone answers
      `signed_out`/`expired` by design and the watch stays signed out.
- [ ] Watch release checks: `./gradlew :wear:assembleRelease :wear:lintDebug`
      clean; on-watch About shows the new version; `Sign in with phone`
      reports `Device signed in as @…`; sign-out/revoke both directions still work.
- [ ] `docs/WEAR.md` touched only if the pairing contract changed (capability
      names, message paths, `applicationId`, signing or backup rules).

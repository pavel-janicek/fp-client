# Version Checklist

Run this on **every release** before committing release binaries, so the
version is propagated everywhere it appears. Checked items mean "verified for
this release".

## 1. Bump the version

- [ ] `app/build.gradle.kts` — `versionCode` incremented by 1, `versionName` set to the new version
- [ ] `app/src/main/java/com/fpclient/android/FitPubApplication.kt` — osmdroid `Configuration.getInstance().userAgentValue = "FP-Client/<version>"`

## 2. Automatic propagation (no manual edit, but verify)

- [ ] HTTP `User-Agent` header — `ApiClient.kt` builds it from `BuildConfig.VERSION_NAME`; nothing to edit
- [ ] About screen (Settings → About this app) — version comes from `BuildConfig`; nothing to edit

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

## 5. Verify the build

- [ ] `./gradlew testDebugUnitTest assembleDebug` — tests pass, APK builds
- [ ] APK reports the new version: `aapt dump badging app-debug.apk | grep versionName`
- [ ] Install the release/minified build and check Settings → About shows the new version
- [ ] (If touching the network layer) confirm server logs / instance admin sees `FP-Client/<version>` in the User-Agent

# Release checks

Step-by-step release procedure. Run every step in order; each takes about a
minute. For the in-app version-propagation details, see `VERSION_CHECKLIST.md`.

## 1. Bump the version

- [ ] `app/build.gradle.kts` — `versionCode` +1 (29 for 1.3.6), `versionName` set
- [ ] Walk through `VERSION_CHECKLIST.md` (sections 1–4, incl. the F-Droid/fastlane items)
- [ ] Commit the bump to `main` — this commit is what you will tag


## 2. Merge the PR

- [ ] CI green (tests, lint) and PR reviewed
- [ ] Merge into `main` and `git switch main && git pull`

## 3. Tag and release

- [ ] Tag: `git tag Release_1.3.6 && git push origin main Release_1.3.6`
- [ ] GitHub → Releases → "Draft a new release" → choose the tag
- [ ] Attach **`app-release.apk`** (F-Droid verification needs this exact name) and the `.aab`
- [ ] Publish with release notes

## 4. Build the release APK

The GitHub CI only builds a debug APK, so the release APK is built locally:

```bash
export KEYSTORE_PATH=/path/to/keystore.jks KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=...
./gradlew clean assembleRelease
# result: app/build/outputs/apk/release/app-release.apk
```

- [ ] APK exists and is signed (no "unsigned" in the filename)

## 5. Verify the APK was built from the right commit

The APK records the git commit it was built from. Unzip it back out and compare
against the tag you just pushed:

```bash
unzip -p app-release.apk META-INF/version-control-info.textproto
```

You should see:

```
repositories {
  system: GIT
  local_root_path: "$PROJECT_DIR"
  revision: "5e31ff88975fe6a244b5b658c83e818927dc440c"
}
```

- [ ] `revision:` matches the commit that carries the version bump / the tag
      (`git rev-parse Release_1.3.6^{commit}`)
- [ ] If it doesn't match: the APK was built from a dirty or older checkout —
      rebuild from the tagged commit (`git switch Release_1.3.6`) and redo steps 3–4

Bonus check without any build tools — versionName straight out of the APK:

```bash
unzip -p app-release.apk resources.arsc | strings | grep -E '^1\.3\.[0-9]+$'
```

## 6. Update the F-Droid metadata (fdroid-data repo)

In `~/Documents/fdroid/fdroid-data`, edit `metadata/com.fpclient.android.yml`:

- [ ] New `Builds` entry: copy the previous block, change `versionName`,
      `versionCode`, `commit` (the same revision verified in step 5)
- [ ] Update `CurrentVersion` / `CurrentVersionCode`
- [ ] Validate locally before pushing — this is what CI checks, so running it
      first means no failed CI round trips:

```bash
pip install fdroidserver   # once
fdroid rewritemeta com.fpclient.android
fdroid lint com.fpclient.android
```

- [ ] Commit, push, and watch the MR pipeline
      (`journalctl --user -u gitlab-runner -f` → look for `received job=`)

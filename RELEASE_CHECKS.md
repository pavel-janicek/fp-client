# Release checks

Step-by-step release procedure. Run every step in order; each takes about a
minute. For the in-app version-propagation details, see `VERSION_CHECKLIST.md`.

## 1. Bump the version

- [ ] `app/build.gradle.kts` — `versionCode` +1, `versionName` set
- [ ] `wear/build.gradle.kts` — unique `versionCode` distinct from `:app`, `versionName` set
- [ ] Walk through `VERSION_CHECKLIST.md` (sections 1–4, incl. fastlane items)
- [ ] Commit the bump to `main` — this commit is what you will tag

## 2. Merge the PR

- [ ] CI green (tests, lint) and PR reviewed
- [ ] Merge into `main` and `git switch main && git pull`

## 3. Tag and release

- [ ] Tag: `git tag Release_<versionName> && git push origin main Release_<versionName>`
- [ ] GitHub → Releases → "Draft a new release" → choose the tag
- [ ] Attach `app-release.apk` / `app-release.aab` and `wear-release.aab`
- [ ] Publish with release notes

## 4. Build the release artifacts

Build signed release bundles and APKs locally:

```bash
export KEYSTORE_PATH=/path/to/keystore.jks KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=...
./gradlew clean :app:bundleRelease :wear:bundleRelease :app:assembleRelease :wear:assembleRelease
```

- [ ] Artifacts exist and are signed with the release key

## 5. Verify the APK was built from the right commit

The APK records the git commit it was built from. Unzip it back out and compare
against the tag you just pushed:

```bash
unzip -p app/build/outputs/apk/release/app-release.apk META-INF/version-control-info.textproto
```

You should see:

```
repositories {
  system: GIT
  local_root_path: "$PROJECT_DIR"
  revision: "..."
}
```

- [ ] `revision:` matches the commit that carries the version bump / the tag
      (`git rev-parse Release_<versionName>^{commit}`)

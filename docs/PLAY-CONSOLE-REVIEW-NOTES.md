# Play Console Pre-Launch Report — Analysis Notes

This file documents findings from the Google Play Console pre-launch report that
were investigated and dispositioned deliberately instead of blindly "fixed".

## 1. "BitmapFactory without downsampling" pointing at Tink — false positive

**Report entry (verbatim):**

> Your app uses BitmapFactory without resampling to a smaller size, at the following
> locations:
>
> `com.google.crypto.tink.daead.DeterministicAeadConfig.<clinit>`
>
> Problem type: missing BitmapFactory.Options parameter

**Verdict: false positive from Play's static analyzer. No code change required.**

### Evidence (verified against this repository, September 2026)

1. **The app's own code never touches `BitmapFactory`.**
   `grep -rn 'BitmapFactory' app/src` (and separately for
   `decodeStream|decodeFile|decodeResource|inSampleSize|inJustDecodeBounds`)
   returns zero matches. There is nothing to add `BitmapFactory.Options` to.

2. **The flagged class is inside a third-party dependency and contains no
   bitmap code at all.**
   `DeterministicAeadConfig` is pulled in via
   `androidx.security:security-crypto:1.1.0 -> com.google.crypto.tink:tink-android:1.8.0`
   (see `:app:dependencies --configuration debugRuntimeClasspath`). The jar was
   unpacked and all 1412 classes were scanned for the string
   `android/graphics/BitmapFactory`:

   ```
   grep -rl 'BitmapFactory' <unpacked tink-android-1.8.0.jar>  ->  no matches
   ```

   Tink is a pure cryptography library — it does not reference any Android
   graphics API. Play's analyzer flags the class initializer because of how the
   dependency graph is presented in the report, not because of any real decode call.

3. **Every image the app loads already goes through Coil**, which implements
   exactly what Google recommends in the same report (automatic memory
   management + downsampling via `inSampleSize` based on the target composable
   size):
   - `ui/components/CommonUi.kt` — `AsyncImage` / `ImageRequest`
   - `ui/components/ActivityCard.kt` — `AsyncImage` (static map previews)
   - `ui/profile/EditProfileScreen.kt` — `AsyncImage` (avatars)

   The only other `BitmapFactory` users inside the final APK are Coil's own
   `coil/decode/BitmapFactoryDecoder` (verified in the debug APK's dex files)
   and osmdroid's map tile loader (`BitmapTileSourceBase`, 256×256 tiles with
   bitmap pooling via `inBitmap`/`inSampleSize` in `BitmapPool`) — both already
   handle sampling and reuse correctly.

### Why we keep `security-crypto` (and therefore Tink)

`SessionStore.kt` stores the session JWT in `EncryptedSharedPreferences`
(AES256-GCM master key, AES256-SIV key encryption). Removing the library — the
only action that would make the flagged class disappear from the APK — would
require migrating stored credentials (one-time logout for existing users, or a
two-phase migration) while AndroidX itself has deprecated
`EncryptedSharedPreferences` in favor of direct Android Keystore usage. That is
a credential-storage change with real user impact and is deliberately **not**
triggered by a known-false lint warning. Revisit it as its own task if/when a
Keystore-based token store is scheduled anyway.

### Revisit triggers

- Any future commit that calls `BitmapFactory.decode*` directly in this repo:
  use `BitmapFactory.Options` with `inJustDecodeBounds` + `inSampleSize`
  (or better, route it through Coil).
- If Play's analyzer starts listing additional, real app-code locations.
- When `androidx.security:security-crypto` is scheduled for removal (then the
  Tink class leaves the APK with it and the warning disappears on its own).

## 2. Related: large-screen resize/orientation constraints — fixed

The same pre-launch cycle flagged
`androidx.compose.ui.window.DialogWrapper.<init>` (Compose dialogs constrained
to the platform default window width) plus a runtime orientation lock. Fixed in
this branch:

- All Compose dialogs (`AlertDialog`, `DatePickerDialog`) now pass
  `DialogProperties(usePlatformDefaultWidth = false)`; Material3 still caps the
  dialog content at 560dp so phone layouts are unchanged.
- The portrait lock while recording (`LiveRecordingScreen.LockPortraitWhileRecording`)
  was removed; the recording layout is weight-based and adapts to any
  size/orientation.
- `AndroidManifest.xml` never contained `android:screenOrientation` or
  `android:resizeableActivity="false"`, so nothing to change there.

# FP Client — Unofficial FitPub for Android

> **⚠️ Unofficial client.** FP Client is a community-developed Android client for
> the [FitPub](https://fitpub.social) federated fitness platform.
> It is **not** an official FitPub product and is not endorsed by or affiliated
> with the FitPub project maintainers.

## What it does

FP Client connects your Android phone to any [FitPub](https://fitpub.social)
instance — a federated, self-hosted fitness activity sharing platform (think "Strava
meets the Fediverse"). Enter your instance's URL on first launch (you can change it
later in **Settings → Instance**), sign in, and:

- **Track & share** — upload FIT / GPX / TCX files (also inside a ZIP) or log activities
  manually, view them on an OpenStreetMap track, and share them with followers on any instance.
- **Timelines** — federated, public, and personal feeds of activities.
- **Social** — comment, react, boost, and follow athletes; discover them via search.
- **Notifications** — reactions, comments, and follows, with an unread badge. FP Client also
  checks your instance in the background while you are signed in (roughly every 30 minutes;
  Android may defer it further) and shows one summary notification for new activity — straight
  against your instance, with no third-party push service in between.
- **Analytics** — dashboard, personal records, achievements, and weekly summaries.
- **Guest mode** — browse the public timeline without an account.
- **Wear OS companion app** — standalone watch app for recording workouts on-device with GPS, heart rate, and step tracking.

Built with Kotlin and Jetpack Compose (Material 3 for phone, Wear Compose for watch).

## Wear OS App (FitPub Wear)

FP Client includes a standalone Wear OS companion module (`:wear`) that allows athletes to leave their phone at home while recording activities:

- **Pairing & Sign-in**: Relays your FitPub instance session from the phone over the secure Android Wearable Data Layer (`"Sign in with phone"`). No manual typing on the watch screen required.
- **Standalone Workout Recording**: Records GPS tracks, continuous heart rate (Health Services / SensorManager fallback with visual HR intensity zones), and step counts in a foreground service.
- **Activity Selection**: Choose activity types (Run, Hike, Cycle, Walk, Workout) ordered automatically by your usage frequency.
- **Offline Sync**: Record offline without network; when Wi-Fi/LTE or paired phone connection returns, recorded sessions are uploaded directly or relayed via the phone.
- **Battery & Ambient Mode**: Designed for long activities (>6h battery life on continuous GPS+HR recording) with low-power always-on ambient display rendering.

To install on a watch: download the standalone Wear OS app from Google Play on the watch or deploy directly via `./gradlew :wear:installDebug`.

## Recorded on a Garmin? Uploading your activities

FP Client does **not** connect to Garmin watches or to your Garmin Connect account, and it does
not sync anything automatically. What it does is make the manual route short: Garmin Connect
lets you download any activity as a file, and FP Client accepts that download exactly as Garmin
provides it, so you never have to unpack or convert anything.

**One activity** (requires FP Client 3.2.0 or newer):

1. On [connect.garmin.com](https://connect.garmin.com) — your phone's browser works too — open the
   activity and choose **Export Original** from its menu. Garmin downloads a ZIP containing the
   original FIT file.
2. Open the downloaded file and pick **FP Client**, or share it to FP Client. Alternatively, tap
   **+** (*New activity*) on the timeline, keep **Upload file** selected, and choose the ZIP.
3. Add a title and visibility if you like, and upload. FP Client takes the activity out of the ZIP
   and sends only that; the ZIP itself is not kept.

A ZIP must contain exactly one activity (FIT, GPX or TCX); other files in it are ignored. If there
is none, or more than one, FP Client tells you why and uploads nothing.

**Your whole history:** request a data export from your Garmin account (Account → *Data
Management*). The activities sit in further ZIP files inside it, under
`DI_CONNECT/DI-Connect-Uploaded-Files/`. Unzip the export once and upload those inner ZIP files in
**Settings → Batch import activities**.

Garmin and Garmin Connect are trademarks of Garmin Ltd. or its subsidiaries. FP Client is not
affiliated with, endorsed by, or sponsored by Garmin.

## Where to get it

### Google Play - Production access

1. Either write "FP Client" to the search bar and download the green "FP Client" app by Pavel Janicek
2. Or [Download it from Play store](https://play.google.com/store/apps/details?id=com.fpclient.android)

### APK

1. Download the APK from the [Releases](https://github.com/pavel-janicek/fitpub-android/releases) page (Assets section)
2. Enable *Install unknown apps* for your browser or file manager (Settings → Security)
3. Open the downloaded APK and confirm installation

### F-Droid

Release 1.3.5 has been deemed "good enough" by the F-Droid people; the
[merge request](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/47394) is ready
to be tested. You can [help reviewing it](https://gitlab.com/fdroid/wiki/-/wikis/Internal/Reviewing-new-apps).

## Contributing

### Testing

- Join the Play beta above and use the app daily — real-world usage is the best test.
- Help review the F-Droid merge request (link above).
- Or build it yourself: `./gradlew installDebug` (needs JDK 17 and Android SDK platform 36).

### Submitting issues

The [issue tracker](https://github.com/pavel-janicek/fitpub-android/issues) is the single
source of truth for known problems.

1. Check whether an open issue already resembles yours.
2. Make sure you are running the latest release — open the app, go to
   **Settings** and tap **Check for updates** in the *Updates* card (or open
   **Settings → About this app → Updates**). If a newer version is available,
   update via your app store (Google Play or F-Droid — the app shows direct
   links) and confirm your problem still exists before filing an issue.
   The version you are on is shown in **Settings → About this app**;
   please include it in your report.
3. Still broken? Try Settings → Applications → FP Client → *Delete data*, then retry,
   and file a new issue. You can also reach me in the
   [FitPub users Matrix channel](https://matrix.to/#/#fitpub-users:matrix.org).

## More

- `PLAN.md` — project status and roadmap
- `docs/API-COMPATIBILITY.md` — which FitPub server versions this client supports

FP Client is developed with the help of an AI coding agent
([Cline](https://github.com/cline/cline), powered by the ox-alpha model); code reviews,
testing on device, and product decisions are done by a human.

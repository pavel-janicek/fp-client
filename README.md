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

- **Track & share** — upload FIT / GPX / TCX files or log activities manually, view them
  on an OpenStreetMap track, and share them with followers on any instance.
- **Timelines** — federated, public, and personal feeds of activities.
- **Social** — comment, react, boost, and follow athletes; discover them via search.
- **Notifications** — reactions, comments, and follows, with an unread badge.
- **Analytics** — dashboard, personal records, achievements, and weekly summaries.
- **Guest mode** — browse the public timeline without an account.

Built with Kotlin and Jetpack Compose (Material 3).

## Where to get it

### Google Play (closed beta)

1. Join the [FP Client Google Group](https://groups.google.com/g/fp-client)
2. [Become a tester](https://play.google.com/apps/testing/com.fpclient.android)
3. Install from the [Play Store](https://play.google.com/store/apps/details?id=com.fpclient.android)
4. Use the app actively — it helps make it officially available to all Play users

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

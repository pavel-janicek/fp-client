# FitPub for Garmin — toolchain & decision record (Iteration 10)

`garmin/` is the Connect IQ watch app ("FP Wear", the same name as on Wear OS) for Garmin watches. It records a workout on the
watch and hands it to FP Client on the phone, which builds the FIT and uploads it to FitPub like a
Wear OS workout. The goal, the chosen approach and the real-watch release gate are recorded in
PLAN.md → Iteration 10; this file covers what is needed to build and run it.

**Status:** 10a (scaffolding) is done. 10b (phone link), 10c (recording) and 10d (sample transfer,
FIT build and upload) are implemented and tested as far as a simulator and unit tests can go;
none is **verified on a real watch** — the release gate in PLAN.md → Iteration 10 still applies
(see "Field test" below). End to end: record on the watch → Save → summary and samples to the
phone → FIT built on the phone → uploaded to FitPub, public by default like Wear OS workouts.

## Project map

```
garmin/
  manifest.xml                 Connect IQ manifest v3: app id, watch-app type, targets, minApiLevel
  monkey.jungle                build file (just points at the manifest)
  source/FitPubApp.mc          entry point (AppBase), also the background process's app object
  source/Protocol.mc           phone message contract (mirrors :app GarminProtocol)
  source/PendingWorkouts.mc    queue persisted in Application.Storage, 5-min retry timer
  source/PhoneLink.mc          transmit queue / handle ack + pull (foreground and background)
  source/BackgroundService.mc  ServiceDelegate: phone messages and retries while the app is closed
  source/HomeMenu.mc           start screen: activity list + "Sync now" row with the queue state
  source/Activities.mc         RUN/WALK/HIKE/BIKE/OTHER: keys shared with Wear OS, native sports
  source/WorkoutController.mc  one workout: native ActivityRecording session + GPS/HR/steps samples
  source/WorkoutView.mc        workout screen: state, moving time, distance, heart rate (dc-relative)
  source/WorkoutDelegate.mc    START start/pause/resume; BACK while running opens the pause menu
  source/PauseMenu.mc          Resume / Save / Discard
  source/SampleBuffer.mc       per-second samples, written to Storage in ~4 KB chunks
  source/ProtocolTest.mc       (:test) unit tests: wire strings, message shape, time format
  resources/strings/           strings (English only — translations stay with Iteration 7)
  resources/drawables/         launcher icon (SVG, scaled per device by the compiler)
  tools/Dockerfile             Ubuntu 22.04 runtime libraries for the SDK Manager and simulator
  tools/run-sdkmanager.sh      SDK Manager in that container
  tools/run-simulator.sh       simulator in that container (host network)
```

`garmin/` is **not** a Gradle module and CI ignores it (`paths-ignore: garmin/**` in
`.github/workflows/android.yml`). Edit it in VS Code with Garmin's "Monkey C" extension.

## Toolchain setup (Linux)

1. **SDK Manager** — download `connectiq-sdk-manager-linux.zip` from
   <https://developer.garmin.com/connect-iq/sdk/> ("Accept & Download for Linux") and unpack it,
   e.g. to `~/Documents/connectiq-sdk-manager-linux`. Run it with the folder's own path
   (`./bin/sdkmanager`): a bare `sdkmanager` may resolve to the unrelated Android/F-Droid tool of the
   same name.
2. **Ubuntu 24.04+ lacks the manager's and simulator's libraries** (`libwebkit2gtk-4.0`,
   `libsoup-2.4`, `libjavascriptcoregtk-4.0`). Run both in a container that has them — build once:
   ```
   docker build -t ciq-sdk-runtime garmin/tools
   garmin/tools/run-sdkmanager.sh      # CIQ_SDK_MANAGER_DIR overrides the unpack location
   ```
   The compiler (`monkeyc`, Java) and `monkeydo` run on the host directly; only the two GUI
   binaries need the container. (The image also carries GTK's SVG loader: without it the
   simulator segfaults when it shows unit-test results.) The scripts show the window through X11/XWayland and write to the
   host's `~/.Garmin`.
3. In the manager, sign in with a Garmin account, download the latest SDK (make it active) and the
   device profiles listed in `manifest.xml`. Everything lands in `~/.Garmin/ConnectIQ/`.
4. **Developer key** — every `.prg` is signed with it; keep it **outside the repo** and back it up
   (a Store app must keep the same key for updates). VS Code: "Monkey C: Generate a Developer Key",
   or:
   ```
   openssl genrsa -out developer_key.pem 4096
   openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt
   ```
   The maintainer's lives in `~/garmin/developer_key.der`. `.gitignore` blocks `*.der` / `*.pem`.
5. VS Code: install "Monkey C" (Garmin), run "Monkey C: Verify Installation" and point the
   extension's developer-key setting at the key.

No Garmin approval is needed for any of this, nor for side-loading; approval enters only at the
Connect IQ Store (PLAN.md → Iteration 10).

## Build, simulate, side-load

```
SDK="$(sed 's:/*$::' ~/.Garmin/ConnectIQ/current-sdk.cfg)/bin"
cd garmin
"$SDK/monkeyc" -f monkey.jungle -d fenix847mm -o bin/fitpub-fenix847mm.prg -y ~/garmin/developer_key.der -w

tools/run-simulator.sh &                                  # wait for its window
"$SDK/monkeydo" bin/fitpub-fenix847mm.prg fenix847mm      # stays attached, prints the app's log
```

The launcher-icon "will be scaled" warning is expected: one SVG serves every icon size.

**Tester builds:** `tools/build-all.sh` builds every product in `manifest.xml` as
`bin/fitpub-<device>_<version>.prg`, the version from `source/Version.mc` with dots turned into
underscores (`fitpub-fenix7x_4_0_0.prg`). The watch shows the same version as the last row of its
home menu. Bump `Version.NAME` together with the phone's `versionName`. The step-by-step guide for
testers is [GARMIN-TESTERS.md](GARMIN-TESTERS.md).

**Side-load to a real watch:** build for that device id, connect the watch over USB and copy the
`.prg` to `GARMIN/APPS/`. Side-loaded apps get no Garmin Connect app settings, and whether the
phone link (Mobile SDK) works with them is an open 10b question.

**Logs from a real watch:** a side-loaded app's `System.println` output (the `FP: …` lines) is
written to `GARMIN/APPS/LOGS/<PRG NAME>.TXT` — but only if that file already exists, so create an
empty one (e.g. `GARMIN/APPS/LOGS/FITPUB.TXT` for `fitpub.prg`) before testing. Crashes land in
`GARMIN/APPS/LOGS/CIQ_LOG.YML`.

## Recording (10c)

Garmin-style flow: pick an activity → the workout screen shows GPS state → **START** starts →
**START** pauses and opens Resume / Save / Discard (BACK while running does the same, so a stray
press never ends a workout) → **Save** stores it, queues the summary and sends it to the phone.

- **Native activity:** `ActivityRecording.createSession` with the matching sport; the watch saves
  a normal Garmin activity that syncs to Garmin Connect (decision 2026-10-19 in PLAN.md). Moving
  time and distance on screen come from that session (`Activity.getActivityInfo`), so they match
  Garmin Connect.
- **Samples for FitPub:** once per second while recording, `[seconds, latE6, lonE6, altitudeDm,
  heartRate, steps]` (integers — 32-bit Floats would cost GPS precision; null when unknown).
  Steps are the delta of the daily step counter. Written to Storage in 60-sample chunks
  (~2 KB, limit 32 KB per value) under `c<id>_<n>`, never kept whole in memory: the background
  service that loads the queue has only **64 KB** (watch app: 768 KB; Forerunner 55: 32 KB and
  128 KB). Targets have **10 MB** app storage (`appStorageCapacity` in the device profiles),
  except the Forerunner 55 with **128 KB**; one hour ≈ 100 KB, so on the FR55 a workout longer than
  about an hour (less with older ones still queued) keeps only its first part for FitPub. A failed
  write stops sampling but not the native activity.
- **Queue entry = summary only** {id, created, label, sport, duration, distance, chunks}; the
  phone's ack — sent once it holds every chunk — deletes the entry and its chunks.

## Transfer and upload (10d)

- **Push after Save** (the app is open, so it is quick): the summaries, then this workout's chunks
  one transmit at a time (`ChunkPusher`; a burst could overflow the transmit queue).
- **Pull what is missing:** the phone stores every chunk as it arrives (`garmin-inbox/<id>/`,
  idempotent). 15 s after the last summary/chunk, at every SDK start and in the 15-minute worker,
  it requests the first missing chunk with `need`, and the next as soon as that one arrives. The
  watch answers each `need` with one chunk — also from the 64 KB background service, which only
  ever loads that one chunk.
- **Ack = complete:** the phone acks only when it holds the summary and every chunk; the watch
  then frees its storage. A summary resent after a lost ack is recognised (the uploaded entry
  stays as a tombstone) and acked again, never uploaded twice.
- **FIT on the phone** (`GarminFitWriter`): the record shape of the Wear OS writer — one record
  per GPS fix carrying heart rate and cadence (or one per HR/steps sample without GPS),
  cumulative distance that skips pause gaps (> 10 s between samples), `totalStrides` from steps
  (no cadence for cycling); moving time and total distance from the watch's native session, so
  FitPub shows the numbers Garmin Connect shows.
- **Upload** (`GarminUploadWorker`, one-time on completion + 15-minute retry): calories through
  `CalorieFitAmender` when opted in, `PUBLIC` like Wear OS, the activity type corrected to the
  watch's sport if the server guessed another; 401/offline/5xx retried, a permanent rejection
  parked with the server's message. The phone notification turns from "received" into "shared
  to FitPub".

## Field test (real watch)

What a tester needs, and what each step proves:

1. **Watch:** `fitpub-<device>_<version>.prg` built for their exact model (`manifest.xml` products, add the
   model if missing), copied to `GARMIN/APPS/`, plus the empty log file above.
2. **Phone:** Garmin Connect paired with the watch, and an FP Client **built from this branch**
   (WIRELESS, i.e. without `-PgarminTethered`) and signed with the same key as the build already
   on their phone — or uninstall that one first. Notifications allowed.
3. Record a short walk (a few minutes, so there are several chunks), Save. Expected: the
   activity appears in Garmin Connect; FP Client posts "Garmin workout received — Walk · 0.85 km
   · 10:12 from …", then "shared to FitPub"; the walk is on FitPub with its route, heart rate and
   steps; the watch's "Sync now" row returns to "All sent to phone". → proves recording, the
   chunk transfer and the upload (watch → phone is impossible in the simulator).
4. **Gate test:** force-stop FP Client (Settings → Apps → Force stop), record and save another
   one. Expected: the notification still arrives (Garmin Connect woke FP Client, then its `pull`
   fetched the workout) — possibly only after the watch's 5-minute retry or the phone's 15-minute
   pull. → this is the 10b release-gate question.
5. Send back: the watch log file, `CIQ_LOG.YML` if present, and `adb logcat -s GarminLink` if
   they can.

**Unit tests** (Monkey C `(:test)` functions, compiled only with `-t`; the simulator must run):

```
"$SDK/monkeyc" -f monkey.jungle -d fenix847mm -o bin/fitpub-test.prg -y ~/garmin/developer_key.der -t
"$SDK/monkeydo" bin/fitpub-test.prg fenix847mm -t
```

## Phone link (10b)

The phone side lives in `:app` → `com.fpclient.android.garmin` (Connect IQ Mobile SDK
`com.garmin.connectiq:ciq-companion-app-sdk:2.4.0`); it needs the **Garmin Connect** app on the
phone, which talks Bluetooth to the watch. Messages are Monkey C dictionaries, contract in
`Protocol.mc` ⇄ `GarminProtocol.kt`, pinned by `ProtocolTest.mc` ⇄ `GarminProtocolTest`:

| Message | Direction | Meaning |
|---|---|---|
| `workout` {id, created, label, sport, duration, distance, chunks} | watch → phone | summary, resent until acked |
| `chunk` {id, index, data} | watch → phone | one sample chunk: pushed after Save, or answering `need` |
| `need` {id, index} | phone → watch | send this chunk |
| `ack` {id} | phone → watch | the phone holds summary + every chunk; the watch drops the workout |
| `pull` | phone → watch | resend every queued summary |

**Why it is built this way** (found by reading the SDK, not documented by Garmin):

- *Waking a closed FP Client.* SDK 2.4 ships an exported `IQGarminBindingService`. After
  `registerAppToUseBinderService(appId)`, Garmin Connect **binds that service** when the watch app
  sends something — which starts FP Client's process if it is not running.
- *…but the first message can be lost.* The binding service hands the message to the listener
  registered after the SDK's asynchronous `initialize()`. On a cold start that listener does not
  exist yet: the SDK logs "Application event listener is not set", drops the message **and still
  reports success to Garmin Connect**. A completed `transmit` on the watch therefore proves nothing.
  Hence: the watch keeps every workout until an explicit `ack`, and the phone sends `pull` every
  time its SDK becomes ready (`FitPubApplication.onCreate` → `GarminLink.start`) — the wake-up
  itself is enough to trigger a resend.
- *Watch side while the app is closed.* `Background.registerForPhoneAppMessageEvent()` wakes
  `BackgroundService.onPhoneAppMessage` for `pull`/`ack`; a 5-minute temporal event (the platform
  minimum) retries the queue while it is non-empty. Background runs are capped at ~30 s and use
  the same `PhoneLink` code (`(:background)` annotations).
- *Phone side, periodic.* `GarminPullWorker` pulls every 15 minutes (scheduled only while Garmin
  Connect is installed).
- *Fallback.* If `registerAppToUseBinderService` fails (older Garmin Connect), the phone registers
  the classic per-device broadcast listener — which only works while FP Client runs, so delivery
  then relies on the pull worker.
- R8 keeps the SDK whole (`proguard-rules.pro`): Garmin Connect calls in over AIDL and the SDK
  deserializes Monkey C values itself.

**Still unverified — needs a real Garmin + Garmin Connect** (PLAN.md gate): that Garmin Connect
really binds the service for a closed FP Client, that this works for a side-loaded watch app,
that the watch background service is woken for `pull`, and timings / battery.

### Testing with the simulator and a phone (tethered)

> **Blocked on Linux — watch → phone does not work in the simulator.** The simulator segfaults on
> the first `Communications.transmit` over the adb connection, before writing a single byte:
> Garmin bug **CIQQA-4579** (acknowledged, no fix or workaround yet;
> <https://forums.garmin.com/developer/connect-iq/i/bug-reports/simulator-segfaults-on-communications-transmit-over-the-tethered-adb-connection-sdk-9-1-0-and-9-2-0-linux>).
> Reproduced here 2026-10-19 with SDK 9.2.0 **and 8.4.1**, against the phone and against a silent
> TCP listener on port 7381 in place of the phone, with a string payload as well as ours — so it
> is neither FP Client nor the message format. Phone → watch reportedly still works. Until Garmin
> fixes it, the watch → phone direction can only be tested on a real watch (PLAN gate).

The SDK's TETHERED mode replaces Garmin Connect with an adb tunnel to the simulator. It exercises
the protocol, queue, ack and pull — not the background wake-up, which exists only via Garmin Connect.

1. Phone on USB with adb; install a tethered debug build:
   `./gradlew :app:installDebug -PgarminTethered=true` (a normal debug/release build is WIRELESS).
2. `adb forward tcp:7381 tcp:7381`, start FP Client on the phone.
3. Start the simulator (`garmin/tools/run-simulator.sh`), run the app with `monkeydo`, then in the
   simulator menu **adb Connection → Start**.
4. On the watch press START: the queue count rises, the phone notifies "Garmin workout received",
   the ack brings the count back to 0. Kill FP Client and press START again: the workout stays
   queued until FP Client is started (its `pull`) — which mimics the lost-first-message case.

## Status on the phone and the watch (10e, part 1)

**Dormant by default.** FP Client 4.0 ships the link to everyone, but it stays dormant until FP
Wear is found: without Garmin Connect nothing runs; with it, the SDK starts with the process (so
Garmin Connect knows whom to wake for a first FP Wear message) and asks the paired watches at most
once a day whether FP Wear is installed. Only when it is — or a FP Wear message arrives — does
`GarminLink.active` (persisted) switch on the pulls, chunk requests, both 15-minute workers and
the full Settings card. It switches off again when every paired watch reports FP Wear gone and
nothing is pending. While dormant, phones with Garmin Connect see a short Settings card that lists
the paired watches and offers **Look for FP Wear**; opening Settings asks the watches too — so
installing FP Wear after FP Client never means waiting for the daily probe. SDK start is guarded, so a misbehaving Connect IQ SDK cannot crash FP Client.


- **FP Client → Settings → "Garmin watch (experimental)"** (`GarminWatchCard`; the full card with an
  active link or pending workouts): whether Garmin Connect is there and reachable
  (`GCM_UPGRADE_NEEDED` and friends in words), each paired watch with connected /
  not connected and *FP Wear installed / not installed* (asked from the watch via
  `getApplicationInfo`), the workouts not yet on FitPub with the newest error, and **Sync now**
  (manual retry of everything, then pull + fetch missing chunks + upload).
- **Banner** above every tab while Garmin workouts are on their way ("N Garmin workout(s) on the
  way to FitPub — <error>"), tapping opens Settings; the Wear OS banner stays separate.
- **Watch "Sync now" row:** "All sent to phone", or "Waiting: N · phone seen 3 h ago" / "Waiting:
  N · phone never replied" (the last FP Client message is stored as `lastPhone`) — "never" almost
  always means FP Client or Garmin Connect is missing on the phone.

## Licence and distribution (read 2026-10-19 — not legal advice)

The Connect IQ SDK **and** the Android companion library (`ciq-companion-app-sdk`, whose POM names
"Garmin CONNECT IQ SDK License Agreement and CONNECT IQ Application Developer Agreement") fall
under one agreement (§ IX.e: "references … to the SDK include the Companion SDK"):

- **Proprietary, not open source.** § I.a: a "limited, non-exclusive, personal, revocable …
  non-sublicensable" licence; § I.b forbids redistributing the Program Materials or deriving
  their source. Shipping the companion AAR inside an app is the intended use (Garmin publishes it
  on Maven Central for that).
  - **Decided 2026-10-19:** FP Client leaves F-Droid (whose main repository refuses proprietary
    dependencies — `play-services-wearable` already was one) and moves from AGPL-3.0 to
    **Apache-2.0** from 4.0 on, so combining it with Garmin's and Google's closed libraries is
    unproblematic. Releases up to 3.x stay available under AGPL-3.0. The sole author holds all
    copyright (git history), so no contributor consent was needed.
- **Distribution:** the agreement describes distribution through the Connect IQ Store (§ II.b)
  but does not forbid side-loading for testing; nothing requires the app's own source to be
  closed. Do not commit SDK files or the developer key (`.gitignore` covers both).
- **Privacy (Exhibit A):** an app that collects data **must have a privacy policy** (§ I.a);
  **location requires notice and opt-in** (§ I.c); data may not be kept longer than needed
  (§ I.b) or sold/transferred (§ I.d). See the draft below.

### Privacy policy section (draft, to merge into FP Client's policy)

> **FP Wear for Garmin watches.** The FP Wear watch app records your workout on your Garmin
> watch: GPS position, altitude, heart rate and step count, once per second, while you record.
> It saves the workout as a normal activity on your watch, which Garmin Connect syncs to your
> Garmin account under Garmin's own terms. FP Wear additionally sends the workout over Bluetooth,
> through the Garmin Connect app, to FP Client on your phone. FP Wear itself has no internet
> access and sends nothing anywhere else. On the watch, a workout is deleted as soon as the phone
> confirms it has received it. FP Client then uploads it to the FitPub instance you are signed in
> to, with the visibility you would get for a Wear OS workout (public by default; change it on
> FitPub), and then deletes the recorded samples; it keeps only a short summary (activity, time,
> distance, watch name) of recent workouts so that none is uploaded twice. No data goes to the FP
> Client developers or any other third party. Location is only recorded while you record a workout, after you
> start it on the watch.

## Connect IQ Store listing (draft — do not submit before the real-watch gate)

- **Export:** `"$SDK/monkeyc" -e -f monkey.jungle -o bin/fitpub.iq -y ~/garmin/developer_key.der -r`
  — one file for every target (138 KB for the six current targets; `.iq` is git-ignored).
- **Name:** FP Wear · **Type:** Device app · **Category:** Health & Fitness.
- **Short description:** Record workouts on your Garmin and share them to your FitPub instance.
- **Description:**
  > Record a run, walk, hike or ride on your Garmin watch and have it appear on your own FitPub
  > instance automatically — no Strava, no export, no manual upload.
  >
  > FP Wear records like the built-in activities (GPS, heart rate, steps) and also saves a normal
  > Garmin activity, so Garmin Connect keeps working as before. After you save, the workout goes
  > to the free FP Client app on your Android phone, which uploads it to the FitPub instance you
  > are signed in to.
  >
  > Requires: FP Client for Android (Google Play / GitHub), the Garmin Connect app on the same
  > phone, and an account on a FitPub instance. iPhone is not supported.
  >
  > Open source: https://github.com/pavel-janicek/fitpub-android
- **Permissions to explain:** Positioning (GPS track), Sensor (heart rate), Fit (save the native
  activity), Communications (send to the phone), Background (finish sending while the app is
  closed).
- **Still to do:** store icon and screenshots (the simulator can take them, but its window does
  not take scripted input reliably — capture by hand), privacy policy URL, the "beta" wording.

## Targets

`minApiLevel 3.4.0` (the Forerunner 55 has Connect IQ 3.4; everything else 5.x — the compiler still
checks each target against its own API). The list started as the installed device profiles spanning
the display classes and grows with the testers' watches:

| Device id | Display |
|---|---|
| `fenix7s` | 240 px round MIP (smallest) |
| `fr55` | 208 px round MIP, 64 colours; CIQ 3.4, 128 KB app / 32 KB background / 128 KB storage (added 2026-10-19 for a tester) |
| `fenix7` | 260 px round MIP |
| `fenix7x` | 280 px round MIP (added 2026-10-19 for a tester's fēnix 7X Solar) |
| `vivoactive5`, `vivoactive6` | 390 px round AMOLED |
| `epix2`, `epix2pro47mm` | 416 px round AMOLED (`epix2` added 2026-10-19 for a tester's epix Gen 2) |
| `fr965` | 454 px round AMOLED (added 2026-10-20 for a tester's Forerunner 965) |
| `fenix847mm` | 454 px round AMOLED (largest) |

Other Forerunners, Venu, Instinct (monochrome) and Edge are not targeted yet — add their profiles in
the SDK Manager and to `manifest.xml` when 10c's UI is sized for them.

## Decisions

- **App identity:** Connect IQ app id `acb5d45a68f54311b0223006a814250f` (fixed: the Store and the
  phone's Mobile SDK address the app by it). Display name "FP Wear", the same as the Wear OS app.
- **No layout XML:** the views draw from `dc` dimensions so one code path fits round and square
  displays of any size, the same intent as the `:wear` round-safe layouts.
- **Permissions added per step**, not up front: 10b `Communications` + `Background`, 10c
  `Positioning`, `Sensor`, `Fit` — the Store shows them to users at install.

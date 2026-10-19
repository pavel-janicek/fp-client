# FitPub for Garmin — toolchain & decision record (Iteration 10)

`garmin/` is the Connect IQ watch app ("FP Wear", the same name as on Wear OS) for Garmin watches. It records a workout on the
watch and hands it to FP Client on the phone, which builds the FIT and uploads it to FitPub like a
Wear OS workout. The goal, the chosen approach and the real-watch release gate are recorded in
PLAN.md → Iteration 10; this file covers what is needed to build and run it.

**Status:** 10a (scaffolding) is done. 10b (phone link) is implemented on both sides and
simulator-tested as far as a simulator can go; it is **not verified on a real watch** — the
release gate in PLAN.md → Iteration 10 still applies. The watch sends a *test* workout (START);
recording arrives with 10c.

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
  source/HomeView.mc           home screen: name, queue state (positions derived from the dc)
  source/HomeDelegate.mc       START queues and sends a test workout (10b prototype)
  source/ProtocolTest.mc       (:test) unit tests pinning the wire strings
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

**Side-load to a real watch:** build for that device id, connect the watch over USB and copy the
`.prg` to `GARMIN/APPS/`. Side-loaded apps get no Garmin Connect app settings, and whether the
phone link (Mobile SDK) works with them is an open 10b question.

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
| `workout` {id, created, label} | watch → phone | resent until acked (10b: a test workout) |
| `ack` {id} | phone → watch | stored on the phone; the watch drops it from its queue |
| `pull` | phone → watch | resend everything still queued |

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

## Targets

`minApiLevel 5.0.0`, current devices only. The list is the device profiles installed on the
development machine, chosen to span the display classes:

| Device id | Display |
|---|---|
| `fenix7s` | 240 px round MIP (smallest) |
| `fenix7` | 260 px round MIP |
| `vivoactive5`, `vivoactive6` | 390 px round AMOLED |
| `epix2pro47mm` | 416 px round AMOLED |
| `fenix847mm` | 454 px round AMOLED (largest) |

Forerunner, Venu, Instinct (monochrome) and Edge are not installed yet — add their profiles in
the SDK Manager and to `manifest.xml` when 10c's UI is sized for them.

## Decisions

- **App identity:** Connect IQ app id `acb5d45a68f54311b0223006a814250f` (fixed: the Store and the
  phone's Mobile SDK address the app by it). Display name "FP Wear", the same as the Wear OS app.
- **No layout XML:** the views draw from `dc` dimensions so one code path fits round and square
  displays of any size, the same intent as the `:wear` round-safe layouts.
- **Permissions added per step**, not up front: 10b `Communications` + `Background`, 10c
  `Positioning`, `Sensor`, `Fit` — the Store shows them to users at install.

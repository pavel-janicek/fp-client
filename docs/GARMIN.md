# FitPub for Garmin — toolchain & decision record (Iteration 10)

`garmin/` is the Connect IQ watch app ("FitPub") for Garmin watches. It records a workout on the
watch and hands it to FP Client on the phone, which builds the FIT and uploads it to FitPub like a
Wear OS workout. The goal, the chosen approach and the real-watch release gate are recorded in
PLAN.md → Iteration 10; this file covers what is needed to build and run it.

**Status: 10a (scaffolding) is done** — the app builds for every target and shows a static home
screen in the simulator. Nothing talks to the phone yet.

## Project map

```
garmin/
  manifest.xml                 Connect IQ manifest v3: app id, watch-app type, targets, minApiLevel
  monkey.jungle                build file (just points at the manifest)
  source/FitPubApp.mc          entry point (AppBase)
  source/HomeView.mc           home screen, positions derived from the dc (round + square safe)
  source/HomeDelegate.mc       home input (nothing to act on yet)
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
   binaries need the container. The scripts show the window through X11/XWayland and write to the
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
  phone's Mobile SDK address the app by it). Display name "FitPub", matching "FitPub Wear".
- **No layout XML:** the views draw from `dc` dimensions so one code path fits round and square
  displays of any size, the same intent as the `:wear` round-safe layouts.
- **Permissions added per step**, not up front: 10b `Communications`, 10c `Positioning`,
  `Sensor`, `Fit` — the Store shows them to users at install.

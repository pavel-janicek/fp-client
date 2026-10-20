# Testing FP Wear on a Garmin watch

Thank you for helping! FP Wear is a small app for your Garmin watch. You record a walk or run on
the watch, and the FP Client app on your phone shares it to FitPub. The whole setup takes about
15 minutes. After that you just go for a short walk.

**What you need**

- A Garmin watch from the list in step 3
- An Android phone with the **Garmin Connect** app, already paired with the watch
- A computer and the USB cable of your watch
- A FitPub account

If something goes wrong at any step, don't worry. Write to the group (step 1) and tell us at which
step you got stuck.

---

## Step 1: Join the tester group

Open **<https://groups.google.com/g/fp-client>** and click **Join group**.

Use the **same Google account as the Play Store on your phone**. Google Play lets you into the test
only through this group. News and new versions are also announced in the group, and this is where
you ask questions and report results. The group address is **fp-client@googlegroups.com**.

## Step 2: Get the test version of FP Client on your phone

1. On your phone, open **<https://play.google.com/apps/testing/com.fpclient.android>** and tap
   **Become a tester**. If the page says you can't join, wait a few minutes after step 1 and
   try again.
2. Open FP Client in the Play Store and install it, or update it if you already have it.
   The test version can take an hour or so to appear.
3. Check the version: Android **Settings → Apps → FP Client**. The version number is at the
   bottom of that screen, and it should be **4.0.0**. Write it down. You'll compare it with the
   watch in step 6.
4. Open FP Client, sign in to FitPub and allow notifications.

## Step 3: Find the file for your watch

Each watch model needs its own file. Find your watch in the list:

| Your watch | File to download |
|---|---|
| epix (Gen 2) | `fitpub-epix2_4_0_0.prg` |
| epix Pro (Gen 2) 47 mm | `fitpub-epix2pro47mm_4_0_0.prg` |
| fēnix 7 | `fitpub-fenix7_4_0_0.prg` |
| fēnix 7S | `fitpub-fenix7s_4_0_0.prg` |
| fēnix 7X | `fitpub-fenix7x_4_0_0.prg` |
| fēnix 8 47 mm | `fitpub-fenix847mm_4_0_0.prg` |
| Forerunner 55 | `fitpub-fr55_4_0_0.prg` |
| Forerunner 965 | `fitpub-fr965_4_0_0.prg` |
| vívoactive 5 | `fitpub-vivoactive5_4_0_0.prg` |
| vívoactive 6 | `fitpub-vivoactive6_4_0_0.prg` |

The `4_0_0` at the end is the version, **4.0.0**. It must match the FP Client version from step 2.

**Your watch is not in the list?** Write its exact name to the group. You'll find it on the watch
under **Settings → System → About**. We'll make a file for it.

## Step 4: Download it

On your computer, open the latest release on
**<https://github.com/pavel-janicek/fitpub-android/releases>**. Expand **Assets** and download the
file for your watch from step 3.

Don't rename the file.

## Step 5: Copy it to the watch

1. Connect the watch to the computer with its USB cable.
2. Open the watch like a USB stick:
   - **Windows:** it shows up in File Explorer as your watch's name.
   - **Linux:** it shows up in the file manager.
   - **Mac:** you need a free app such as [OpenMTP](https://openmtp.ganeshrvel.com/), because
     Finder can't open the watch.
3. Open the folder **GARMIN → APPS**.
4. **If an older FP Wear file is already there** (`fitpub-…prg`), delete it first. Two versions
   on one watch get in each other's way.
5. Copy the new `.prg` file into **GARMIN → APPS**.
6. Disconnect the watch. It may take a moment to update.

## Step 6: Check the version on the watch

1. On the watch, open the app list, the same place where you'd start a run. Find **FP Wear** and
   open it.
2. Scroll down to the bottom of the menu. The last line, **Version**, should say **4.0.0**.

The watch, the file name and the phone should all show the same version. If one of them differs,
stop here and write to the group.

## Step 7: Let the phone find the watch

The Garmin part of FP Client is hidden while it's being tested, so switch it on first:

1. Open FP Client and go to **Settings → About this app**.
2. Tap the line **Version 4.0.0 (63)** seven times. A short message says **Garmin testing on**.
3. Go back to **Settings**. Find the new card **Garmin watch (experimental)** and tap
   **Look for FP Wear**. The card should then show your watch with "FP Wear installed".

## Step 8: Go for a walk

1. On the watch, open **FP Wear**, choose **Walk** and wait for **GPS ready**.
2. Press **START**. Walk for at least 5 minutes.
3. Press **START** again to stop, then choose **Save**.

**What should happen:**

- The walk appears in Garmin Connect as usual.
- Within a few minutes your phone shows **"Garmin workout received"**, and then
  **"Garmin workout shared to FitPub"**.
- The walk appears on FitPub with its map, heart rate and steps.
- On the watch, the **Sync now** line in the FP Wear menu says **All sent to phone**.

A notification can take up to 15 minutes. Please wait that long before deciding something is
wrong.

## Step 9: Tell us how it went

Write to **fp-client@googlegroups.com**:

- your watch model and your phone model
- whether each item under "What should happen" worked
- anything odd you noticed, with a photo of the watch screen if that helps

That's it, thank you!

---

### Extra: send us the watch log (only if we ask)

If something didn't work, the watch's log helps us find out why. The watch writes it only if you
prepare an empty file first:

1. Connect the watch to the computer and open **GARMIN → APPS → LOGS**.
2. Create an empty text file there with the same name as the `.prg` file in capitals, but ending
   with `.TXT`. For example: `FITPUB-FR965_4_0_0.TXT`.
3. Disconnect the watch and repeat the walk from step 8.
4. Connect the watch again and send us that `.TXT` file. Also send `CIQ_LOG.YML` from the same
   folder if it's there.

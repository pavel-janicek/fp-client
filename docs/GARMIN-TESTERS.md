# Testing the Garmin Connect import

Thank you for helping! With this feature, activities you record on your Garmin watch reach
FitPub automatically. You don't install anything on the watch: FP Client gets them from your
Garmin Connect account. Setup takes about five minutes. After that you just go for a short walk.

> Linking opens to testers once Garmin approves FP Client for more users. Until then, the
> **Connect** button may end with "Garmin Connect™ could not be linked". We'll announce in the
> group when it works.

**What you need**

- A Garmin watch that syncs to **Garmin Connect**
- An Android phone
- A FitPub account

If something goes wrong at any step, don't worry. Write to the group (step 1) and tell us at which
step you got stuck.

---

## Tested FP Wear with version 4.0.0? Remove it from your watch

From FP Client **4.1.0**, the FP Wear watch app no longer works. The phone stops receiving its
workouts, and the Garmin Connect import below replaces it. Please remove FP Wear from your
watch.

1. **Before you update FP Client to 4.1.0, sync what is still on the watch.** Open FP Wear on the
   watch once, with the phone nearby and FP Client 4.0.0 signed in. Wait until FP Client no
   longer shows "Garmin workout(s) on the way to FitPub". Workouts left in FP Wear after the
   update can't reach FitPub any more. If FP Client has already updated, those workouts are
   lost, sorry.
2. Connect the watch to the computer with its USB cable, the same way as when you installed FP
   Wear:
   - **Windows:** the watch shows up in File Explorer.
   - **Mac:** use [OpenMTP](https://openmtp.ganeshrvel.com/).
3. Open the folder **GARMIN → APPS** and delete the FP Wear file, `fitpub-…prg` (for example
   `fitpub-fr965_4_0_0.prg`).
4. If you created a log file for us, also delete it from **GARMIN → APPS → LOGS**
   (`FITPUB-….TXT`).
5. Disconnect the watch. **FP Wear** disappears from the app list. If it is still there, restart
   the watch.

Can't find the file, or FP Wear stays on the watch? Write to the group with your watch model.

---

## Step 1: Join the tester group

Open **<https://groups.google.com/g/fp-client>** and click **Join group**.

Use the **same Google account as the Play Store on your phone**. Google Play lets you into the test
only through this group. News and new versions are also announced in the group, and this is where
you ask questions and report results. The group address is **fp-client@googlegroups.com**.

## Step 2: Get the test version of FP Client

1. On your phone, open **<https://play.google.com/apps/testing/com.fpclient.android>** and tap
   **Become a tester**. If the page says you can't join, wait a few minutes after step 1 and
   try again.
2. Open FP Client in the Play Store and install it, or update it if you already have it.
   The test version can take an hour or so to appear.
3. Open FP Client, sign in to FitPub and allow notifications.

## Step 3: Switch on Garmin testing

1. In FP Client, go to **Settings → About**.
2. Tap the line **Version 4.1.0 (…)** seven times. A short message says **Garmin testing on**.

## Step 4: Connect Garmin Connect™

1. Go to **Settings → Watches**. You'll see the **Garmin Connect™** card.
2. Read the note about the "Garmin [device model]" attribution and tick the box.
3. Tap **Connect Garmin Connect™**. A browser tab opens Garmin's sign-in page. Sign in and
   allow access, including **Activities**.
4. You return to FP Client with the message **Garmin Connect™ linked**. The card says
   **Connected**.

Your FitPub login never leaves the phone, and your Garmin password goes only to Garmin.

## Step 5: Choose who sees your activities

Imported activities get your **default visibility** from **Settings → Privacy**, which is
**Only me** unless you change it. Keep it for the test if you like. You can change each activity
on FitPub later.

## Step 6: Record and check

1. Record a short activity on the watch, 5–10 minutes is enough, and save it.
2. Let it sync to Garmin Connect, as you normally do.
3. Wait a few minutes. FP Client checks every 15 minutes, or tap **Sync now** in the card.
4. You get a notification **Garmin activity imported to FitPub**. On FitPub, the activity's
   description is **Garmin** followed by your watch model.

## Step 7: Tell us how it went

Write to the group:

- your watch model and phone,
- whether the activity arrived and how long it took,
- whether the map, distance, time, heart rate and activity type look right,
- anything the card showed in red.

## Stopping

Tap **Disconnect** in the card. FP Client's relay forgets your Garmin access and deletes anything
still waiting. Activities already on FitPub stay. You can also remove FP Client in Garmin Connect
(**Settings → Connected Apps**).

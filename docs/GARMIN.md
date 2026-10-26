# Garmin watches: import through Garmin Connect

FP Client imports activities recorded on any Garmin watch through **Garmin Connect™**. Users
never install anything on the watch. The watch syncs to Garmin Connect as usual, Garmin passes
each new activity to FP Client's relay, and the phone uploads it to the user's FitPub instance.

> Status: experimental. The Settings card is shown only to testers (seven taps on the version
> in **Settings → About**) until Garmin approves FP Client for more users. The plan entry is
> "Garmin Connect API" in [PLAN.md](../PLAN.md).

## How an activity travels

1. The user records on the watch. Garmin Connect syncs it.
2. Garmin notifies the relay (`connect.paveljanicek.cz`, run by the FP Client developer). The
   relay downloads the original activity file.
3. The relay encrypts the file and its metadata to keys that exist only on the user's phone, and
   queues them. It stores only ciphertext and the Garmin access tokens.
4. FP Client (`GarminConnectWorker`, every 15 minutes and on "Sync now") lists the queue,
   decrypts each file with `WebPushCrypto` (the same RFC 8291 code as mailbox push) and
   uploads it with the user's own FitPub login. The relay never sees FitPub credentials.
5. After FitPub has the activity, the phone tells the relay to delete its copy.

Activities that were created or uploaded on the Garmin Connect website are not imported. Only
watch recordings are.

## On the phone

| Piece | Role |
|---|---|
| `garmin/GarminConnectStore` | The relay mailbox (id, manage token, recipient keys) in keystore-encrypted preferences, excluded from backup; the cached link status; the tester switch. |
| `garmin/GarminConnectClient` | HTTP client for the relay. Separate from `ApiClient`, so no FitPub session material reaches the relay. |
| `garmin/GarminConnectSync` | Linking, status, disconnecting, and the import loop. |
| `garmin/GarminConnectWorker` | Runs the import in the background. |
| `garmin/GarminConnectReturnActivity` | Receives `fpclient://garmin-connect?result=…` after Garmin's sign-in and closes the Custom Tab. |
| `ui/settings/GarminConnectCard` | Settings → Watches → **Garmin Connect™**. |

Each import:

- skips an activity already on FitPub (start-time match, as for Wear OS workouts),
- uploads with the user's **default visibility** (Settings → Privacy, "Only me" unless changed),
- sets the description to **"Garmin [device model]"**,
- maps Garmin's sport to a FitPub activity type.

An activity FitPub rejects for good is removed from the relay, and the card shows why. The
original stays in Garmin Connect.

## Attribution

Garmin's API brand guidelines require a **"Garmin [device model]"** attribution next to the
title of any activity that came from a Garmin device ("Garmin" alone when the model is unknown),
also when it is shared further. FP Client cannot change how FitPub displays or federates an
activity, so the attribution is the activity's description. That way it travels with the
activity. Users confirm this before linking.

## Privacy

The relay's privacy policy is at <https://connect.paveljanicek.cz/privacy>. In short, the relay
holds:

- the Garmin tokens, until the user disconnects,
- encrypted files, until the phone collects them or for at most 30 days.

It never holds a FitPub login. **Disconnect** in the card deregisters FP Client at Garmin and
deletes everything on the relay. Removing FP Client in Garmin Connect (Settings → Connected
Apps) does the same.

## Manual route

Without the import, any Garmin activity can still be uploaded by hand. See the README section
"Recorded on a Garmin? Uploading your activities".

## History

FP Client 4.0 test builds briefly had **FP Wear**, a Connect IQ watch app that sent workouts to
the phone over the Connect IQ companion SDK. It was removed when the Garmin Connect import
worked. Garmin Connect covers every Garmin watch, needs no watch app, and no Connect IQ Store
listing. Phones that had a test build drop FP Wear's leftover data on start
(`GarminConnectStore.removeFpWearLeftovers`).

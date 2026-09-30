package com.fpclient.android.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Opens the keystore-backed [EncryptedSharedPreferences] file called [fileName], deleting and
 * recreating it when the stored keyset cannot be decrypted.
 *
 * The file's entries are sealed by a Tink keyset that is itself encrypted under an
 * AndroidKeyStore master key, and AndroidKeyStore keys never leave the device. A restore can
 * therefore hand the app the file without the key that opens it (reinstall under a different
 * signing key, restore onto a new device): opening then fails with `AEADBadTagException`, and
 * because both [com.fpclient.android.data.session.SessionStore] and
 * [com.fpclient.android.notifications.PushSubscriptionStore] open their file during
 * `Application.onCreate`, that one unreadable file used to crash-loop the app before it could
 * ever draw a frame. What the file held was unreadable anyway — dropping it and starting the
 * store empty trades dead keys for a login screen instead of a crash loop.
 *
 * Backup keeps new restores from carrying the poison (`res/xml/backup_rules.xml` and
 * `res/xml/data_extraction_rules.xml`); this is the guard for restores already in the cloud.
 */
internal fun openEncryptedPrefs(context: Context, fileName: String): SharedPreferences =
    openEncryptedPrefsOrReset(
        fileName = fileName,
        wipe = { context.deleteSharedPreferences(fileName) },
        open = {
            EncryptedSharedPreferences.create(
                context,
                fileName,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        },
    )

/**
 * Runs [open]; on any [Exception] runs [wipe] once, then retries [open] a second time. The
 * retry's failure — if there is one — propagates: there is nothing left to wipe.
 *
 * The broad catch is deliberate. A poisoned keyset surfaces as `GeneralSecurityException`
 * (`AEADBadTagException`, `KeyStoreException`), as `IOException` from a truncated file or as a
 * parse error from a mangled one, and the caller cannot tell a recoverable file from a broken
 * keystore — while a missed case means a crash loop at every start.
 */
internal fun openEncryptedPrefsOrReset(
    fileName: String,
    wipe: () -> Unit,
    open: () -> SharedPreferences,
): SharedPreferences = try {
    open()
} catch (failure: Exception) {
    Log.w(TAG, "Encrypted prefs '$fileName' failed to open; wiping and recreating", failure)
    wipe()
    open()
}

private const val TAG = "EncryptedPrefs"
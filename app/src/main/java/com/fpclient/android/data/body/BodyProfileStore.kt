package com.fpclient.android.data.body

import android.content.Context
import com.fpclient.android.util.openEncryptedPrefs
import com.fpclient.android.wear.WearBodyProfileSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only store for the [BodyProfile] a user opts in for calorie recording. It is health
 * data, so it lives in keystore-backed [openEncryptedPrefs] (like the session token) and is
 * excluded from cloud backup / device transfer — see `res/xml/backup_rules.xml` and
 * `res/xml/data_extraction_rules.xml`. It is never sent to the server; every save mirrors the
 * profile to the paired watch (see [WearBodyProfileSync]) so a watch workout uploaded directly
 * can carry the same calorie estimate the phone's relay path applies.
 */
class BodyProfileStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = openEncryptedPrefs(appContext, FILE_NAME)
    private val mutableProfile = MutableStateFlow(read())

    /** The stored profile; null-safe so a first-time user sees defaults. */
    val profile: StateFlow<BodyProfile> = mutableProfile.asStateFlow()

    /** One-shot synchronous read, e.g. to seed an edit form before a flow emits. */
    fun current(): BodyProfile = read()

    /** Persists [profile] and notifies observers. */
    fun save(profile: BodyProfile) {
        prefs.edit()
            .putBoolean(KEY_OPT_IN, profile.optIn)
            .putString(KEY_SEX, profile.sex.storageValue)
            .putString(KEY_HEIGHT_CM, profile.heightCm?.toString() ?: "")
            .putString(KEY_WEIGHT_KG, profile.weightKg?.toString() ?: "")
            .putString(KEY_AGE_YEARS, profile.ageYears?.toString() ?: "")
            .apply()
        mutableProfile.value = profile
        // Mirror to the paired watch so direct watch uploads can amend calories too; clearing
        // the profile pushes an empty one, wiping the watch's copy in the same step.
        WearBodyProfileSync.push(appContext, profile)
    }

    /** Opts out and wipes every stored value, keeping nothing on the device. */
    fun clear() = save(BodyProfile())

    private fun read(): BodyProfile {
        if (!prefs.getBoolean(KEY_OPT_IN, false)) return BodyProfile()
        return BodyProfile(
            optIn = true,
            sex = Sex.fromStorage(prefs.getString(KEY_SEX, null)),
            heightCm = prefs.getString(KEY_HEIGHT_CM, null)?.toDoubleOrNull(),
            weightKg = prefs.getString(KEY_WEIGHT_KG, null)?.toDoubleOrNull(),
            ageYears = prefs.getString(KEY_AGE_YEARS, null)?.toIntOrNull(),
        )
    }

    private companion object {
        const val FILE_NAME = "fitpub_body_profile"
        const val KEY_OPT_IN = "calorie_opt_in"
        const val KEY_SEX = "sex"
        const val KEY_HEIGHT_CM = "height_cm"
        const val KEY_WEIGHT_KG = "weight_kg"
        const val KEY_AGE_YEARS = "age_years"
    }
}

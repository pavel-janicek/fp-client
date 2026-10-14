package com.fpclient.android.wear.body

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.bodyProfileDataStore by preferencesDataStore(name = "fitpub_wear_body_profile")

/**
 * Watch-side copy of the body profile the phone sends over a Wear message (the phone's writer
 * is `WearBodyProfileSync`; `WatchWearAuthService` hands each message to [apply]). Read right
 * before a direct upload so the watch can amend the FIT file with an estimated calorie total on
 * its own — the phone-side relay path applies the same estimate from its own copy.
 *
 * Health data: it stays on the watch (backups are off for `:wear`) and is never sent to the
 * server. An opt-out from the phone arrives as an empty profile, which wipes every value here.
 */
object BodyProfileSync {

    private val KEY_OPT_IN = booleanPreferencesKey(BodyProfileProtocol.KEY_OPT_IN)
    private val KEY_SEX = stringPreferencesKey(BodyProfileProtocol.KEY_SEX)
    private val KEY_HEIGHT_CM = stringPreferencesKey(BodyProfileProtocol.KEY_HEIGHT_CM)
    private val KEY_WEIGHT_KG = stringPreferencesKey(BodyProfileProtocol.KEY_WEIGHT_KG)
    private val KEY_AGE_YEARS = stringPreferencesKey(BodyProfileProtocol.KEY_AGE_YEARS)

    /** Persists a profile received from the phone; an opt-out clears the stored values. */
    suspend fun apply(context: Context, profile: BodyProfile) {
        context.applicationContext.bodyProfileDataStore.edit { prefs ->
            prefs.clear()
            if (!profile.optIn) return@edit
            prefs[KEY_OPT_IN] = true
            prefs[KEY_SEX] = profile.sex.storageValue
            profile.heightCm?.let { prefs[KEY_HEIGHT_CM] = it.toString() }
            profile.weightKg?.let { prefs[KEY_WEIGHT_KG] = it.toString() }
            profile.ageYears?.let { prefs[KEY_AGE_YEARS] = it.toString() }
        }
    }

    /** The stored profile, or null when the phone never sent one / the read failed. */
    suspend fun read(context: Context): BodyProfile? = runCatching {
        val prefs = context.applicationContext.bodyProfileDataStore.data.first()
        if (prefs.asMap().isEmpty()) return@runCatching null
        BodyProfileProtocol.fromValues(
            optIn = prefs[KEY_OPT_IN] ?: false,
            sex = prefs[KEY_SEX],
            heightCm = prefs[KEY_HEIGHT_CM],
            weightKg = prefs[KEY_WEIGHT_KG],
            ageYears = prefs[KEY_AGE_YEARS],
        )
    }.getOrNull()
}

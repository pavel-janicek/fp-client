package com.fpclient.android.data.settings

import android.content.Context
import android.content.SharedPreferences

/** Tracks which one-time onboarding screens the user has already seen. */
class OnboardingStore(private val prefs: SharedPreferences) {

    constructor(context: Context) :
        this(context.getSharedPreferences("fitpub_onboarding", Context.MODE_PRIVATE))

    val visibilityOnboardingDone: Boolean
        get() = prefs.getBoolean(KEY_VISIBILITY_DONE, false)

    fun markVisibilityOnboardingDone() {
        prefs.edit().putBoolean(KEY_VISIBILITY_DONE, true).apply()
    }

    companion object {
        private const val KEY_VISIBILITY_DONE = "visibility_onboarding_done"
    }
}

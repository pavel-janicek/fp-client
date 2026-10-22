package com.fpclient.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.fpclient.android.data.dto.ActivityVisibilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The visibility every new activity starts with (Settings → Privacy): phone recordings, file
 * uploads, manual entries, Wear OS workouts without a choice made on the watch, FP Wear and
 * Garmin Connect imports. "Only me" by default, so nothing is shared or federated until the user
 * decides so. Forms still let the user pick another visibility per activity.
 */
class DefaultVisibilityStore internal constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences("fitpub_upload_defaults", Context.MODE_PRIVATE))

    private val mutableVisibility = MutableStateFlow(
        prefs.getString(KEY, null)?.takeIf { it in ActivityVisibilities.ALL } ?: DEFAULT,
    )
    val visibility: StateFlow<String> = mutableVisibility.asStateFlow()

    fun set(visibility: String) {
        if (visibility !in ActivityVisibilities.ALL) return
        prefs.edit().putString(KEY, visibility).apply()
        mutableVisibility.value = visibility
    }

    companion object {
        private const val KEY = "default_visibility"
        val DEFAULT = ActivityVisibilities.PRIVATE
    }
}

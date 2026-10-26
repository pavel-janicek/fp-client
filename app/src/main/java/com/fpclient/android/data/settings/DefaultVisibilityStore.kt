package com.fpclient.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.fpclient.android.data.dto.ActivityVisibilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The visibility every new activity starts with (Settings → Privacy): phone recordings, file
 * uploads, manual entries, Wear OS workouts (the watch gets a copy through [onChange] and the
 * auth handshake) and Garmin Connect imports. "Only me" by default, so
 * nothing is shared or federated until the user decides so. Forms still let the user pick
 * another visibility per activity.
 */
class DefaultVisibilityStore internal constructor(
    private val prefs: SharedPreferences,
    private val onChange: (String) -> Unit = {},
) {

    constructor(context: Context, onChange: (String) -> Unit) :
        this(context.getSharedPreferences("fitpub_upload_defaults", Context.MODE_PRIVATE), onChange)

    private val mutableVisibility = MutableStateFlow(
        prefs.getString(KEY, null)?.takeIf { it in ActivityVisibilities.ALL } ?: DEFAULT,
    )
    val visibility: StateFlow<String> = mutableVisibility.asStateFlow()

    fun set(visibility: String) {
        if (visibility !in ActivityVisibilities.ALL) return
        prefs.edit().putString(KEY, visibility).apply()
        mutableVisibility.value = visibility
        onChange(visibility)
    }

    companion object {
        private const val KEY = "default_visibility"
        val DEFAULT = ActivityVisibilities.PRIVATE
    }
}

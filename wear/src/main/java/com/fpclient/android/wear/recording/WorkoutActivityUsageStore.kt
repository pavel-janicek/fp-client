package com.fpclient.android.wear.recording

import android.content.Context

class WorkoutActivityUsageStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun recordUsage(type: WorkoutActivityType) {
        val count = preferences.getInt(type.name, 0)
        preferences.edit().putInt(type.name, count + 1).apply()
    }

    fun sortedTypes(): List<WorkoutActivityType> {
        return WorkoutActivityType.entries.sortedByDescending { preferences.getInt(it.name, 0) }
    }

    companion object {
        private const val PREFERENCES_NAME = "fitpub_watch_activity_usage"
    }
}

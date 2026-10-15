package com.fpclient.android.wear.recording

import android.content.Context

class WorkoutActivityUsageStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun recordUsage(type: WorkoutActivityType) {
        val count = preferences.getInt(type.name, 0)
        preferences.edit().putInt(type.name, count + 1).putString(LAST_USED_KEY, type.name).apply()
    }

    /** The type the quick-start tile offers: the last one started, else the most used. */
    fun lastUsedType(): WorkoutActivityType =
        preferences.getString(LAST_USED_KEY, null)
            ?.let { name -> WorkoutActivityType.entries.firstOrNull { it.name == name } }
            ?: sortedTypes().first()

    fun sortedTypes(): List<WorkoutActivityType> {
        return WorkoutActivityType.entries.sortedByDescending { preferences.getInt(it.name, 0) }
    }

    companion object {
        private const val PREFERENCES_NAME = "fitpub_watch_activity_usage"

        /** Not a type name, so it never collides with the per-type usage counters. */
        private const val LAST_USED_KEY = "last_used"
    }
}

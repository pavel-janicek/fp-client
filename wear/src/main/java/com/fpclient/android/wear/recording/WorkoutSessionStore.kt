package com.fpclient.android.wear.recording

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutSessionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: WorkoutSessionSnapshot) {
        preferences.edit().putString(SNAPSHOT_KEY, encode(snapshot)).commit()
    }

    fun load(): WorkoutSessionSnapshot? = preferences.getString(SNAPSHOT_KEY, null)?.let(::decode)

    fun clear() {
        preferences.edit().remove(SNAPSHOT_KEY).commit()
    }

    companion object {
        private const val PREFERENCES_NAME = "fitpub_workout_recording"
        private const val SNAPSHOT_KEY = "session"
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(snapshot: WorkoutSessionSnapshot): String = json.encodeToString(snapshot)

        fun decode(value: String): WorkoutSessionSnapshot? =
            runCatching { json.decodeFromString<WorkoutSessionSnapshot>(value) }.getOrNull()
    }
}
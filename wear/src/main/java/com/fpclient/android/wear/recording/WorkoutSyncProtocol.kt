package com.fpclient.android.wear.recording

internal object WorkoutSyncProtocol {
    const val DATA_PATH_PREFIX = "/fitpub/workout/"
    const val ACK_PATH = "/fitpub/workout/synced"
    const val KEY_ID = "session_id"
    const val KEY_ACTIVITY_TYPE = "activity_type"
    const val KEY_TITLE = "title"
    const val KEY_DESCRIPTION = "description"
    const val KEY_VISIBILITY = "visibility"
    const val KEY_OWNER_SERVER = "owner_server"
    const val KEY_OWNER_USERNAME = "owner_username"
    const val KEY_NODE_ID = "node_id"

    /**
     * Monotonic-ish relay timestamp. The Data Layer drops change events for byte-identical
     * DataItem puts, so every re-relay must carry a fresh value — otherwise "Sync pending now"
     * on the watch re-puts the same workout and the phone is never told to look at it again.
     */
    const val KEY_SYNC_ATTEMPT = "sync_attempt"
    const val ASSET_GPX = "workout_gpx"
    const val ASSET_SIDECAR = "workout_sidecar"
}
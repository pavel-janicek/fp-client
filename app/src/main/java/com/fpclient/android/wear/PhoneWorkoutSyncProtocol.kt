package com.fpclient.android.wear

internal object PhoneWorkoutSyncProtocol {
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
    const val ASSET_GPX = "workout_gpx"
    const val ASSET_SIDECAR = "workout_sidecar"
}

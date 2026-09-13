package com.fpclient.android.util

/**
 * Share helpers for activities: the public web URL of an activity and the
 * social share text used by the activity-detail share button.
 */
object ShareLinks {

    /** Full web URL of an activity on the instance, e.g. `https://fitpub.social/activities/{id}`. */
    fun publicActivityUrl(serverUrl: String, activityId: String): String =
        UrlBuilder.join(serverUrl, "activities/$activityId")

    /** Social share text announcing a finished activity together with its public web link. */
    fun activityShareText(activityName: String, serverUrl: String, activityId: String): String =
        "I just finished: $activityName check it out at: ${publicActivityUrl(serverUrl, activityId)}"
}

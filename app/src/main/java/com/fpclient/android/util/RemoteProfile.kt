package com.fpclient.android.util

import com.fpclient.android.data.dto.ActorDto
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where a federated user's full profile lives. The home instance's discover-remote answer
 * carries only a minimal actor card, but another FitPub instance serves its users' public
 * profile, public activities and peaks without authentication — the same anonymous reads
 * [RemoteActivity] does for a federated activity, through `ApiClient.originApi`, which never
 * sends the session to another instance.
 */
object RemoteProfile {

    /** [serverUrl] has no trailing slash; [username] is the user's name on that instance. */
    data class Origin(val serverUrl: String, val username: String) {
        val host: String get() = ActorHandle.hostOf(serverUrl).orEmpty()

        /** A federated activity's page on this instance, which [RemoteActivity.origin] reads. */
        fun activityUri(activityId: String) = "$serverUrl/activities/$activityId"
    }

    private val USERNAME = Regex("[A-Za-z0-9_.-]{1,64}")

    /**
     * The origin of [actor], or null when there is none to read from: the actor is local, the
     * home instance did not classify its server as FitPub (Mastodon and the rest speak another
     * API), or its URI is not a FitPub actor (`https://host[:port]/users/{username}`).
     */
    fun origin(actor: ActorDto?): Origin? {
        if (actor == null || actor.local || !actor.instanceType.equals("FITPUB", ignoreCase = true)) return null
        val url = actor.actorUri?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val segments = url.pathSegments
        if (segments.size != 2 || segments[0] != "users" || !USERNAME.matches(segments[1])) return null
        val port = if (url.port == 443) "" else ":${url.port}"
        return Origin(serverUrl = "https://${url.host}$port", username = segments[1])
    }
}

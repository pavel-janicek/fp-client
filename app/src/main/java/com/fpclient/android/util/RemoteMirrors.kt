package com.fpclient.android.util

import com.fpclient.android.data.dto.ActivityDto
import com.fpclient.android.data.dto.ActivityMetricsDto
import com.fpclient.android.data.dto.TimelineActivityDto

/**
 * The home instance's copy of federated timeline items, kept in memory by mirror id.
 *
 * A followers-only activity on another FitPub instance is in the follower's timeline, but its
 * origin refuses the anonymous detail read (403: only a signed server-to-server request from a
 * follower's instance gets it), and the home instance has no detail route for its mirror.
 * The timeline item is then all the app knows, so the detail screen shows that instead of a
 * dead end. Only the last [MAX] opened items are kept; nothing is persisted.
 */
class RemoteMirrors {
    private val items = object : LinkedHashMap<String, TimelineActivityDto>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TimelineActivityDto>?) = size > MAX
    }

    @Synchronized
    fun remember(item: TimelineActivityDto) {
        if (item.id.isNotBlank()) items[item.id] = item
    }

    @Synchronized
    fun get(mirrorId: String): TimelineActivityDto? = items[mirrorId]

    companion object {
        const val MAX = 50

        /** The timeline item as a (reduced) detail: no route file, comments or full metrics. */
        fun toDetail(item: TimelineActivityDto): ActivityDto = ActivityDto(
            id = item.id,
            activityType = item.activityType,
            title = item.title,
            description = item.description,
            startedAt = item.startedAt,
            endedAt = item.endedAt,
            timezone = item.timezone,
            visibility = item.visibility,
            totalDistance = item.totalDistance,
            totalDurationSeconds = item.totalDurationSeconds,
            elevationGain = item.elevationGain,
            elevationLoss = item.elevationLoss,
            metrics = item.metrics?.let { m ->
                ActivityMetricsDto(
                    averageSpeed = m.averageSpeed,
                    maxSpeed = m.maxSpeed,
                    averagePaceSeconds = m.averagePaceSeconds,
                    averageHeartRate = m.averageHeartRate,
                    maxHeartRate = m.maxHeartRate,
                    averagePower = m.averagePower,
                    calories = m.calories,
                    movingTimeSeconds = m.movingTimeSeconds ?: item.movingTimeSeconds,
                    stoppedTimeSeconds = m.stoppedTimeSeconds ?: item.stoppedTimeSeconds,
                )
            },
            routeDownloadAvailable = false,
            createdAt = item.createdAt,
            activityLocation = item.activityLocation,
            username = item.username,
            displayName = item.displayName,
            avatarUrl = item.avatarUrl,
            actorUri = item.actorUri,
            isLocal = false,
            simplifiedTrack = item.simplifiedTrack,
            hasGpsTrack = item.simplifiedTrack != null,
            indoor = item.indoor,
            context = item.context,
            likesCount = item.likesCount,
            commentsCount = item.commentsCount,
            reactionCounts = item.reactionCounts,
            currentUserReaction = item.currentUserReaction,
        )
    }
}

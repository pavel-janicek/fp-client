package com.fpclient.android.util

import com.fpclient.android.data.dto.TimelineActivityDto
import com.fpclient.android.data.dto.TimelineMetricsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMirrorsTest {

    @Test
    fun keepsOnlyTheMostRecentItems() {
        val mirrors = RemoteMirrors()
        repeat(RemoteMirrors.MAX + 1) { mirrors.remember(TimelineActivityDto(id = "a$it")) }
        assertNull(mirrors.get("a0"))
        assertNotNull(mirrors.get("a${RemoteMirrors.MAX}"))
    }

    @Test
    fun ignoresItemsWithoutId() {
        val mirrors = RemoteMirrors()
        mirrors.remember(TimelineActivityDto(id = ""))
        assertNull(mirrors.get(""))
    }

    @Test
    fun timelineItemBecomesReducedDetail() {
        val detail = RemoteMirrors.toDetail(
            TimelineActivityDto(
                id = "mirror",
                title = "Evening Run",
                visibility = "FOLLOWERS",
                totalDistance = 8200.0,
                movingTimeSeconds = 2400,
                username = "merms",
                actorUri = "https://fitpub.social/users/merms",
                activityUri = "https://fitpub.social/activities/aab1b1be-7272-4b75-944a-b0eb9736e602",
                metrics = TimelineMetricsDto(averageHeartRate = 150),
            ),
        )
        assertEquals("mirror", detail.id)
        assertEquals("Evening Run", detail.title)
        assertEquals("FOLLOWERS", detail.visibility)
        assertEquals(8200.0, detail.totalDistance!!, 0.0)
        assertEquals(150, detail.metrics?.averageHeartRate)
        assertEquals(2400L, detail.metrics?.movingTimeSeconds)
        assertEquals("@merms@fitpub.social", detail.fullHandle)
        assertFalse(detail.routeDownloadAvailable!!)
        assertFalse(detail.isLocal)
    }
}

package com.fpclient.android.data.dto

import com.fpclient.android.ui.components.boostAttribution
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineBoostEventTest {

    // makni.cz public timeline, 2026-10-16: the same activity as its ORIGINAL post and as a BOOST
    // (trimmed to the fields that matter). Keying the LazyColumn by `id` crashed the app.
    private val page = Json { ignoreUnknownKeys = true }.decodeFromString<PageEnvelopeTimelineDto>(
        """
        {
          "content": [
            {
              "id": "6bebe22e-24c9-4acc-8ea5-1f45362443d6",
              "title": "Nordic Walking",
              "eventId": "54f5e148-828f-4172-a052-9c9766ae62a2",
              "eventKind": "BOOST",
              "eventAt": "2026-10-15T15:05:16Z",
              "booster": {
                "id": "54f5ee39-d1a5-404b-a4af-f42a9c25cc40",
                "username": "aikencz",
                "displayName": "Lukáš Jelínek",
                "actorUri": "https://f.cz/users/aikencz",
                "isLocal": false
              }
            },
            {
              "id": "6bebe22e-24c9-4acc-8ea5-1f45362443d6",
              "title": "Nordic Walking",
              "eventId": "6bebe22e-24c9-4acc-8ea5-1f45362443d6",
              "eventKind": "ORIGINAL",
              "eventAt": "2026-10-15T15:03:02.257377Z",
              "booster": null
            }
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun boostAndOriginalShareTheActivityIdButNotTheTimelineKey() {
        val (boost, original) = page.content
        assertEquals(boost.id, original.id)
        assertNotEquals(boost.timelineKey, original.timelineKey)
        assertEquals(2, page.content.distinctBy { it.timelineKey }.size)
    }

    @Test
    fun itemsFromServersWithoutEventsFallBackToTheActivityId() {
        assertEquals("a1", TimelineActivityDto(id = "a1").timelineKey)
    }

    @Test
    fun onlyTheBoostEventCarriesAnAttributionLine() {
        val (boost, original) = page.content
        assertTrue(boostAttribution(boost)!!.startsWith("🔁 Lukáš Jelínek boosted"))
        assertNull(boostAttribution(original))
    }
}

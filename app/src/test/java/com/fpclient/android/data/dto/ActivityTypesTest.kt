package com.fpclient.android.data.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recording picker and the emoji shown next to an activity are driven by this list, which
 * mirrors the server's `Activity.ActivityType` enum. When the server gained `FISTBALL` the app
 * did not follow, so such an activity could not be recorded and rendered with the generic
 * "other" gym icon. A missing entry is invisible in review, so the whole enum is pinned here.
 */
class ActivityTypesTest {

    /** `Activity.ActivityType` in `social.fitpub.activity.entity.Activity`, in server order. */
    private val serverEnum = listOf(
        "RUN", "RIDE", "HIKE", "WALK", "SWIM", "ALPINE_SKI", "BACKCOUNTRY_SKI",
        "NORDIC_SKI", "SNOWBOARD", "ROWING", "KAYAKING", "CANOEING", "INLINE_SKATING",
        "ROCK_CLIMBING", "MOUNTAINEERING", "TENNIS", "FISTBALL", "YOGA", "WORKOUT", "OTHER",
    )

    @Test
    fun thePickerOffersExactlyTheServersActivityTypes() {
        assertEquals(serverEnum, ActivityTypes.ALL)
    }

    @Test
    fun everyActivityTypeHasItsOwnIcon() {
        val generic = ActivityTypes.icon("SOMETHING_NEW")
        for (type in serverEnum) {
            assertTrue(
                "$type falls back to the generic icon",
                ActivityTypes.icon(type) != generic || type == "OTHER",
            )
        }
    }

    @Test
    fun fistballUsesTheBallIcon() {
        assertEquals("🏐", ActivityTypes.icon("FISTBALL"))
    }
}
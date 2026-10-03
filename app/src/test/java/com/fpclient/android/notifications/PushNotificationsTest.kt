package com.fpclient.android.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the channel filter behind [PushNotifications.cancelAll]: "Mark all read" sweeps
 * every notification this object posted (they all live on [PushNotifications.CHANNEL_ID])
 * so the launcher badge clears, while the ongoing notifications of other services — each
 * on its own channel — must never be caught in the sweep.
 */
class PushNotificationsTest {

    @Test
    fun `push notifications are swept by cancelAll`() {
        assertTrue(
            PushNotifications.isPushNotificationChannel(PushNotifications.CHANNEL_ID),
        )
    }

    @Test
    fun `the ongoing track-recording notification survives the sweep`() {
        assertFalse(PushNotifications.isPushNotificationChannel("track_recording"))
    }

    @Test
    fun `the ongoing instant-delivery notification survives the sweep`() {
        assertFalse(PushNotifications.isPushNotificationChannel("fitpub_instant_delivery"))
    }

    @Test
    fun `notifications without a channel are never swept`() {
        assertFalse(PushNotifications.isPushNotificationChannel(null))
    }
}

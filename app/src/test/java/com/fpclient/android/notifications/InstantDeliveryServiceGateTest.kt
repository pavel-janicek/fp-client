package com.fpclient.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Iteration 8j — who is allowed to open the ntfy socket.
 *
 * The receiver is a long-lived foreground service holding a connection open for a
 * specific account, so "connects when it shouldn't" is the failure that matters:
 * a guest, a signed-out device or a *different* account's subscription on the same
 * phone must never connect, because that would show the wrong user the wrong
 * notifications. The gate is pulled out of `onStartCommand` for exactly this
 * reason — it is pure and testable without a running service, and it mirrors
 * `PushFetchWorker.doWork` (and, like it, exits by simply not starting).
 */
class InstantDeliveryServiceGateTest {

    private val owner = NotificationPolling.cursorOwner("https://fitpub.test", "sam")
    private val otherOwner = NotificationPolling.cursorOwner("https://fitpub.test", "someone-else")

    @Test
    fun aGuestConnectsToNothing() {
        assertNull(
            resolve(isLoggedIn = false, owner = null),
        )
    }

    @Test
    fun aSignedOutSessionConnectsToNothing() {
        // A stale subscription can outlive the sign-in that created it (the local
        // keys are only wiped by an explicit disable), so the sign-out check is not
        // redundant with the owner check.
        assertNull(resolve(isLoggedIn = false, owner = owner))
        assertNull(resolve(isLoggedIn = false, owner = null, subscription = subscription()))
    }

    @Test
    fun anotherAccountsSubscriptionNeverConnects() {
        assertNull(
            "a different account's subscription must not be used",
            resolve(isLoggedIn = true, owner = otherOwner, subscription = subscription()),
        )
        assertNull(resolve(isLoggedIn = true, owner = "https://elsewhere|kim", subscription = subscription()))
    }

    @Test
    fun noSubscriptionMeansNoConnection() {
        assertNull(resolve(isLoggedIn = true, owner = owner, subscription = null))
    }

    @Test
    fun instantDeliverySwitchedOffMeansNoConnection() {
        assertNull(
            resolve(isLoggedIn = true, owner = owner, subscription = subscription(instantEnabled = false)),
        )
    }

    @Test
    fun aMissingTopicOrServerMeansNoConnection() {
        // A half-written config must fail closed rather than dial a wrong host.
        assertNull(resolve(isLoggedIn = true, owner = owner, subscription = subscription(topic = null)))
        assertNull(resolve(isLoggedIn = true, owner = owner, subscription = subscription(topic = "  ")))
        assertNull(resolve(isLoggedIn = true, owner = owner, ntfyServer = "   "))
    }

    @Test
    fun theOwningAccountWithInstantDeliveryOnConnects() {
        val target = resolve(isLoggedIn = true, owner = owner, subscription = subscription())
        assertNotNull(target)
        assertEquals("fp-abc123", target!!.topic)
        assertEquals("https://ntfy.example", target.ntfyServer)
        assertTrue(target.subscription.instantEnabled)
    }

    // ------------------------------------------------------- the ongoing notification

    /**
     * The ongoing notification has to be independently switchable, because that is the answer
     * the app gives to "can I make this go away?": hide this channel, keep the connection.
     * If it shared a channel with the notifications the user actually wants, hiding it would
     * silence those too and the claim would be a lie.
     */
    @Test
    fun theOngoingNotificationHasItsOwnChannel() {
        assertEquals(InstantDeliveryService.CHANNEL_ID, "fitpub_instant_delivery")
        assertTrue(
            "must not share a channel with the notifications the user wants to receive",
            InstantDeliveryService.CHANNEL_ID != PushNotifications.CHANNEL_ID,
        )
        assertTrue(
            "must not share the track-recording channel either",
            InstantDeliveryService.CHANNEL_ID != "track_recording",
        )
    }

    /**
     * Android 13 (API 33) made foreground-service notifications dismissible on purpose, and an
     * `ongoing` notification opts out of that. Below 33 the platform does not allow it, so
     * claiming dismissibility there would be a promise the OS breaks.
     */
    @Test
    fun theOngoingNotificationIsDismissibleWhereAndroidAllowsIt() {
        val service = InstantDeliveryService()
        assertTrue("API 33+ allows it", service.notificationIsDismissible(33))
        assertTrue("and later", service.notificationIsDismissible(36))
        assertFalse("API 32 does not", service.notificationIsDismissible(32))
        assertFalse("and below", service.notificationIsDismissible(26))
    }

    // ------------------------------------------------------------------ helpers

    private fun resolve(
        isLoggedIn: Boolean,
        owner: String?,
        subscription: PushSubscription? = this.subscription(),
        ntfyServer: String? = "https://ntfy.example",
    ) = InstantDeliveryService().resolveTarget(isLoggedIn, owner, subscription, ntfyServer)

    private fun subscription(
        instantEnabled: Boolean = true,
        topic: String? = "fp-abc123",
    ) = PushSubscription(
        owner = this.owner,
        mailboxBase = "https://push.example",
        endpoint = "https://push.example/push/abc",
        publicKey = PushFixture.P256DH,
        privateKey = PushFixture.PRIVATE,
        authSecret = PushFixture.AUTH,
        manageToken = "manage",
        instantEnabled = instantEnabled,
        ntfyTopic = topic,
    )
}

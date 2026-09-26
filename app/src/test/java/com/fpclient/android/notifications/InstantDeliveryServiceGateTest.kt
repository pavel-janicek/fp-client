package com.fpclient.android.notifications

import org.junit.Assert.assertEquals
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

    @Test
    fun backoffGrowsAndIsCapped() {
        val service = InstantDeliveryService()
        assertEquals(0, service.backoffMillis(0))
        val first = service.backoffMillis(1)
        // Jitter means only the bounds are assertable; the cap is the real contract.
        assertTrue("first retry must wait at least the base delay", first >= InstantDeliveryService.BASE_BACKOFF_MS)
        for (attempt in 1..40) {
            val delay = service.backoffMillis(attempt)
            assertTrue("attempt $attempt waited $delay", delay >= InstantDeliveryService.BASE_BACKOFF_MS)
            assertTrue("attempt $attempt waited $delay", delay <= InstantDeliveryService.MAX_BACKOFF_MS)
        }
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

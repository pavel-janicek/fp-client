package com.fpclient.android.notifications

import android.content.SharedPreferences
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

/**
 * The 8i bookkeeping in the store: that switching instant delivery on changes *only* the topic
 * and the flag (the relay is handed the very same keypair), that disabling anything wipes the
 * manage token too, and that a generated topic is unguessable and inside ntfy's own rules.
 */
class PushSubscriptionStoreTest {

    private val prefs: SharedPreferences = Mockito.mock(SharedPreferences::class.java)
    private val editor: SharedPreferences.Editor = Mockito.mock(SharedPreferences.Editor::class.java)
    private val removed = mutableListOf<String>()

    @Before
    fun setUp() {
        removed.clear()
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(Mockito.anyString(), Mockito.any())).thenReturn(editor)
        `when`(editor.putBoolean(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(editor)
        val recorded: MutableList<String> = removed
        Mockito.doAnswer { invocation ->
            recorded += invocation.getArgument<String>(0)
            editor
        }.`when`(editor).remove(Mockito.anyString())
        // apply()/commit() return Unit on a void method — nothing to stub.
    }

    @Test
    fun setInstantForward_updatesTheActiveSubscriptionInPlace() = runTest {
        val store = PushSubscriptionStore(prefs)
        // A store that has never saved anything must not crash here — the flow update is
        // guarded, the preference write still happens.
        store.setInstantForward(true, "fp-orphan")
        assertNull(store.subscription.value)

        val original = subscription()
        store.save(original)
        store.setInstantForward(true, "fp-abc")

        val updated = store.subscription.value ?: throw AssertionError("nothing stored")
        assertTrue(updated.instantEnabled)
        assertEquals("fp-abc", updated.ntfyTopic)
        // Nothing about the key material may change: the relay decrypts with the *same* key.
        assertEquals(original.publicKey, updated.publicKey)
        assertEquals(original.privateKey, updated.privateKey)
        assertEquals(original.authSecret, updated.authSecret)
        assertEquals(original.manageToken, updated.manageToken)
    }

    @Test
    fun setInstantForward_clearsTheTopicWhenSwitchedBackOff() = runTest {
        val store = PushSubscriptionStore(prefs)
        store.save(subscription().copy(instantEnabled = true, ntfyTopic = "fp-abc"))

        store.setInstantForward(false, null)

        val updated = store.subscription.value ?: throw AssertionError("subscription vanished")
        assertFalse(updated.instantEnabled)
        assertNull("a stale topic would keep inviting ntfy subscribers", updated.ntfyTopic)
    }

    @Test
    fun clear_removesTheManageTokenAndTheTopicToo() = runTest {
        val store = PushSubscriptionStore(prefs)
        store.save(subscription().copy(instantEnabled = true, ntfyTopic = "fp-abc"))

        store.clear()

        assertTrue("the manage token must go", "manage_token" in removed)
        assertTrue("the instant flag must go", "instant_enabled" in removed)
        assertTrue("the topic must go", "ntfy_topic" in removed)
        assertTrue("the private key must go", "private_key" in removed)
        assertNull(store.subscription.value)
    }

    @Test
    fun isValidNtfyTopic_matchesWhatTheRelayAccepts() {
        assertTrue(PushSubscriptionStore.isValidNtfyTopic("fp-0123456789abcdef"))
        assertTrue(PushSubscriptionStore.isValidNtfyTopic("a"))
        assertTrue(PushSubscriptionStore.isValidNtfyTopic("a".repeat(64)))
        assertFalse("ntfy topics are lower-case", PushSubscriptionStore.isValidNtfyTopic("Topic"))
        assertFalse(PushSubscriptionStore.isValidNtfyTopic("has space"))
        assertFalse(PushSubscriptionStore.isValidNtfyTopic("dot.topic"))
        assertFalse(PushSubscriptionStore.isValidNtfyTopic(""))
        assertFalse(PushSubscriptionStore.isValidNtfyTopic("a".repeat(65)))
    }

    @Test
    fun generateNtfyTopic_isUnguessableAndFitsNtfysLimit() {
        val topics = (1..50).map { PushSubscriptionStore.generateNtfyTopic() }
        assertEquals("topics must not repeat", topics.size, topics.toSet().size)
        topics.forEach {
            assertTrue(PushSubscriptionStore.isValidNtfyTopic(it))
            assertTrue("must fit ntfy's 64-char limit: $it", it.length <= 64)
        }
    }

    private fun subscription() = PushSubscription(
        owner = NotificationPolling.cursorOwner("https://fitpub.test", "sam"),
        mailboxBase = "https://push.example.test",
        endpoint = "https://push.example.test/push/abc",
        publicKey = b64(ByteArray(65)),
        privateKey = b64(ByteArray(32)),
        authSecret = b64(ByteArray(16)),
        manageToken = "manage-secret",
    )

    private fun b64(bytes: ByteArray) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

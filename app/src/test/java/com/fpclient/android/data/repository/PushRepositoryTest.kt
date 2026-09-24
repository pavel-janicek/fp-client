package com.fpclient.android.data.repository

import android.content.SharedPreferences
import com.fpclient.android.data.network.ApiResult
import com.fpclient.android.data.network.FitPubApi
import com.fpclient.android.data.network.MailboxClient
import com.fpclient.android.data.session.Session
import com.fpclient.android.data.session.SessionStore
import com.fpclient.android.notifications.NotificationPolling
import com.fpclient.android.notifications.PushFixture
import com.fpclient.android.notifications.PushSubscription
import com.fpclient.android.notifications.PushSubscriptionStore
import com.fpclient.android.notifications.WebPushCrypto
import java.util.Base64
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Pins the 8h wire contract against a MockWebServer playing instance + relay: the vapid-key
 * probe (503 = "push disabled on this instance"), the enable order (mint mailbox →
 * POST /subscribe with `{endpoint, keys{p256dh, auth}}`), the 503 cleanup, the disable order
 * (DELETE /subscribe → DELETE mailbox → wipe keys), and the fetch → decrypt → parse pipeline
 * replaying the server's own fixture through the relay's response shape.
 */
class PushRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: PushRepository
    private lateinit var store: PushSubscriptionStore
    private val sessionStore: SessionStore = Mockito.mock(SessionStore::class.java)
    private val prefs: SharedPreferences = Mockito.mock(SharedPreferences::class.java)
    private val editor: SharedPreferences.Editor = Mockito.mock(SharedPreferences.Editor::class.java)

    private val session = Session(
        serverUrl = "https://fitpub.test",
        token = "jwt",
        username = "sam",
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FitPubApi::class.java)
        // A *real* store over mocked prefs: assertions read store.subscription.value
        // directly, which sidesteps Mockito matchers on Kotlin suspend functions (their
        // platform-typed results are null-checked by the Kotlin call site).
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(Mockito.anyString(), Mockito.any())).thenReturn(editor)
        `when`(editor.remove(Mockito.anyString())).thenReturn(editor)
        store = PushSubscriptionStore(prefs)
        repository = PushRepository(api, MailboxClient(), store, sessionStore)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ------------------------------------------------------------------
    // probe
    // ------------------------------------------------------------------

    @Test
    fun probe_reportsAvailableWhenTheInstanceServesTheVapidKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"publicKey":"BA"}"""))

        val result = repository.probe()

        assertEquals(ApiResult.Success(true), result)
        assertEquals("/api/web/push/vapid-key", server.takeRequest().path)
    }

    @Test
    fun probe_maps503ToPushDisabledSoThe8fPollStaysTheFallback() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"error":"Push notifications are not configured"}"""),
        )

        val result = repository.probe()

        // Not an error: "push is disabled here" is a definitive answer the UI shows as
        // "keep the background check" — only unreachable instances are errors.
        assertEquals(ApiResult.Success(false), result)
        assertEquals("/api/web/push/vapid-key", server.takeRequest().path)
    }

    // ------------------------------------------------------------------
    // enable
    // ------------------------------------------------------------------

    @Test
    fun enable_mintsMailboxThenSubscribesWithFreshKeysAndStoresThem() = runTest {
        `when`(sessionStore.currentSession()).thenReturn(session)
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"endpoint":"$base/push/new-id"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"subscribed"}"""))

        val result = repository.enable(base)

        assertTrue("expected success, got $result", result is ApiResult.Success)

        val mint = server.takeRequest()
        assertEquals("POST", mint.method)
        assertEquals("/mailbox", mint.path)

        val subscribe = server.takeRequest()
        assertEquals("POST", subscribe.method)
        assertEquals("/api/web/push/subscribe", subscribe.path)
        val body = subscribe.body.readUtf8()
        assertTrue(body.contains("\"endpoint\":\"$base/push/new-id\""))
        assertTrue(body.contains("\"p256dh\":\""))
        assertTrue(body.contains("\"auth\":\""))

        val saved = store.subscription.value
            ?: throw AssertionError("enable() must persist the subscription")
        assertEquals(base, saved.mailboxBase)
        assertEquals("$base/push/new-id", saved.endpoint)
        assertEquals(NotificationPolling.cursorOwner("https://fitpub.test", "sam"), saved.owner)
        // What was stored is exactly what was registered — and it decodes to a real keypair.
        assertEquals(65, WebPushCrypto.fromBase64Url(saved.publicKey).size)
        assertEquals(32, WebPushCrypto.fromBase64Url(saved.privateKey).size)
        assertEquals(16, WebPushCrypto.fromBase64Url(saved.authSecret).size)
    }

    @Test
    fun enable_refusesWhenInstancePushIsDisabledAndRemovesTheFreshMailbox() = runTest {
        `when`(sessionStore.currentSession()).thenReturn(session)
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"endpoint":"$base/push/new-id"}"""))
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"error":"Push notifications are not configured"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.enable(base)

        assertTrue(result is ApiResult.Error)
        assertEquals(503, (result as ApiResult.Error).statusCode)
        server.takeRequest() // mailbox mint
        server.takeRequest() // rejected subscribe
        val cleanup = server.takeRequest()
        assertEquals("DELETE", cleanup.method)
        assertEquals("/push/new-id", cleanup.path)
        assertNull("no subscription must be stored", store.subscription.value)
    }

    // ------------------------------------------------------------------
    // disable
    // ------------------------------------------------------------------

    @Test
    fun disable_unsubscribesDeletesTheMailboxThenWipesLocalKeys() = runTest {
        `when`(sessionStore.currentSession()).thenReturn(session)
        store.save(subscription(server.url("/push/abc").toString()))

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"unsubscribed"}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.disable()

        assertTrue("expected success, got $result", result is ApiResult.Success)

        val unsubscribe = server.takeRequest()
        assertEquals("DELETE", unsubscribe.method)
        assertEquals("/api/web/push/subscribe", unsubscribe.path)
        assertTrue(unsubscribe.body.readUtf8().contains("push/abc"))

        val mailbox = server.takeRequest()
        assertEquals("DELETE", mailbox.method)
        assertEquals("/push/abc", mailbox.path)

        assertNull("keys must be wiped", store.subscription.value)
    }

    @Test
    fun disable_wipesLocallyEvenWhenBothSystemsFail() = runTest {
        `when`(sessionStore.currentSession()).thenReturn(session)
        store.save(subscription(server.url("/push/abc").toString()))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(503))

        val result = repository.disable()

        assertTrue(result is ApiResult.Error)
        // The local wipe is the guarantee the user asked for — it happened regardless.
        assertNull("keys must be wiped", store.subscription.value)
    }

    // ------------------------------------------------------------------
    // fetch → decrypt → parse
    // ------------------------------------------------------------------

    @Test
    fun fetchPayloads_decryptsTheServerFixtureQueuedByTheRelay() = runTest {
        // The relay hands blobs back as standard base64 of the exact bytes (Go []byte JSON).
        val encoded = Base64.getEncoder().encodeToString(Base64.getUrlDecoder().decode(PushFixture.BLOB))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"messages":[{"ttl":86400,"received":1770000000,"payload":"$encoded"}]}"""),
        )

        val result = repository.fetchPayloads(subscription(server.url("/push/abc").toString()))

        val payloads = (result as ApiResult.Success).data
        assertEquals(1, payloads.size)
        assertEquals("FitPub", payloads[0].title)
        assertEquals("Alice boosted Morning Run", payloads[0].body)
        assertEquals("fitpub-activity_shared", payloads[0].tag)
        assertEquals("/activities/5f0e7b2c-9d31-4a6e-8c5f-2b7e4d1a9c03", payloads[0].url)
        assertEquals("/img/fitpub-logo-256.png", payloads[0].icon)
    }

    @Test
    fun fetchPayloads_surfacesAGoneMailboxSoTheWorkerCanTearDown() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(410).setBody("""{"error":"subscription expired or removed"}"""),
        )

        val result = repository.fetchPayloads(subscription(server.url("/push/gone").toString()))

        assertTrue(result is ApiResult.Error)
        assertEquals(410, (result as ApiResult.Error).statusCode)
    }

    private fun subscription(endpoint: String) = PushSubscription(
        owner = NotificationPolling.cursorOwner("https://fitpub.test", "sam"),
        mailboxBase = "https://push.example.test",
        endpoint = endpoint,
        publicKey = PushFixture.P256DH,
        privateKey = PushFixture.PRIVATE,
        authSecret = PushFixture.AUTH,
    )

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}

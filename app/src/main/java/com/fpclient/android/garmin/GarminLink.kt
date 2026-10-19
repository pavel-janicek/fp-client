package com.fpclient.android.garmin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.fpclient.android.BuildConfig
import com.fpclient.android.FitPubApplication
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The phone end of the Garmin link (Iteration 10b), built so delivery never depends on FP Client
 * already running:
 *
 * - **Wake-up:** on ready the SDK registers FP Client with Garmin Connect's binding service, so
 *   Garmin Connect binds the SDK's `IQGarminBindingService` — starting this process if needed —
 *   whenever the watch app sends something.
 * - **Lost first message:** that wake-up races the SDK's own (asynchronous) initialisation, and a
 *   message arriving before [onReady] is dropped by the SDK while the watch is still told it was
 *   delivered. So every time the SDK becomes ready we send `pull`, and the watch resends whatever
 *   it still holds; nothing leaves the watch's queue without our explicit `ack`.
 * - **Periodic pull:** [GarminPullWorker] repeats the pull every 15 minutes.
 * - **Samples (10d):** the watch pushes its sample chunks right after the summary; whatever is
 *   missing after a quiet [FETCH_DELAY_MS] (or at the next SDK start / periodic run) is requested
 *   one chunk at a time with `need`, the next as soon as the previous arrives. `ack` goes only
 *   once the summary and every chunk are stored; then [GarminUploadWorker] takes over.
 *
 * SDK setup runs on the main thread, where the SDK also delivers its callbacks. Sends go to one
 * background thread: in TETHERED mode `sendMessage` writes the adb socket synchronously, which the
 * main thread may not do (NetworkOnMainThreadException); one thread keeps them in order.
 */
object GarminLink {
    private const val TAG = "GarminLink"
    private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"
    /** Quiet time after a summary or chunk before asking for what is still missing. */
    private const val FETCH_DELAY_MS = 15_000L

    private enum class State { IDLE, STARTING, READY, UNAVAILABLE }

    private val watchApp = IQApp(GarminProtocol.APP_ID)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendExecutor = Executors.newSingleThreadExecutor { Thread(it, "garmin-send") }
    /** Chunk index last requested per workout id, so its arrival triggers the next request. */
    private val requested = ConcurrentHashMap<String, Int>()

    @Volatile private var state = State.IDLE
    private var connectIQ: ConnectIQ? = null
    private lateinit var appContext: Context

    /** True once the SDK is up; false while starting and when Garmin Connect is not installed. */
    val isReady: Boolean get() = state == State.READY

    /** Idempotent; safe from any thread. Does nothing on phones without Garmin Connect. */
    fun start(context: Context) {
        appContext = context.applicationContext
        mainHandler.post {
            if (state != State.IDLE) return@post
            if (!isAvailable(appContext)) {
                state = State.UNAVAILABLE
                return@post
            }
            state = State.STARTING
            // TETHERED talks to the Connect IQ simulator through `adb forward tcp:7381 tcp:7381`
            // (debug builds with -PgarminTethered=true, see docs/GARMIN.md); WIRELESS goes
            // through the Garmin Connect app to a real watch.
            val type = if (BuildConfig.GARMIN_TETHERED) ConnectIQ.IQConnectType.TETHERED else ConnectIQ.IQConnectType.WIRELESS
            val sdk = ConnectIQ.getInstance(appContext, type)
            connectIQ = sdk
            sdk.initialize(appContext, false, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() = onReady(sdk)

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                    Log.w(TAG, "Connect IQ unavailable: $status")
                    state = State.UNAVAILABLE
                }

                override fun onSdkShutDown() {
                    state = State.IDLE
                }
            })
        }
    }

    /** Asks every known watch to resend its queued workouts. No-op until the SDK is ready. */
    fun pullAll() {
        mainHandler.post {
            val sdk = connectIQ?.takeIf { state == State.READY } ?: return@post
            for (device in knownDevices(sdk)) {
                Log.i(TAG, "pull → ${device.friendlyName}")
                send(sdk, device, GarminProtocol.pull())
            }
        }
    }

    private fun onReady(sdk: ConnectIQ) {
        state = State.READY
        val viaBinder = runCatching {
            sdk.registerAppToUseBinderService(GarminProtocol.APP_ID)
            sdk.registerForAppEvents(::onMessages)
        }.onFailure { Log.w(TAG, "Binding service unavailable, falling back to broadcasts", it) }.isSuccess
        if (!viaBinder) {
            // Older Garmin Connect (or the simulator): messages only reach a running process.
            for (device in knownDevices(sdk)) {
                runCatching { sdk.registerForAppEvents(device, watchApp, ::onMessages) }
                    .onFailure { Log.w(TAG, "Cannot listen to ${device.friendlyName}", it) }
            }
        }
        pullAll()
        fetchAllMissing()
    }

    private fun onMessages(device: IQDevice, app: IQApp, messages: List<Any?>?, status: ConnectIQ.IQMessageStatus) {
        if (status != ConnectIQ.IQMessageStatus.SUCCESS || app.applicationId != GarminProtocol.APP_ID) {
            Log.w(TAG, "Message from ${device.friendlyName} not usable: $status")
            return
        }
        for (message in messages.orEmpty()) {
            GarminProtocol.parseWorkout(message)?.let { workout -> scope.launch { onWorkout(device, workout) } }
            GarminProtocol.parseChunk(message)?.let { chunk -> scope.launch { onChunk(device, chunk) } }
        }
    }

    private fun onWorkout(device: IQDevice, workout: GarminProtocol.Workout) {
        val inbox = FitPubApplication.container(appContext).garminInboxStore
        val isNew = inbox.recordSummary(
            GarminInboxEntry(
                id = workout.id,
                label = workout.label,
                createdEpochSeconds = workout.createdEpochSeconds,
                deviceName = device.friendlyName.orEmpty(),
                receivedAtEpochMs = System.currentTimeMillis(),
                sport = workout.sport,
                durationSeconds = workout.durationSeconds,
                distanceMeters = workout.distanceMeters,
                deviceId = device.deviceIdentifier,
                expectedChunks = workout.chunks,
            ),
        )
        val entry = inbox.get(workout.id) ?: return
        Log.i(TAG, "workout ${workout.id} ${if (isNew) "new" else "known"}, ${entry.receivedChunks.size}/${entry.expectedChunks} chunks")
        if (isNew) GarminNotifications.postReceived(appContext, entry)
        if (entry.isComplete || entry.uploadedActivityId != null) {
            // Also for a resent summary: the watch resends until an ack gets through.
            complete(device, entry)
        } else {
            // The watch pushes the chunks right behind the summary; ask only for what is still
            // missing once that burst had time to arrive.
            scheduleFetch(entry.id, FETCH_DELAY_MS)
        }
    }

    private fun onChunk(device: IQDevice, chunk: GarminProtocol.Chunk) {
        val inbox = FitPubApplication.container(appContext).garminInboxStore
        val entry = inbox.storeChunk(chunk.id, chunk.index, chunk.values) ?: return
        if (entry.isComplete) {
            complete(device, entry)
        } else if (requested[chunk.id] == chunk.index) {
            // Answer to our `need`: ask for the next gap straight away.
            fetchMissing(entry)
        } else {
            scheduleFetch(entry.id, FETCH_DELAY_MS)
        }
    }

    /** Everything is on the phone: release the watch and upload. */
    private fun complete(device: IQDevice, entry: GarminInboxEntry) {
        requested.remove(entry.id)
        connectIQ?.let { send(it, device, GarminProtocol.ack(entry.id)) }
        if (entry.isPendingUpload) GarminUploadWorker.enqueue(appContext)
    }

    /** Requests the first missing chunk of every incomplete workout. No-op until the SDK is ready. */
    fun fetchAllMissing() {
        if (state != State.READY) return
        scope.launch {
            FitPubApplication.container(appContext).garminInboxStore.all()
                .filter { !it.isComplete && it.uploadedActivityId == null }
                .forEach(::fetchMissing)
        }
    }

    private fun fetchMissing(entry: GarminInboxEntry) {
        val index = entry.missingChunks.firstOrNull() ?: return
        val deviceId = entry.deviceId ?: return
        val sdk = connectIQ ?: return
        requested[entry.id] = index
        send(sdk, IQDevice(deviceId, entry.deviceName), GarminProtocol.need(entry.id, index))
    }

    private fun scheduleFetch(id: String, delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(fetchToken(id))
        mainHandler.postAtTime(
            {
                FitPubApplication.container(appContext).garminInboxStore.get(id)
                    ?.takeIf { !it.isComplete && it.uploadedActivityId == null }
                    ?.let { scope.launch { fetchMissing(it) } }
            },
            fetchToken(id),
            android.os.SystemClock.uptimeMillis() + delayMs,
        )
    }

    private fun fetchToken(id: String): Any = "garmin-fetch-$id".intern()

    private fun send(sdk: ConnectIQ, device: IQDevice, message: Map<String, Any>) {
        sendExecutor.execute {
            runCatching {
                sdk.sendMessage(device, watchApp, message) { target, _, status ->
                    if (status != ConnectIQ.IQMessageStatus.SUCCESS) Log.w(TAG, "Send to ${target.friendlyName} failed: $status")
                }
            }.onFailure { Log.w(TAG, "Send to ${device.friendlyName} failed", it) }
        }
    }

    private fun knownDevices(sdk: ConnectIQ): List<IQDevice> =
        runCatching { sdk.knownDevices.orEmpty() }.onFailure { Log.w(TAG, "No device list", it) }.getOrDefault(emptyList())

    /** Whether a link is possible at all: Garmin Connect installed, or the simulator build. */
    fun isAvailable(context: Context): Boolean = BuildConfig.GARMIN_TETHERED || try {
        context.packageManager.getPackageInfo(GARMIN_CONNECT_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

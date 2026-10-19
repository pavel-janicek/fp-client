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
 *
 * SDK setup runs on the main thread, where the SDK also delivers its callbacks. Sends go to one
 * background thread: in TETHERED mode `sendMessage` writes the adb socket synchronously, which the
 * main thread may not do (NetworkOnMainThreadException); one thread keeps them in order.
 */
object GarminLink {
    private const val TAG = "GarminLink"
    private const val GARMIN_CONNECT_PACKAGE = "com.garmin.android.apps.connectmobile"

    private enum class State { IDLE, STARTING, READY, UNAVAILABLE }

    private val watchApp = IQApp(GarminProtocol.APP_ID)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendExecutor = Executors.newSingleThreadExecutor { Thread(it, "garmin-send") }

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
    }

    private fun onMessages(device: IQDevice, app: IQApp, messages: List<Any?>?, status: ConnectIQ.IQMessageStatus) {
        if (status != ConnectIQ.IQMessageStatus.SUCCESS || app.applicationId != GarminProtocol.APP_ID) {
            Log.w(TAG, "Message from ${device.friendlyName} not usable: $status")
            return
        }
        Log.i(TAG, "${messages?.size ?: 0} message(s) from ${device.friendlyName}")
        val inbox = FitPubApplication.container(appContext).garminInboxStore
        for (workout in messages.orEmpty().mapNotNull(GarminProtocol::parseWorkout)) {
            scope.launch {
                val entry = GarminInboxEntry(
                    id = workout.id,
                    label = workout.label,
                    createdEpochSeconds = workout.createdEpochSeconds,
                    deviceName = device.friendlyName.orEmpty(),
                    receivedAtEpochMs = System.currentTimeMillis(),
                    sport = workout.sport,
                    durationSeconds = workout.durationSeconds,
                    distanceMeters = workout.distanceMeters,
                )
                val isNew = inbox.record(entry)
                Log.i(TAG, "workout ${workout.id} ${if (isNew) "stored" else "already known"}, ack")
                if (isNew) GarminNotifications.postReceived(appContext, entry)
                // Ack duplicates too: the watch resends until an ack gets through.
                connectIQ?.let { send(it, device, GarminProtocol.ack(workout.id)) }
            }
        }
    }

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

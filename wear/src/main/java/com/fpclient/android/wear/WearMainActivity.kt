package com.fpclient.android.wear

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fpclient.android.wear.auth.CredentialRequestResult
import com.fpclient.android.wear.auth.WatchWearAuthRelay
import com.fpclient.android.wear.auth.WearAuthStore
import com.fpclient.android.wear.recording.WorkoutRecordingBus
import com.fpclient.android.wear.recording.WorkoutRecordingController
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutSyncScheduler
import com.fpclient.android.wear.ui.FitPubWearTheme
import com.fpclient.android.wear.ui.WearAppNavGraph
import kotlinx.coroutines.launch

/**
 * Launcher activity for FitPub Wear (Iteration 9a).
 *
 * Hosts the standalone watch UI and requests workout permissions in context before starting the
 * foreground recording service.
 */
class WearMainActivity : ComponentActivity() {

    private lateinit var authStore: WearAuthStore
    private lateinit var authRelay: WatchWearAuthRelay
    private var phoneReachable by mutableStateOf<Boolean?>(null)
    /** Transient handshake status shown on Home until the phone answers (9b diagnostics). */
    private var authStatus by mutableStateOf<String?>(null)
    private var workoutSnapshot by mutableStateOf(WorkoutRecordingSnapshot())
    private var workoutPermissionError by mutableStateOf<String?>(null)
    private var pendingWorkoutType = WorkoutActivityType.RUN

    private val workoutPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        startWorkoutIfAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authStore = WearAuthStore(this)
        authRelay = WatchWearAuthRelay(this, authStore)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val syncStore = com.fpclient.android.wear.recording.WatchWorkoutSyncStore(applicationContext)
            WorkoutRecordingBus.publishPendingCount(syncStore.all().size)
        }
        WorkoutSyncScheduler.schedulePeriodic(this)
        requestCredentials()
        lifecycleScope.launch {
            WorkoutRecordingBus.state.collect { workoutSnapshot = it }
        }
        setContent {
            FitPubWearTheme {
                val authState by authStore.state.collectAsState(initial = com.fpclient.android.wear.auth.WearAuthState())
                WearAppNavGraph(
                    authState = authState,
                    phoneReachable = phoneReachable,
                    authStatus = authStatus,
                    onRequestCredentials = ::requestCredentials,
                    onSignOut = ::signOut,
                    workoutSnapshot = workoutSnapshot,
                    workoutPermissionError = workoutPermissionError,
                    onStartWorkout = ::requestWorkoutStart,
                    onPauseWorkout = { WorkoutRecordingController.pause(this) },
                    onResumeWorkout = { WorkoutRecordingController.resume(this) },
                    onStopWorkout = { WorkoutRecordingController.stop(this) },
                    onAmbientModeChanged = { isAmbient ->
                        if (workoutSnapshot.status == com.fpclient.android.wear.recording.WorkoutStatus.RECORDING ||
                            workoutSnapshot.status == com.fpclient.android.wear.recording.WorkoutStatus.PAUSED
                        ) {
                            WorkoutRecordingController.setAmbientMode(this, isAmbient)
                        }
                    },
                )
            }
        }
    }

    private fun requestCredentials() {
        phoneReachable = null
        authStatus = "Looking for phone…"
        lifecycleScope.launch {
            when (val result = authRelay.requestCredentials()) {
                CredentialRequestResult.SentAwaitingReply -> {
                    phoneReachable = true
                    authStatus = "Request sent — waiting for phone reply…"
                }
                CredentialRequestResult.NoPhoneFound -> {
                    phoneReachable = false
                    authStatus = null // Home already shows "No paired phone is reachable"
                }
                is CredentialRequestResult.SendFailed -> {
                    phoneReachable = false
                    authStatus = "Send failed: ${result.reason}"
                }
            }
        }
    }

    private fun signOut() {
        authStatus = null
        lifecycleScope.launch { phoneReachable = authRelay.signOut() }
    }

    private fun requestWorkoutStart(activityType: WorkoutActivityType) {
        pendingWorkoutType = activityType
        workoutPermissionError = null
        val missing = WorkoutRecordingController.missingPermissions(this)
        if (missing.isEmpty()) startWorkoutIfAllowed() else workoutPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun startWorkoutIfAllowed() {
        if (!WorkoutRecordingController.canStart(this)) {
            workoutPermissionError = "Grant a health or location permission to start recording."
            return
        }
        WorkoutRecordingController.start(this, pendingWorkoutType)
        workoutPermissionError = null
    }
}

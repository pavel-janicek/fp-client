package com.fpclient.android.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.fpclient.android.wear.auth.WearAuthState
import com.fpclient.android.wear.recording.WorkoutActivityType
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.WorkoutStatus
import kotlinx.coroutines.delay

/** Route table for the watch nav graph. */
object WearRoutes {
    const val HOME = "home"
    const val WORKOUT_SELECT = "workout_select"
    const val WORKOUT = "workout"
    const val SETTINGS = "settings"
}

/** How long the ✓ SAVED confirmation stays visible before auto-returning to Home. */
const val STOPPED_AUTO_CLOSE_DELAY_MS = 1_500L

/**
 * Pure decision behind the workout auto-close: auto-return Home only when the composable actually
 * observed the RECORDING/PAUSED → STOPPED transition in this process lifetime. A STOPPED snapshot
 * restored from storage (process death) or shown on re-entry has a null [previousStatus], so it
 * can never satisfy the guard and the workout window cannot bounce-loop back to Home.
 */
fun shouldAutoReturnOnStop(previousStatus: WorkoutStatus?, isCurrentlyStopped: Boolean): Boolean =
    isCurrentlyStopped &&
        (previousStatus == WorkoutStatus.RECORDING || previousStatus == WorkoutStatus.PAUSED)

/**
 * Navigation graph for the watch app.
 *
 * [SwipeDismissableNavHost] is the Wear OS counterpart of NavHost: every destination gets the
 * platform back behaviour for free — swipe-from-left-edge below API 36, predictive back gestures
 * from API 36 onwards — so no screen needs its own back button chrome.
 */
@Composable
fun WearAppNavGraph(
    authState: WearAuthState = WearAuthState(),
    phoneReachable: Boolean? = null,
    authStatus: String? = null,
    onRequestCredentials: () -> Unit = {},
    onSignOut: () -> Unit = {},
    workoutSnapshot: WorkoutRecordingSnapshot = WorkoutRecordingSnapshot(),
    workoutPermissionError: String? = null,
    onStartWorkout: (WorkoutActivityType) -> Unit = {},
    onPauseWorkout: () -> Unit = {},
    onResumeWorkout: () -> Unit = {},
    onStopWorkout: () -> Unit = {},
    onSyncPending: () -> Unit = {},
    onDiscardPending: () -> Unit = {},
    onResolvePending: () -> Unit = {},
    onAmbientModeChanged: (Boolean) -> Unit = {},
    onRequestSensorPermissions: () -> Unit = {},
    permissionRefreshKey: Int = 0,
    navController: NavHostController = rememberSwipeDismissableNavController(),
) {
    var selectedActivityType by remember { mutableStateOf(WorkoutActivityType.RUN) }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.HOME,
    ) {
        composable(WearRoutes.HOME) {
            HomeScreen(
                authState = authState,
                phoneReachable = phoneReachable,
                authStatus = authStatus,
                pendingSyncCount = workoutSnapshot.pendingSyncCount,
                onRequestCredentials = onRequestCredentials,
                onSignOut = onSignOut,
                onOpenWorkout = {
                    if (workoutSnapshot.status == WorkoutStatus.RECORDING || workoutSnapshot.status == WorkoutStatus.PAUSED) {
                        navController.navigate(WearRoutes.WORKOUT)
                    } else {
                        navController.navigate(WearRoutes.WORKOUT_SELECT)
                    }
                },
                onOpenSettings = { navController.navigate(WearRoutes.SETTINGS) },
            )
        }
        composable(WearRoutes.SETTINGS) {
            SettingsScreen(
                onRequestSensorPermissions = onRequestSensorPermissions,
                onSyncPending = onSyncPending,
                onDiscardPending = onDiscardPending,
                onResolvePending = onResolvePending,
                permissionRefreshKey = permissionRefreshKey,
            )
        }
        composable(WearRoutes.WORKOUT_SELECT) {
            ActivitySelectionScreen(
                onSelectActivity = { type ->
                    selectedActivityType = type
                    onStartWorkout(type)
                    navController.navigate(WearRoutes.WORKOUT) {
                        popUpTo(WearRoutes.WORKOUT_SELECT) { inclusive = true }
                    }
                },
                onRequestSensorPermissions = onRequestSensorPermissions,
            )
        }
        composable(WearRoutes.WORKOUT) {
            // Auto-return Home shortly after a fresh stop so the ✓ + beep + buzz land first.
            // Tracks the previous status and fires only on the RECORDING/PAUSED → STOPPED
            // transition, so process death + re-entry on a persisted STOPPED snapshot cannot
            // bounce-loop back to Home.
            val finishedSessionId = workoutSnapshot.session
                ?.takeIf { it.status == WorkoutStatus.STOPPED }
                ?.startedAtEpochMs
            var previousStatus by remember(workoutSnapshot.session?.startedAtEpochMs) {
                mutableStateOf<WorkoutStatus?>(null)
            }
            LaunchedEffect(finishedSessionId) {
                val previous = previousStatus
                previousStatus = workoutSnapshot.status
                if (!shouldAutoReturnOnStop(previous, finishedSessionId != null)) return@LaunchedEffect
                delay(STOPPED_AUTO_CLOSE_DELAY_MS)
                if (navController.currentDestination?.route == WearRoutes.WORKOUT) {
                    navController.popBackStack(WearRoutes.HOME, inclusive = false)
                }
            }
            WorkoutControlScreen(
                activityType = selectedActivityType,
                snapshot = workoutSnapshot,
                permissionError = workoutPermissionError,
                onStart = onStartWorkout,
                onPause = onPauseWorkout,
                onResume = onResumeWorkout,
                onStop = {
                    onStopWorkout()
                    navController.popBackStack(WearRoutes.HOME, inclusive = false)
                },
                onSyncPending = onSyncPending,
                onDiscardPending = onDiscardPending,
                onAmbientModeChanged = onAmbientModeChanged,
            )
        }
    }
}

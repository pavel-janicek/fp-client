package com.fpclient.android.wear.ui

import androidx.compose.runtime.Composable
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

/** Route table for the watch nav graph. */
object WearRoutes {
    const val HOME = "home"
    const val WORKOUT_SELECT = "workout_select"
    const val WORKOUT = "workout"
    const val SETTINGS = "settings"
}

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
                permissionRefreshKey = permissionRefreshKey,
            )
        }
        composable(WearRoutes.WORKOUT_SELECT) {
            ActivitySelectionScreen(
                onSelectActivity = { type ->
                    selectedActivityType = type
                    navController.navigate(WearRoutes.WORKOUT)
                },
                onRequestSensorPermissions = onRequestSensorPermissions,
            )
        }
        composable(WearRoutes.WORKOUT) {
            WorkoutControlScreen(
                activityType = selectedActivityType,
                snapshot = workoutSnapshot,
                permissionError = workoutPermissionError,
                onStart = onStartWorkout,
                onPause = onPauseWorkout,
                onResume = onResumeWorkout,
                onStop = onStopWorkout,
                onSyncPending = onSyncPending,
                onAmbientModeChanged = onAmbientModeChanged,
            )
        }
    }
}

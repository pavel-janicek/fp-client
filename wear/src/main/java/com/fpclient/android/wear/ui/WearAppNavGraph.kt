package com.fpclient.android.wear.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.fpclient.android.wear.auth.WearAuthState
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.WorkoutActivityType

/** Route table for the watch nav graph. */
object WearRoutes {
    const val HOME = "home"
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
    onOpenWorkout: () -> Unit = {},
    onStartWorkout: (WorkoutActivityType) -> Unit = {},
    onPauseWorkout: () -> Unit = {},
    onResumeWorkout: () -> Unit = {},
    onStopWorkout: () -> Unit = {},
    onAmbientModeChanged: (Boolean) -> Unit = {},
    onRequestSensorPermissions: () -> Unit = {},
    permissionRefreshKey: Int = 0,
    navController: NavHostController = rememberSwipeDismissableNavController(),
) {
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
                onOpenWorkout = { navController.navigate(WearRoutes.WORKOUT) },
                onOpenSettings = { navController.navigate(WearRoutes.SETTINGS) },
            )
        }
        composable(WearRoutes.SETTINGS) {
            SettingsScreen(
                onRequestSensorPermissions = onRequestSensorPermissions,
                permissionRefreshKey = permissionRefreshKey,
            )
        }
        composable(WearRoutes.WORKOUT) {
            WorkoutControlScreen(
                snapshot = workoutSnapshot,
                permissionError = workoutPermissionError,
                onStart = onStartWorkout,
                onPause = onPauseWorkout,
                onResume = onResumeWorkout,
                onStop = onStopWorkout,
                onAmbientModeChanged = onAmbientModeChanged,
            )
        }
    }
}

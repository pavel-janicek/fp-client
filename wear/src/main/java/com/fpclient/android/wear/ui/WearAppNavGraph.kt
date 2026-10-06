package com.fpclient.android.wear.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.fpclient.android.wear.BuildConfig
import com.fpclient.android.wear.auth.WearAuthState
import com.fpclient.android.wear.recording.WorkoutRecordingSnapshot
import com.fpclient.android.wear.recording.WorkoutActivityType

/** Route table for the watch nav graph — one entry per Iteration 9 screen as they land. */
object WearRoutes {
    const val HOME = "home"
    const val ABOUT = "about"
    const val WORKOUT = "workout"
}

/**
 * Navigation graph for the watch app (Iteration 9a).
 *
 * [SwipeDismissableNavHost] is the Wear OS counterpart of NavHost: every destination gets the
 * platform back behaviour for free — swipe-from-left-edge below API 36, predictive back gestures
 * from API 36 onwards — so no screen needs its own back button chrome. 9b/9c/9d add their
 * destinations to this graph rather than growing the launcher activity.
 */
@Composable
fun WearAppNavGraph(
    authState: WearAuthState = WearAuthState(),
    phoneReachable: Boolean? = null,
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
    navController: NavHostController = rememberSwipeDismissableNavController(),
) {
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = WearRoutes.HOME,
    ) {
        composable(WearRoutes.HOME) {
            HomeScreen(
                versionName = BuildConfig.VERSION_NAME,
                onOpenAbout = { navController.navigate(WearRoutes.ABOUT) },
                authState = authState,
                phoneReachable = phoneReachable,
                pendingSyncCount = workoutSnapshot.pendingSyncCount,
                onRequestCredentials = onRequestCredentials,
                onSignOut = onSignOut,
                onOpenWorkout = { navController.navigate(WearRoutes.WORKOUT) },
            )
        }
        composable(WearRoutes.ABOUT) {
            AboutScreen()
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

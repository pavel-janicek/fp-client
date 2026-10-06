package com.fpclient.android.wear.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.fpclient.android.wear.BuildConfig
import com.fpclient.android.wear.auth.WearAuthState

/** Route table for the watch nav graph — one entry per Iteration 9 screen as they land. */
object WearRoutes {
    const val HOME = "home"
    const val ABOUT = "about"
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
                onRequestCredentials = onRequestCredentials,
                onSignOut = onSignOut,
            )
        }
        composable(WearRoutes.ABOUT) {
            AboutScreen()
        }
    }
}

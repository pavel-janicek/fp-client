package com.fpclient.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fpclient.android.notifications.PushNotifications
import com.fpclient.android.ui.AppViewModel
import com.fpclient.android.ui.auth.LoginContent
import com.fpclient.android.ui.auth.LoginViewModel
import com.fpclient.android.ui.auth.PasswordResetContent
import com.fpclient.android.ui.auth.PasswordResetViewModel
import com.fpclient.android.ui.auth.RegisterContent
import com.fpclient.android.ui.auth.RegisterViewModel
import com.fpclient.android.ui.auth.ServerSetupContent
import com.fpclient.android.ui.auth.ServerSetupViewModel
import com.fpclient.android.ui.auth.VerifyCodeContent
import com.fpclient.android.ui.navigation.Routes
import com.fpclient.android.ui.navigation.SharedUriArg
import com.fpclient.android.ui.theme.FPClientTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels { AppViewModel.factory(FitPubApplication.container(this)) }

    /**
     * Bottom tab asked for by a tapped background notification (Iteration 8f), or null for a
     * normal launch. Cleared by the main screen once it has honoured the request.
     */
    private val requestedTab = mutableStateOf<String?>(null)

    /**
     * Server-relative path asked for by a tapped mailbox push notification (Iteration 8h),
     * e.g. `/activities/<id>`, or null for a normal launch. Consumed once the nav graph is up.
     */
    private val requestedPath = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FitPubApplication.container(this)
        // Only on a fresh launch — on recreation the same intent would re-trigger the share flow.
        val sharedFileUri = if (savedInstanceState == null) extractSharedFileUri(intent) else null
        requestedTab.value = intent.getStringExtra(PushNotifications.EXTRA_OPEN_TAB)
        // Path only on a fresh launch: on recreation the same intent would re-navigate and
        // stack a duplicate detail screen (same reasoning as the shared-file guard above).
        requestedPath.value =
            if (savedInstanceState == null) intent.getStringExtra(PushNotifications.EXTRA_OPEN_PATH) else null
        // Workouts whose share attempt failed (or that were recorded without a reachable
        // server, Iteration 8d) are retried silently on the next launch, once per process
        // and only for a signed-in session — the Record screen keeps the queue visible and
        // retryable if the server is still unreachable.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val session = container.sessionStore.session.first()
                if (session.isLoggedIn) {
                    // Only workouts the user actually asked to share. Retrying everything
                    // here silently published recordings they had merely stopped, and made
                    // "Discard workout" too late — the activity and its personal record were
                    // already on the server.
                    runCatching {
                        container.recordingShareManager.retryPending(onlyUserRequested = true)
                    }
                }
            }
        }
        setContent {
            FPClientTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by appViewModel.uiState.collectAsState()
                    when {
                        !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        !state.configured -> ServerSetupRoute(container)
                        !state.loggedIn && !state.guest -> AuthFlowRoute(container, appViewModel)
                        else -> MainAppRoute(
                            container = container,
                            appViewModel = appViewModel,
                            sharedFileUri = sharedFileUri,
                            requestedTab = requestedTab.value,
                            onRequestedTabHandled = { requestedTab.value = null },
                            requestedPath = requestedPath.value,
                            onRequestedPathHandled = { requestedPath.value = null },
                        )
                    }
                }
            }
        }
    }

    /**
     * Tapping the background poll's summary notification brings FP Client to the front
     * (Iteration 8f). The PendingIntent uses NEW_TASK|CLEAR_TOP, so the normal path is a fresh
     * `onCreate` carrying the extra; [onNewIntent] covers deliveries that land on a live
     * instance instead, so the tap works either way.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab.value = intent.getStringExtra(PushNotifications.EXTRA_OPEN_TAB)
        requestedPath.value = intent.getStringExtra(PushNotifications.EXTRA_OPEN_PATH)
    }

    /** File shared into the app (share sheet: ACTION_SEND + EXTRA_STREAM) or opened via
     * "Open with" (ACTION_VIEW with a content URI); null for a normal launch. */
    private fun extractSharedFileUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}

/** Matches the server-relative activity paths mailbox push payloads carry (`/activities/<id>`). */
private val PUSH_ACTIVITY_PATH = Regex("^/activities/([^/?#]+)")

@Composable
private fun ServerSetupRoute(
    container: AppContainer,
    initialUrl: String? = null,
    allowSkip: Boolean = true,
    onDone: () -> Unit = {},
    onCancel: (() -> Unit)? = null,
) {
    val vm: ServerSetupViewModel = viewModel(factory = ServerSetupViewModel.factory(container))
    val done by vm.done.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    LaunchedEffect(done) { if (done) onDone() }
    ServerSetupContent(
        busy = busy,
        hint = error,
        initialUrl = initialUrl,
        onSave = vm::connect,
        onSkip = if (allowSkip) ({ vm.skip() }) else null,
        onCancel = onCancel,
    )
}
@Composable
private fun AuthFlowRoute(container: AppContainer, appViewModel: AppViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.factory(container))
            val busy by vm.busy.collectAsState()
            val error by vm.error.collectAsState()
            val success by vm.success.collectAsState()
            val st by appViewModel.uiState.collectAsState()
            if (success == true) return@composable
            LoginContent(
                busy = busy,
                error = error,
                serverUrl = st.serverUrl,
                onLogin = vm::login,
                onOpenRegister = { navController.navigate(Routes.REGISTER) },
                onOpenPasswordReset = { navController.navigate(Routes.PASSWORD_RESET) },
                onChangeServer = { navController.navigate(Routes.SERVER_SETUP) },
                onBrowseAsGuest = vm::browseAsGuest,
            )
        }
        composable(Routes.SERVER_SETUP) {
            val st by appViewModel.uiState.collectAsState()
            ServerSetupRoute(
                container = container,
                initialUrl = st.serverUrl.takeIf { it.isNotBlank() },
                allowSkip = false,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.REGISTER) {
            val st by appViewModel.uiState.collectAsState()
            RegisterRoute(
                container = container,
                serverUrl = st.serverUrl,
                onBack = { navController.popBackStack() },
                onChangeServer = { navController.navigate(Routes.SERVER_SETUP) },
            )
        }
        composable(Routes.PASSWORD_RESET) {
            val vm: PasswordResetViewModel = viewModel(factory = PasswordResetViewModel.factory(container))
            val busy by vm.busy.collectAsState()
            val error by vm.error.collectAsState()
            val requested by vm.requested.collectAsState()
            PasswordResetContent(
                busy = busy,
                error = error,
                requested = requested,
                onRequest = vm::request,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun RegisterRoute(
    container: AppContainer,
    serverUrl: String,
    onBack: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val vm: RegisterViewModel = viewModel(factory = RegisterViewModel.factory(container))
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val status by vm.registrationStatus.collectAsState()
    val awaitingCode by vm.awaitingCode.collectAsState()
    val verified by vm.verified.collectAsState()
    var pendingEmail by remember { mutableStateOf("") }

    // The registration rules (enabled / password required) belong to the chosen instance,
    // so reload them whenever the instance changes — e.g. after "Change instance".
    LaunchedEffect(serverUrl) { vm.loadStatus() }

    when {
        verified == true -> return
        awaitingCode -> VerifyCodeContent(
            email = pendingEmail,
            busy = busy,
            error = error,
            onVerify = { email, code -> vm.verify(email, code) },
            onResend = { email -> vm.resend(email) },
        )
        else -> RegisterContent(
            busy = busy,
            error = error,
            status = status,
            serverUrl = serverUrl,
            onStart = { username, email, password, displayName, timezone ->
                pendingEmail = email
                vm.start(username, email, password, displayName, null, timezone, null)
            },
            onBack = onBack,
            onChangeServer = onChangeServer,
        )
    }
}

@Composable
private fun MainAppRoute(
    container: AppContainer,
    appViewModel: AppViewModel,
    sharedFileUri: Uri? = null,
    requestedTab: String? = null,
    onRequestedTabHandled: () -> Unit = {},
    requestedPath: String? = null,
    onRequestedPathHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    FitPubNavGraph(
        navController = navController,
        container = container,
        appViewModel = appViewModel,
        sharedFileUri = sharedFileUri,
        requestedTab = requestedTab,
        onRequestedTabHandled = onRequestedTabHandled,
        requestedPath = requestedPath,
        onRequestedPathHandled = onRequestedPathHandled,
    )
}
@Composable
private fun FitPubNavGraph(
    navController: NavHostController,
    container: AppContainer,
    appViewModel: AppViewModel,
    sharedFileUri: Uri? = null,
    requestedTab: String? = null,
    onRequestedTabHandled: () -> Unit = {},
    requestedPath: String? = null,
    onRequestedPathHandled: () -> Unit = {},
) {
    // A file arrived through the share sheet / "Open with": open the upload form with
    // that file pre-selected once, right after the main screen is up.
    if (sharedFileUri != null) {
        LaunchedEffect(sharedFileUri) {
            navController.navigate(Routes.createWithSharedUri(sharedFileUri.toString()))
        }
    }
    // A mailbox push notification (Iteration 8h) carried a server-relative path such as
    // /activities/<id>: open it on top of the main screen once the graph is up, so the tap
    // lands on the activity behind the notification instead of the bare notifications tab
    // (which the companion EXTRA_OPEN_TAB request still selects underneath).
    LaunchedEffect(requestedPath) {
        val path = requestedPath ?: return@LaunchedEffect
        val activityId = PUSH_ACTIVITY_PATH.matchEntire(path)?.groupValues?.get(1)
        if (!activityId.isNullOrBlank()) {
            navController.navigate(Routes.activityDetail(activityId))
        }
        onRequestedPathHandled()
    }
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            com.fpclient.android.ui.main.MainScaffold(
                container = container,
                appViewModel = appViewModel,
                onOpenActivity = { id -> navController.navigate(Routes.activityDetail(id)) },
                onOpenProfile = { username -> navController.navigate(Routes.profile(username)) },
                onOpenCreate = { navController.navigate(Routes.create()) },
                onOpenRecord = { navController.navigate(Routes.RECORD) },
                onOpenEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },
                onOpenFollowers = { username -> navController.navigate(Routes.followList(username, "followers")) },
                onOpenFollowing = { username -> navController.navigate(Routes.followList(username, "following")) },
                onOpenRecords = { navController.navigate(Routes.RECORDS) },
                onOpenPeaks = { u -> navController.navigate(Routes.peaks(u)) },
                onOpenPeak = { u, peakId -> navController.navigate(Routes.peakDetail(u, peakId)) },
                onOpenWearInbox = { navController.navigate(Routes.WEAR_WORKOUT_INBOX) },
                requestedTab = requestedTab,
                onRequestedTabHandled = onRequestedTabHandled,
            )
        }
        composable(
            route = Routes.ACTIVITY_DETAIL,
            arguments = listOf(navArgument("activityId") { }),
        ) { entry ->
            val activityId = entry.arguments?.getString("activityId").orEmpty()
            com.fpclient.android.ui.activity.ActivityDetailScreen(
                activityId = activityId,
                container = container,
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onOpenProfile = { username -> navController.navigate(Routes.profile(username)) },
                onOpenTrim = { navController.navigate(Routes.activityTrim(activityId)) },
            )
        }
        // Trim workspace: pick the range of the original GPS track to keep. Its own route so
        // Back from it returns to the activity detail with that screen's state intact.
        composable(
            route = Routes.ACTIVITY_TRIM,
            arguments = listOf(navArgument("activityId") { }),
        ) { entry ->
            val trimActivityId = entry.arguments?.getString("activityId").orEmpty()
            com.fpclient.android.ui.activity.ActivityTrimScreen(
                activityId = trimActivityId,
                container = container,
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        // Personal records, reached from the Analytics "Personal records" tile.
        composable(Routes.RECORDS) {
            val unitSystem by appViewModel.unitSystem.collectAsState()
            com.fpclient.android.ui.analytics.RecordsScreen(
                container = container,
                unitSystem = unitSystem,
                onBack = { navController.popBackStack() },
                onOpenActivity = { id -> navController.navigate(Routes.activityDetail(id)) },
            )
        }
        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("username") { }),
        ) { entry ->
            val username = entry.arguments?.getString("username").orEmpty().ifBlank { Routes.ME }
            com.fpclient.android.ui.profile.ProfileScreen(
                username = username,
                container = container,
                appViewModel = appViewModel,
                embedded = false,
                onBack = { navController.popBackStack() },
                onOpenActivity = { id -> navController.navigate(Routes.activityDetail(id)) },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenFollowers = { u -> navController.navigate(Routes.followList(u, "followers")) },
                onOpenFollowing = { u -> navController.navigate(Routes.followList(u, "following")) },
                onOpenPeaks = { u -> navController.navigate(Routes.peaks(u)) },
                onOpenPeak = { u, peakId -> navController.navigate(Routes.peakDetail(u, peakId)) },
            )
        }
        composable(Routes.CREATE) { entry ->
            // Only a scheme-bearing URI (content://…) pre-selects a file: this rejects the
            // literal "{sharedUri}" placeholder a navigation to the raw route pattern passes in.
            val sharedUri = entry.arguments?.getString("sharedUri")
                ?.takeIf { SharedUriArg.isFileUri(it) }
                ?.let { Uri.parse(it) }
            com.fpclient.android.ui.create.CreateActivityScreen(
                container = container,
                appViewModel = appViewModel,
                sharedUri = sharedUri,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.EDIT_PROFILE) {
            com.fpclient.android.ui.profile.EditProfileScreen(
                container = container,
                appViewModel = appViewModel,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            com.fpclient.android.ui.settings.SettingsScreen(
                container = container,
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onOpenPrivacyZones = { navController.navigate(Routes.PRIVACY_ZONES) },
                onChangeInstance = { navController.navigate(Routes.SERVER_SETUP) },
                onOpenBatchImport = { navController.navigate(Routes.BATCH_IMPORT) },
                onOpenKomootImport = { navController.navigate(Routes.KOMOOT_IMPORT) },
                onOpenDataExport = { navController.navigate(Routes.DATA_EXPORT) },
                onOpenRecord = { navController.navigate(Routes.RECORD) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenEmailChange = { navController.navigate(Routes.EMAIL_CHANGE) },
                onOpenWearWorkoutInbox = { navController.navigate(Routes.WEAR_WORKOUT_INBOX) },
            )
        }
        composable(Routes.WEAR_WORKOUT_INBOX) {
            com.fpclient.android.ui.settings.WearWorkoutInboxScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ABOUT) {
            com.fpclient.android.ui.settings.AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DATA_EXPORT) {
            com.fpclient.android.ui.settings.DataExportScreen(
                container = container,
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BATCH_IMPORT) {
            com.fpclient.android.ui.settings.BatchImportScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PEAKS,
            arguments = listOf(navArgument("username") { }),
        ) { entry ->
            val username = entry.arguments?.getString("username").orEmpty()
            com.fpclient.android.ui.profile.PeaksScreen(
                container = container,
                username = username,
                onBack = { navController.popBackStack() },
                onOpenPeak = { peakId ->
                    navController.navigate(Routes.peakDetail(username, peakId))
                },
            )
        }
        composable(
            route = Routes.PEAK_DETAIL,
            arguments = listOf(navArgument("username") { }, navArgument("peakId") { }),
        ) { entry ->
            val username = entry.arguments?.getString("username").orEmpty()
            val peakId = entry.arguments?.getString("peakId")?.toLongOrNull() ?: 0L
            com.fpclient.android.ui.profile.PeakDetailScreen(
                container = container,
                username = username,
                peakId = peakId,
                onBack = { navController.popBackStack() },
                onOpenActivity = { id -> navController.navigate(Routes.activityDetail(id)) },
            )
        }
        composable(Routes.EMAIL_CHANGE) {
            com.fpclient.android.ui.settings.EmailChangeScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.FEEDBACK) {
            com.fpclient.android.ui.settings.FeedbackScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.KOMOOT_IMPORT) {
            val unitSystem by appViewModel.unitSystem.collectAsState()
            com.fpclient.android.ui.settings.KomootImportScreen(
                container = container,
                unitSystem = unitSystem,
                onBack = { navController.popBackStack() },
                // Bumping the shared counter makes the timeline/profile re-fetch so the
                // freshly imported activities actually appear.
                onActivitiesAdded = { container.activitiesVersion.value++ },
            )
        }
        composable(Routes.RECORD) {
            com.fpclient.android.ui.record.RecordScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenSummary = { sessionId ->
                    navController.navigate(Routes.workoutSummary(sessionId))
                },
            )
        }
        composable(
            route = Routes.WORKOUT_SUMMARY,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
            com.fpclient.android.ui.record.WorkoutSummaryRoute(
                sessionId = sessionId,
                container = container,
                onOpenActivity = { id ->
                    // Leave the summary behind: once the activity is on the server the
                    // review is done, and Back from the detail should land on Record.
                    navController.popBackStack()
                    navController.navigate(Routes.activityDetail(id))
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.SERVER_SETUP) {
            val st by appViewModel.uiState.collectAsState()
            ServerSetupRoute(
                container = container,
                initialUrl = st.serverUrl.takeIf { it.isNotBlank() },
                allowSkip = false,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.PRIVACY_ZONES) {
            com.fpclient.android.ui.settings.PrivacyZonesScreen(
                container = container,
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onCreateZone = { navController.navigate(Routes.privacyZoneEdit()) },
                onEditZone = { zoneId -> navController.navigate(Routes.privacyZoneEdit(zoneId)) },
            )
        }
        composable(
            route = Routes.PRIVACY_ZONE_EDIT,
            arguments = listOf(navArgument("zoneId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            com.fpclient.android.ui.settings.PrivacyZoneEditScreen(
                container = container,
                appViewModel = appViewModel,
                zoneId = entry.arguments?.getString("zoneId"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.FOLLOW_LIST,
            arguments = listOf(navArgument("username") { }, navArgument("type") { }),
        ) { entry ->
            val username = entry.arguments?.getString("username").orEmpty()
            val type = entry.arguments?.getString("type").orEmpty()
            com.fpclient.android.ui.profile.FollowListScreen(
                container = container,
                username = username,
                type = type,
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Routes.profile(it)) },
            )
        }
    }
}

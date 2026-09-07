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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.fpclient.android.ui.theme.FPClientTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels { AppViewModel.factory(FitPubApplication.container(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FitPubApplication.container(this)
        // Only on a fresh launch — on recreation the same intent would re-trigger the share flow.
        val sharedFileUri = if (savedInstanceState == null) extractSharedFileUri(intent) else null
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
                        else -> MainAppRoute(container, appViewModel, sharedFileUri)
                    }
                }
            }
        }
    }

    /** File shared into the app (share sheet: ACTION_SEND + EXTRA_STREAM) or opened via
     * "Open with" (ACTION_VIEW with a content URI); null for a normal launch. */
    private fun extractSharedFileUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}

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
            RegisterRoute(container = container, onBack = { navController.popBackStack() })
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
private fun RegisterRoute(container: AppContainer, onBack: () -> Unit) {
    val vm: RegisterViewModel = viewModel(factory = RegisterViewModel.factory(container))
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val status by vm.registrationStatus.collectAsState()
    val awaitingCode by vm.awaitingCode.collectAsState()
    val verified by vm.verified.collectAsState()
    var pendingEmail by remember { mutableStateOf("") }

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
            onStart = { username, email, password, displayName, timezone ->
                pendingEmail = email
                vm.start(username, email, password, displayName, null, timezone, null)
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun MainAppRoute(
    container: AppContainer,
    appViewModel: AppViewModel,
    sharedFileUri: Uri? = null,
) {
    val navController = rememberNavController()
    FitPubNavGraph(navController = navController, container = container, appViewModel = appViewModel, sharedFileUri = sharedFileUri)
}
@Composable
private fun FitPubNavGraph(
    navController: NavHostController,
    container: AppContainer,
    appViewModel: AppViewModel,
    sharedFileUri: Uri? = null,
) {
    // A file arrived through the share sheet / "Open with": open the upload form with
    // that file pre-selected once, right after the main screen is up.
    if (sharedFileUri != null) {
        LaunchedEffect(sharedFileUri) {
            navController.navigate(Routes.createWithSharedUri(sharedFileUri.toString()))
        }
    }
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            com.fpclient.android.ui.main.MainScaffold(
                container = container,
                appViewModel = appViewModel,
                onOpenActivity = { id -> navController.navigate(Routes.activityDetail(id)) },
                onOpenProfile = { username -> navController.navigate(Routes.profile(username)) },
                onOpenCreate = { navController.navigate(Routes.CREATE) },
                onOpenEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenFollowers = { username -> navController.navigate(Routes.followList(username, "followers")) },
                onOpenFollowing = { username -> navController.navigate(Routes.followList(username, "following")) },
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
            )
        }
        composable(Routes.CREATE) { entry ->
            val sharedUri = entry.arguments?.getString("sharedUri")?.let { Uri.parse(it) }
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
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) {
            com.fpclient.android.ui.settings.AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BATCH_IMPORT) {
            com.fpclient.android.ui.settings.BatchImportScreen(
                container = container,
                onBack = { navController.popBackStack() },
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

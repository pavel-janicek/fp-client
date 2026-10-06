package com.fpclient.android.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fpclient.android.wear.auth.WatchWearAuthRelay
import com.fpclient.android.wear.auth.WearAuthStore
import com.fpclient.android.wear.ui.FitPubWearTheme
import com.fpclient.android.wear.ui.WearAppNavGraph
import kotlinx.coroutines.launch

/**
 * Launcher activity for FitPub Wear (Iteration 9a).
 *
 * Deliberately minimal: its job is to prove that the `:wear` module installs and launches on a
 * watch (or the Wear OS emulator) straight from Android Studio. The screens it shows are the
 * 9a navigation/scaffolding skeleton; the sign-in handshake state arrives with 9b and the
 * recording UX with 9d.
 */
class WearMainActivity : ComponentActivity() {

    private lateinit var authStore: WearAuthStore
    private lateinit var authRelay: WatchWearAuthRelay
    private var phoneReachable by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authStore = WearAuthStore(this)
        authRelay = WatchWearAuthRelay(this, authStore)
        requestCredentials()
        setContent {
            FitPubWearTheme {
                val authState by authStore.state.collectAsState(initial = com.fpclient.android.wear.auth.WearAuthState())
                WearAppNavGraph(
                    authState = authState,
                    phoneReachable = phoneReachable,
                    onRequestCredentials = ::requestCredentials,
                    onSignOut = ::signOut,
                )
            }
        }
    }

    private fun requestCredentials() {
        phoneReachable = null
        lifecycleScope.launch { phoneReachable = authRelay.requestCredentials() }
    }

    private fun signOut() {
        lifecycleScope.launch { phoneReachable = authRelay.signOut() }
    }
}

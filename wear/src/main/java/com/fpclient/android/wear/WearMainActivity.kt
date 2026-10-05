package com.fpclient.android.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fpclient.android.wear.ui.FitPubWearTheme
import com.fpclient.android.wear.ui.WearAppNavGraph

/**
 * Launcher activity for FitPub Wear (Iteration 9a).
 *
 * Deliberately minimal: its job is to prove that the `:wear` module installs and launches on a
 * watch (or the Wear OS emulator) straight from Android Studio. The screens it shows are the
 * 9a navigation/scaffolding skeleton; the sign-in handshake state arrives with 9b and the
 * recording UX with 9d.
 */
class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitPubWearTheme {
                WearAppNavGraph()
            }
        }
    }
}

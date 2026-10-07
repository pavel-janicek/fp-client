package com.fpclient.android.wear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.auth.WearAuthState

/**
 * Landing screen of FitPub Wear: the "FP Wear" title, a compact sign-in/status line (tap to sign
 * in or refresh from the paired phone), and the two primary destinations — Workout and Settings.
 * The app version moved to [SettingsScreen], so it no longer rides the bottom rim here.
 *
 * Round safety: everything scales from the screen diameter ([BoxWithConstraints], 200 dp reference,
 * clamped 0.85–1.3×) and text is horizontally inset off the curved edges.
 */
@Composable
fun HomeScreen(
    authState: WearAuthState = WearAuthState(),
    phoneReachable: Boolean? = null,
    authStatus: String? = null,
    pendingSyncCount: Int = 0,
    onRequestCredentials: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenWorkout: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val scale = (minOf(maxWidth, maxHeight) / 200.dp).coerceIn(0.85f, 1.3f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp * scale),
            ) {
                Text(
                    text = "FP Wear",
                    fontSize = 32.sp * scale,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp * scale))
                // Compact sign-in / status line — tapped to sign in (or refresh) from the phone.
                val identityText = when {
                    authState.isSignedIn -> "Signed in as @${authState.username.ifBlank { "user" }} · tap to refresh"
                    authState.expired -> "Sign-in expired · tap to sign in again"
                    authStatus != null -> authStatus
                    phoneReachable == false -> "No paired phone is reachable"
                    phoneReachable == null -> "Looking for phone…"
                    else -> "Not signed in · tap to sign in"
                }
                Text(
                    text = identityText,
                    fontSize = 15.sp * scale,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRequestCredentials),
                )
                if (authState.isSignedIn || authState.expired) {
                    Spacer(modifier = Modifier.height(4.dp * scale))
                    Text(
                        text = "Sign out",
                        fontSize = 12.sp * scale,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSignOut),
                    )
                }
                if (authState.isSignedIn && phoneReachable == false) {
                    Spacer(modifier = Modifier.height(4.dp * scale))
                    Text(
                        text = "Using saved sign-in; phone is not reachable",
                        fontSize = 11.sp * scale,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp * scale))
                Button(
                    onClick = onOpenWorkout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pendingSyncCount > 0) "Workout ($pendingSyncCount pending)" else "Workout")
                }
                Spacer(modifier = Modifier.height(8.dp * scale))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Settings")
                }
            }
        }
    }
}

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun HomeScreenPreview() {
    FitPubWearTheme {
        HomeScreen()
    }
}

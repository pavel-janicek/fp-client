package com.fpclient.android.wear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import com.fpclient.android.wear.auth.WearAuthState

/**
 * Landing screen of FitPub Wear.
 *
 * A [ScalingLazyColumn] — the round-safe Wear list used by the other screens — hosts everything,
 * so the screen auto-centres when the content fits and scrolls (crown/touch) when it overflows:
 * on a small round display the Workout and Settings buttons would otherwise fall below the fold.
 * The title is deliberately compact and the app version lives on [SettingsScreen], not here.
 * The position indicator and the round-screen insets ([WearInsets]) are Play's Wear quality
 * requirements: a visible scrollbar while scrolling, and nothing cut off by the circle.
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
    val listState = rememberScalingLazyListState()
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listContentPadding(),
        ) {
            item {
                Text(
                    text = "FP Wear",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
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
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = textExtraInset())
                        .clickable(onClick = onRequestCredentials),
                )
            }
            if (authState.isSignedIn || authState.expired) {
                item {
                    Text(
                        text = "Sign out",
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textExtraInset())
                            .clickable(onClick = onSignOut),
                    )
                }
            }
            if (authState.isSignedIn && phoneReachable == false) {
                item {
                    Text(
                        text = "Using saved sign-in; phone is not reachable",
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textExtraInset()),
                    )
                }
            }
            item {
                Button(
                    onClick = onOpenWorkout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pendingSyncCount > 0) "Workout ($pendingSyncCount pending)" else "Workout")
                }
            }
            item {
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

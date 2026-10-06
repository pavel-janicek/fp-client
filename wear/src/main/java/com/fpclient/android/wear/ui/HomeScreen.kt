package com.fpclient.android.wear.ui

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
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Button
import com.fpclient.android.wear.auth.WearAuthState

/**
 * Landing screen of FitPub Wear (Iteration 9a).
 *
 * Round-display safety is done in two layers:
 *  - everything in the centre column is scaled from the screen diameter measured by
 *    [BoxWithConstraints], referenced against 200 dp (≈ the diameter of a typical Wear OS 3+
 *    round display, e.g. 400 px at 2x density) and clamped so odd form factors cannot blow the
 *    layout up or shrink it to unreadable;
 *  - the version label rides the bottom rim on a [CurvedLayout]/[basicCurvedText] arc instead of
 *    a flat row, so it stays inside the bezel on round screens while still looking right on
 *    square ones (the arc simply lands on the bottom edge either way).
 *
 * The identity line reflects the phone-relayed session; workout controls live on their own
 * scrollable route so they do not compete with sign-in on the round display.
 */
@Composable
fun HomeScreen(
    versionName: String,
    onOpenAbout: () -> Unit,
    authState: WearAuthState = WearAuthState(),
    phoneReachable: Boolean? = null,
    pendingSyncCount: Int = 0,
    onRequestCredentials: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenWorkout: () -> Unit = {},
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
            // 200 dp reference diameter; clamped so a square 400 dp tablet-like preview or a tiny
            // legacy watch cannot produce absurd typography.
            val scale = (minOf(maxWidth, maxHeight) / 200.dp).coerceIn(0.85f, 1.3f)
            // Resolved here, in composable context: the content lambda of CurvedLayout below is
            // a plain (non-@Composable) scope, so MaterialTheme could not be read inside it.
            val versionColor = MaterialTheme.colors.onSurfaceVariant
            val versionFontSize = 11.sp * scale

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    // Horizontal inset keeps text off the curved edges of a round display.
                    .padding(horizontal = 32.dp * scale),
            ) {
                Text(
                    text = "FitPub",
                    fontSize = 32.sp * scale,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp * scale))
                val identityText = when {
                    authState.isSignedIn -> "Device signed in as @${authState.username.ifBlank { "user" }}"
                    authState.expired -> "Sign-in expired. Sign in again on your phone."
                    phoneReachable == false -> "No paired phone is reachable"
                    else -> "Not signed in yet"
                }
                Text(
                    text = identityText,
                    fontSize = 15.sp * scale,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp * scale))
                if (authState.displayName.isNotBlank() && authState.isSignedIn) {
                    Text(
                        text = authState.displayName,
                        fontSize = 12.sp * scale,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Button(
                    onClick = onRequestCredentials,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (authState.isSignedIn) "Refresh from phone" else "Sign in with phone")
                }
                if (authState.isSignedIn || authState.expired) {
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign out")
                    }
                }
                if (authState.isSignedIn && phoneReachable == false) {
                    Text(
                        text = "Using saved sign-in; phone is not reachable",
                        fontSize = 11.sp * scale,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Card(
                    onClick = onOpenWorkout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (pendingSyncCount > 0) "Workout ($pendingSyncCount pending)" else "Workout",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp * scale))
                Card(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "About",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Version label curved along the bottom rim (anchor 90° = 6 o'clock).
            CurvedLayout(
                modifier = Modifier.fillMaxSize(),
                anchor = 90f,
            ) {
                basicCurvedText(
                    text = "v$versionName",
                    style = CurvedTextStyle(
                        color = versionColor,
                        fontSize = versionFontSize,
                    ),
                )
            }
        }
    }
}

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun HomeScreenPreview() {
    FitPubWearTheme {
        HomeScreen(versionName = "2.2.1", onOpenAbout = {})
    }
}

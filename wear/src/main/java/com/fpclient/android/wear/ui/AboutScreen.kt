package com.fpclient.android.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fpclient.android.wear.BuildConfig

/**
 * About screen (Iteration 9a): version plus the two facts a tester standing next to a watch
 * actually needs — how independent the module is, and what this build does *not* do yet.
 *
 * [ScalingLazyColumn] is the round-safe list: it scales and fades items towards the bezel so
 * nothing clips on a round display, and it scrolls with the crown/rotary input by default.
 * Going "back" is the platform gesture handled by [androidx.wear.compose.navigation.SwipeDismissableNavHost].
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "FitPub Wear",
                    style = MaterialTheme.typography.title2,
                    color = MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Independent watch module — it imports nothing from the phone app, " +
                        "which relays sign-in over the Wearable Data Layer.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "The phone relays your FitPub sign-in to this watch. Data Layer " +
                        "messages are protected in transit; watch-side token encryption at " +
                        "rest is a later hardening step. This build does not record workouts.",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun AboutScreenPreview() {
    FitPubWearTheme {
        AboutScreen()
    }
}

package com.fpclient.android.ui.record

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.fpclient.android.recording.RecordingState
import com.fpclient.android.recording.TrackRecordingBus
import com.fpclient.android.recording.TrackSessionSnapshot
import com.fpclient.android.ui.theme.FPClientTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented smoke tests for the Record flow (Iteration 8c): the pre-start picker,
 * the live screen controls, and the app-wide recording banner. Location permissions are
 * granted via the shell (pm grant) so the gate shows the actual flow instead of its
 * explanation card — no extra test-rule dependency needed.
 */
class RecordFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun resetBusAndGrantPermissions() {
        TrackRecordingBus.publish(null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        ).forEach { permission ->
            instrumentation.uiAutomation
                .executeShellCommand("pm grant ${target.packageName} $permission")
        }
    }

    @After
    fun clearBus() {
        TrackRecordingBus.publish(null)
    }

    private fun publishActiveSession(type: String = "RUN") {
        val now = System.currentTimeMillis()
        TrackRecordingBus.publish(
            TrackSessionSnapshot(RecordingState.RECORDING, now, 0L, now, type),
        )
    }

    @Test
    fun record_preStart_showsActivityPickerAndStartButton() {
        composeRule.setContent {
            FPClientTheme { RecordScreen(onBack = {}) }
        }

        composeRule.onNodeWithText("What are you doing?").assertIsDisplayed()
        composeRule.onNodeWithText("🏃 run").assertIsDisplayed()
        composeRule.onNodeWithText("🚴 ride").assertIsDisplayed()
        composeRule.onNodeWithText("Start recording").assertIsDisplayed()
    }

    @Test
    fun record_liveScreen_showsTimerControlsAndSettings() {
        publishActiveSession("RIDE")
        composeRule.setContent {
            FPClientTheme { RecordScreen(onBack = {}) }
        }

        composeRule.onNodeWithText("🚴 ride").assertIsDisplayed()
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
        composeRule.onNodeWithText("Keep screen on").assertIsDisplayed()
        composeRule.onNodeWithText("Show mini-map").assertIsDisplayed()
    }

    @Test
    fun banner_showsActivityAndElapsedWhileSessionIsActive() {
        publishActiveSession("HIKE")
        composeRule.setContent {
            FPClientTheme { RecordingBanner(onOpen = {}) }
        }

        composeRule.onNodeWithText("Recording 🥾 hike", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("tap to open", substring = true).assertIsDisplayed()
    }
}
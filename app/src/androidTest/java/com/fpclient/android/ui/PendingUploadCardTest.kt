package com.fpclient.android.ui.record

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fpclient.android.recording.PendingUpload
import com.fpclient.android.ui.theme.FPClientTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PendingUploadCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun discard_requiresConfirmation_andCancelKeepsWorkout() {
        var deletions = 0
        composeRule.setContent {
            FPClientTheme {
                PendingUploadCard(
                    entry = PendingUpload(
                        sessionId = 1L,
                        startedAtEpochMs = 1L,
                        activityType = "RUN",
                        createdAtEpochMs = 1L,
                    ),
                    busy = false,
                    onOpen = {},
                    onDiscard = { deletions++ },
                )
            }
        }
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.onNodeWithText("Discard this workout?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, deletions) }
        composeRule.onNodeWithText("Keep workout").performClick()
        composeRule.onNodeWithText("Discard this workout?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, deletions) }
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.onNodeWithText("Delete workout").performClick()
        composeRule.onNodeWithText("Discard this workout?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(1, deletions) }
    }
}

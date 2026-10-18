package com.irhunter.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun launchesWithoutEmitterAndNavigatesOfflineEmptyStates() {
        compose.onNodeWithText("IR HUNTER").assertIsDisplayed()
        compose.onNodeWithText("No compatible built-in IR emitter detected.", substring = true).assertExists()
        compose.onNodeWithText("Saved", substring = true).performClick()
        compose.onNodeWithTag("contentList").performScrollToNode(hasText("No matching profiles.", substring = true))
        compose.onNodeWithText("No matching profiles.", substring = true).assertExists()
        compose.onNodeWithText("All OFF", substring = true).performClick()
        compose.onNodeWithText("All Devices OFF").assertIsDisplayed()
        compose.onNodeWithText("No saved remotes.", substring = true).assertExists()
        compose.onNodeWithTag("contentList").performScrollToNode(hasText("Review and send (0)"))
        compose.onNodeWithText("Review and send (0)").assertIsNotEnabled()
    }
}

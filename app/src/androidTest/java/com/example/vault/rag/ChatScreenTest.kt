package com.example.vault.rag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.vault.MainActivity
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun questionReturnsAnswerAndClickableSourcePreview() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(10_000) { compose.onNodeWithTag("question-input").fetchSemanticsNode().config
            .contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText) &&
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("chat-loading")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("send-question").assertIsNotEnabled()
        compose.onNodeWithTag("question-input").performTextInput("When is rent due?")
        compose.onNodeWithTag("send-question").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("source-S1")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Document excerpts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("source-S1").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("source-preview")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("source-preview").assertIsDisplayed()
        compose.onNodeWithText("Apartment lease.txt · page 1").assertIsDisplayed()
    }
}

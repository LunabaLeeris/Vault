package com.example.vault.rag

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.vault.MainActivity
import com.example.vault.data.repository.VaultRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = VaultRepository.getInstance(context)
    private val title = "Chat reference " + UUID.randomUUID()
    private var folderId = 0L
    private var input: File? = null

    @Before fun seedOnlyTestOwnedDocument() = runBlocking {
        folderId = repository.createFolder("Chat test " + UUID.randomUUID()).getOrThrow()
        val file = IntegrationFixtures.pdf(context).also { input = it }
        val id = repository.saveDocument(Uri.fromFile(file), title, "application/pdf", folderId).getOrThrow()
        repository.extractAndSaveTextForDocument(id).getOrThrow()
        Unit
    }

    @After fun cleanupOnlyTestOwnedDocument() = runBlocking {
        if (folderId > 0) repository.deleteFolder(folderId).getOrThrow()
        input?.delete()
        Unit
    }

    @Test fun vaultChatOpensCitedPdfPageAndRetainsHistory() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.onNodeWithTag("open-chat").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasTestTag("question-input")).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodes(hasTestTag("chat-loading")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Search: All documents").performClick()
        compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithTag("send-question").assertIsNotEnabled()
        if (File(context.filesDir, "models/chat.litertlm").isFile) compose.onNodeWithTag("model-mode").performClick()
        compose.onNodeWithTag("question-input").performTextInput("When is rent due?")
        compose.onNodeWithTag("send-question").assertIsEnabled().performClick()
        compose.waitUntil(120_000) { compose.onAllNodes(hasTestTag("source-S1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("source-S1").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Page 3 of 3")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pdf-page-status").assertTextEquals("Page 3 of 3")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("source-S1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("source-S1").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
    }
}

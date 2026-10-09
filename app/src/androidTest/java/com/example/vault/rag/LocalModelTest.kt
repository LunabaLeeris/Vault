package com.example.vault.rag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.SearchScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs real inference only when a developer has provisioned a compatible local model. */
@RunWith(AndroidJUnit4::class)
class LocalModelTest {
    @Test fun localModelAnswersRentQuestionWithCheckedReference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dependencies = RagDependencies(context)
        assumeTrue("Provision files/models/chat.litertlm first", dependencies.hasLocalModel())
        val answer = dependencies.pipeline.ask("When is rent due?", SearchScope.All, dependencies.modelGenerator)
        assertEquals("The model must return checked citations instead of falling back to excerpts", AnswerMode.LOCAL_MODEL, answer.mode)
        assertTrue(answer.text.lowercase().let { "fifth" in it || "5th" in it || "5" in it })
        assertTrue(answer.references.any { it.chunk.documentId == "demo-lease" && it.chunk.pageNumber == 1 })
    }
}

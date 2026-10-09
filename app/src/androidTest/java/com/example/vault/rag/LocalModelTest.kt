package com.example.vault.rag

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vault.data.repository.VaultRepository
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.SearchScope
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalModelTest {
    @Test fun localModelAnswersFromRealSavedOcrWithPageThreeCitation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dependencies = RagDependencies(context)
        assumeTrue("Provision files/models/chat.litertlm first", dependencies.hasLocalModel())
        val repository = VaultRepository.getInstance(context)
        val folder = repository.createFolder("Model test " + UUID.randomUUID()).getOrThrow()
        val input = IntegrationFixtures.pdf(context)
        try {
            val documentId = repository.saveDocument(Uri.fromFile(input), "Model integration reference", "application/pdf", folder).getOrThrow()
            repository.extractAndSaveTextForDocument(documentId).getOrThrow()
            val answer = dependencies.pipeline.ask("When is rent due?", SearchScope.Document(documentId.toString()), dependencies.modelGenerator)
            assertEquals(AnswerMode.LOCAL_MODEL, answer.mode)
            assertTrue(answer.text.lowercase().let { "fifth" in it || "5th" in it || "5" in it })
            assertTrue(answer.references.any { it.chunk.documentId == documentId.toString() && it.chunk.pageNumber == 3 })
        } finally {
            repository.deleteFolder(folder).getOrThrow()
            input.delete()
            dependencies.pipeline.refresh()
        }
    }
}

package com.example.vault.rag

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vault.data.local.VaultDatabase
import com.example.vault.data.repository.FileStorageManager
import com.example.vault.data.repository.VaultRepository
import com.example.vault.ocr.ExtractedPage
import com.example.vault.rag.generation.ExcerptAnswerGenerator
import com.example.vault.rag.index.KeywordQuery
import com.example.vault.rag.index.SqliteRetrievalIndex
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.SearchScope
import com.example.vault.rag.source.RoomDocumentSource
import java.io.File
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRagIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).build()
    private val repository = VaultRepository(database.vaultDao(), FileStorageManager(context), database)
    private val source = RoomDocumentSource(repository)
    private val index = SqliteRetrievalIndex(context, null)
    private val pipeline = RagPipeline(source, index)
    private val inputs = mutableListOf<File>()
    private var folderId = 0L

    @Before fun setup() = runBlocking { folderId = repository.createFolder("Test vault").getOrThrow(); Unit }
    @After fun cleanup() = runBlocking {
        if (database.vaultDao().getFolderByIdSync(folderId) != null) repository.deleteFolder(folderId).getOrThrow()
        inputs.forEach { it.delete() }
        index.close(); database.close()
    }

    private suspend fun savePdf(): Long {
        val file = IntegrationFixtures.pdf(context).also { inputs.add(it) }
        return repository.saveDocument(Uri.fromFile(file), "Integration reference", "application/pdf", folderId).getOrThrow()
    }

    @Test fun realPdfOcrAndEditsFeedTheSamePipeline() = runBlocking {
        val id = savePdf()
        repository.extractAndSaveTextForDocument(id).getOrThrow()
        val entry = source.snapshot().single()
        assertEquals(3, entry.pages.size)
        assertEquals(2L, entry.pages.last().revision)
        val answer = pipeline.ask("When is rent due?", SearchScope.Document(id.toString()), ExcerptAnswerGenerator())
        assertTrue(answer.text.contains("fifth", ignoreCase = true))
        assertEquals(3, answer.references.first().chunk.pageNumber)
        val page = repository.readDocumentSnapshot().single().pages.single { it.pageNumber == 3 }
        repository.updatePageText(page.id, "Rent is due on the eighth day of each month.").getOrThrow()
        val edited = pipeline.ask("When is rent due?", SearchScope.All, ExcerptAnswerGenerator())
        assertTrue(edited.text.contains("eighth"))
        assertFalse(edited.text.contains("fifth"))
        assertTrue(edited.references.first().chunk.isEdited)
        assertEquals(3L, edited.references.first().chunk.revision)
        assertTrue(repository.readDocumentSnapshot().single().pages.single { it.id == page.id }.rawText.contains("fifth"))
        repository.updatePageText(page.id, "").getOrThrow()
        assertEquals(AnswerMode.NO_EVIDENCE, pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator()).mode)
    }

    @Test fun realImageOcrIsAvailableAfterImport() = runBlocking {
        val file = IntegrationFixtures.image(context).also { inputs.add(it) }
        val id = repository.saveDocument(Uri.fromFile(file), "Image reference", "image/png", folderId).getOrThrow()
        repository.extractAndSaveTextForDocument(id).getOrThrow()
        val answer = pipeline.ask("laptop warranty", SearchScope.Document(id.toString()), ExcerptAnswerGenerator())
        assertTrue(answer.text.contains("24"))
        assertEquals(1, answer.references.first().chunk.pageNumber)
    }

    @Test fun staleOcrCannotRestoreDeletedTextAndOriginalStillOpens() = runBlocking {
        val id = savePdf()
        val pages = listOf(ExtractedPage(3, "Rent due on day five"))
        repository.saveExtractedPagesForDocument(id, pages).getOrThrow()
        val answer = pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator())
        val oldRevision = repository.findDocument(id)!!.textRevision
        repository.deleteExtractedText(id).getOrThrow()
        assertTrue(repository.saveExtractedPagesForDocument(id, pages, oldRevision).isFailure)
        assertTrue(repository.readDocumentSnapshot().single().pages.isEmpty())
        assertEquals(AnswerMode.NO_EVIDENCE, pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator()).mode)
        val original = pipeline.openSource(answer.references.first().chunk)!!
        assertNull(original.page)
        assertEquals(3, original.requestedPageNumber)
        assertTrue(repository.getFile(repository.findDocument(id)!!.relativePath).isFile)
    }

    @Test fun reextractionPreservesEmptyEditAndPageIdentityAndFailuresPreserveText() = runBlocking {
        val id = savePdf()
        repository.saveExtractedPagesForDocument(id, listOf(ExtractedPage(1, "Raw text"))).getOrThrow()
        val page = repository.readDocumentSnapshot().single().pages.single()
        repository.updatePageText(page.id, "").getOrThrow()
        repository.saveExtractedPagesForDocument(id, listOf(ExtractedPage(1, "New raw text"))).getOrThrow()
        val current = repository.readDocumentSnapshot().single().pages.single()
        assertEquals(page.id, current.id)
        assertEquals("New raw text", current.rawText)
        assertEquals("", current.content)
        val document = repository.findDocument(id)!!
        repository.getFile(document.relativePath).delete()
        assertTrue(repository.extractAndSaveTextForDocument(id).isFailure)
        assertEquals(current, repository.readDocumentSnapshot().single().pages.single())
        assertEquals(document.textRevision, repository.findDocument(id)!!.textRevision)
    }

    @Test fun committedDeletionRefreshesCacheAndCleansOwnedFolderFiles() = runBlocking {
        val id = savePdf()
        repository.saveExtractedPagesForDocument(id, listOf(ExtractedPage(1, "Rental deposit"))).getOrThrow()
        val file = repository.getFile(repository.findDocument(id)!!.relativePath)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = RagSyncCoordinator(repository, pipeline, scope)
        try {
            pipeline.refresh()
            assertFalse(index.search(KeywordQuery.terms("deposit", 16), SearchScope.All, 4).isEmpty())
            repository.deleteFolder(folderId).getOrThrow()
            withTimeout(10_000) {
                while (index.search(KeywordQuery.terms("deposit", 16), SearchScope.All, 4).isNotEmpty()) delay(50)
            }
            assertFalse(file.exists())
            assertTrue(repository.readDocumentSnapshot().isEmpty())
        } finally { coordinator.close(); scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}

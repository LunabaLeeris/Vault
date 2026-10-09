package com.example.vault.rag

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import com.example.vault.rag.index.KeywordQuery
import com.example.vault.rag.index.PageChunker
import com.example.vault.rag.index.SqliteRetrievalIndex
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.SearchScope
import com.example.vault.rag.model.TextPage
import com.example.vault.rag.source.AssetDocumentSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SqliteRetrievalIndexTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "rag_test_${UUID.randomUUID()}.db"
    private var index = SqliteRetrievalIndex(context, databaseName)
    private fun terms(question: String) = KeywordQuery.terms(question, 16)

    @After fun cleanup() { index.close(); context.deleteDatabase(databaseName) }

    @Test fun fixtureQuestionFindsLeasePageAndSurvivesReopen() = runBlocking {
        val documents = AssetDocumentSource(InstrumentationRegistry.getInstrumentation().context.assets).snapshot()
        index.replace(documents.flatMap { PageChunker().chunk(it, RagConfig()) })
        val found = index.search(terms("When is rent due?"), SearchScope.All, 4)
        assertEquals("demo-lease", found.first().documentId)
        assertTrue(found.first().text.contains("fifth day"))
        assertEquals(1, found.first().pageNumber)
        assertTrue(index.search(terms("rent"), SearchScope.Folder("demo-travel"), 4).isEmpty())
        assertTrue(index.search(terms("astronomy"), SearchScope.All, 4).isEmpty())
        index.close()
        index = SqliteRetrievalIndex(context, databaseName)
        assertFalse(index.search(terms("rent"), SearchScope.All, 4).isEmpty())
    }

    @Test fun scopePrecedesLimitAndReplacementDeletesStaleText() = runBlocking {
        val foreign = (1..20).map { RagDocument("foreign$it", "Rent", "other", "Other", listOf(TextPage(1, "Rent", 1))) }
        val desired = RagDocument("target", "Lease", "home", "Home", listOf(TextPage(2, "Rent due on day five", 1)))
        index.replace((foreign + desired).flatMap { PageChunker().chunk(it, RagConfig()) })
        assertEquals("target", index.search(terms("rent"), SearchScope.Document("target"), 1).single().documentId)
        val edited = desired.copy(title = "Renamed", pages = listOf(TextPage(2, "Deposit returned after inspection", 2, true)))
        index.replace(PageChunker().chunk(edited, RagConfig()))
        assertTrue(index.search(terms("rent"), SearchScope.All, 4).isEmpty())
        val found = index.search(terms("deposit"), SearchScope.Folder("home"), 4).single()
        assertEquals("Renamed", found.title)
        assertEquals(2L, found.revision)
        assertTrue(found.isEdited)
        index.replace(emptyList())
        assertTrue(index.search(terms("deposit"), SearchScope.All, 4).isEmpty())
    }
}

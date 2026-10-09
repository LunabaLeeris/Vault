package com.example.vault.rag

import com.example.vault.rag.generation.AnswerGenerator
import com.example.vault.rag.generation.ExcerptAnswerGenerator
import com.example.vault.rag.index.RetrievalIndex
import com.example.vault.rag.model.*
import com.example.vault.rag.source.DocumentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RagPipelineTest {
    private fun document(text: String = "Rent is due on the fifth day.", revision: Long = 1, title: String = "Lease") =
        RagDocument("doc", title, "home", "Home", listOf(TextPage(1, text, revision, revision > 1)))

    private class Source(var documents: List<RagDocument>) : DocumentSource {
        override suspend fun snapshot() = documents
    }

    private class Index : RetrievalIndex {
        var chunks = emptyList<ReferenceChunk>()
        var replaceCount = 0
        var failRefresh = false
        override suspend fun replace(chunks: List<ReferenceChunk>) {
            if (failRefresh) error("Simulated database failure")
            this.chunks = chunks
            replaceCount++
        }
        override suspend fun search(terms: List<String>, scope: SearchScope, limit: Int) = chunks.filter { chunk ->
            terms.any { chunk.text.contains(it, ignoreCase = true) } && when (scope) {
                SearchScope.All -> true
                is SearchScope.Document -> chunk.documentId == scope.id
                is SearchScope.Folder -> chunk.folderId == scope.id
            }
        }.take(limit)
    }

    @Test fun boundsEvidenceAndFallsBackForInventedCitations() = runTest {
        val source = Source(listOf(document("rent ".repeat(100))))
        val index = Index()
        var supplied = emptyList<Evidence>()
        val generator = object : AnswerGenerator {
            override val mode = AnswerMode.LOCAL_MODEL
            override suspend fun generate(question: String, evidence: List<Evidence>, config: RagConfig): String {
                supplied = evidence
                return "Invented fact [S99]"
            }
        }
        val pipeline = RagPipeline(source, index, RagConfig(maxContextChars = 40))
        val answer = pipeline.ask("rent", SearchScope.All, generator)
        assertTrue(supplied.sumOf { it.chunk.text.length } <= 40)
        assertEquals(AnswerMode.EXCERPTS, answer.mode)
        assertFalse(answer.text.contains("Invented fact"))
        assertNotNull(answer.notice)
        assertEquals(supplied, answer.references)
    }

    @Test fun refreshesEditsRenamesTextDeletionAndDocumentDeletion() = runTest {
        val source = Source(listOf(document()))
        val index = Index()
        val pipeline = RagPipeline(source, index)
        val first = pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator())
        pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator())
        assertEquals(1, index.replaceCount)
        source.documents = listOf(document("Rent is now due on day ten.", 2, "Renamed lease"))
        val edited = pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator())
        assertEquals("Renamed lease", edited.references.first().chunk.title)
        assertEquals(2L, edited.references.first().chunk.revision)
        assertTrue(pipeline.openSource(first.references.first().chunk)!!.revisionChanged)
        source.documents = listOf(document("", 3))
        assertEquals(AnswerMode.NO_EVIDENCE, pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator()).mode)
        assertTrue(index.chunks.isEmpty())
        source.documents = listOf(document().copy(pages = emptyList()))
        val original = pipeline.openSource(first.references.first().chunk)
        assertNotNull(original)
        assertNull(original!!.page)
        assertEquals(1, original.requestedPageNumber)
        source.documents = emptyList()
        pipeline.refresh()
        assertNull(pipeline.openSource(first.references.first().chunk))
    }

    @Test fun refreshFailureDoesNotAnswerFromStaleChunksAndCanRetry() = runTest {
        val source = Source(listOf(document()))
        val index = Index()
        val pipeline = RagPipeline(source, index)
        pipeline.refresh()
        source.documents = listOf(document("Rent changed.", 2))
        index.failRefresh = true
        try { pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator()); fail("Expected refresh failure") }
        catch (_: IllegalStateException) { }
        index.failRefresh = false
        assertEquals(2L, pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator()).references.first().chunk.revision)
    }

    @Test fun emptyEvidenceSkipsGenerationAndCancellationPropagates() = runTest {
        val pipeline = RagPipeline(Source(listOf(document())), Index())
        var calls = 0
        val cancelled = object : AnswerGenerator {
            override val mode = AnswerMode.LOCAL_MODEL
            override suspend fun generate(question: String, evidence: List<Evidence>, config: RagConfig): String {
                calls++
                throw CancellationException("Cancelled")
            }
        }
        assertEquals(AnswerMode.NO_EVIDENCE, pipeline.ask("astronomy", SearchScope.All, cancelled).mode)
        assertEquals(0, calls)
        try { pipeline.ask("rent", SearchScope.All, cancelled); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun newConfigRechunksExistingContent() = runTest {
        val index = Index()
        val pipeline = RagPipeline(Source(listOf(document("rent ".repeat(100)))), index)
        pipeline.refresh()
        pipeline.updateConfig(RagConfig(chunkChars = 50, overlapChars = 5, maxContextChars = 30))
        val answer = pipeline.ask("rent", SearchScope.All, ExcerptAnswerGenerator())
        assertEquals(2, index.replaceCount)
        assertTrue(index.chunks.all { it.text.length <= 50 })
        assertTrue(answer.references.sumOf { it.chunk.text.length } <= 30)
    }
}

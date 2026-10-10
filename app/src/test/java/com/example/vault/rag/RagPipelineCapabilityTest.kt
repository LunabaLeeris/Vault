package com.example.vault.rag

import com.example.vault.rag.generation.ExcerptAnswerGenerator
import com.example.vault.rag.index.KeywordQuery
import com.example.vault.rag.index.RetrievalIndex
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.ReferenceChunk
import com.example.vault.rag.model.SearchScope
import com.example.vault.rag.model.TextPage
import com.example.vault.rag.source.DocumentSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPipelineCapabilityTest {
    private val documents = listOf(
        RagDocument("1", "GCash Transfer Receipt", "inbox", "Inbox", listOf(TextPage(1, "Money sent via GCash wallet transfer of 500 PHP.", 1))),
        RagDocument("2", "GCash Bill Payment", "finance", "Finance", listOf(TextPage(1, "Payment of electricity bill through GCash express.", 1))),
        RagDocument("3", "Apartment Lease Contract", "legal", "Legal", listOf(TextPage(1, "Standard residential lease agreement and terms.", 1))),
    )

    @Test fun listingWithTypoMatchesAllRelevantDocumentsAndExcludesUnrelated() = runBlocking {
        val index = HybridSimulatedIndex()
        // User query with typo "realted"
        val plan = IntentRouter().classify("list all documents realted to gcash", documents)
        val result = pipeline(index).execute(plan, ExcerptAnswerGenerator())
        assertEquals(DocumentIntent.LIST_DOCUMENTS, plan.intent)
        assertEquals(listOf("1", "2"), result.matchedDocuments.map { it.id })
        assertEquals(2, result.references.size)
        assertTrue(result.text.startsWith("Here are related documents regarding gcash:"))
    }

    @Test fun listingDeduplicatesIdenticalDocumentCopies() = runBlocking {
        val docsWithDuplicates = listOf(
            RagDocument("1", "GCash Transfer Receipt", "inbox", "Inbox", listOf(TextPage(1, "Money sent via GCash wallet transfer of 500 PHP.", 1))),
            RagDocument("2", "GCash Transfer Receipt", "archive", "Archive", listOf(TextPage(1, "Money sent via GCash wallet transfer of 500 PHP.", 1))),
            RagDocument("3", "GCash Bill Payment", "finance", "Finance", listOf(TextPage(1, "Payment of electricity bill through GCash express.", 1))),
        )
        val index = HybridSimulatedIndex()
        val plan = IntentRouter().classify("list all documents related to gcash", docsWithDuplicates)
        val result = RagPipeline(MemorySource(docsWithDuplicates), index).execute(plan, ExcerptAnswerGenerator())
        assertEquals(DocumentIntent.LIST_DOCUMENTS, plan.intent)
        assertEquals(2, result.matchedDocuments.size)
        assertEquals(listOf("GCash Transfer Receipt", "GCash Bill Payment"), result.matchedDocuments.map { it.title })
        assertEquals(2, result.references.size)
        assertTrue(result.text.startsWith("Here are related documents regarding gcash:"))
    }

    @Test fun listingPreservesDistinctDocumentsEvenWithSameTitle() = runBlocking {
        val licenses = listOf(
            RagDocument("10", "Driver's License", "ids", "IDs", listOf(TextPage(1, "Republic of the Philippines Driver's License Name: Ron Santos Lic: N01-11", 1))),
            RagDocument("11", "Driver's License", "ids", "IDs", listOf(TextPage(1, "Republic of the Philippines Driver's License Name: Jane Santos Lic: N02-22", 1))),
            RagDocument("12", "Medical Record", "health", "Health", listOf(TextPage(1, "Complete Blood Count Lab Results CBC normal", 1))),
        )
        val index = HybridSimulatedIndex()
        val plan = IntentRouter().classify("list me all drivers licenses", licenses)
        val result = RagPipeline(MemorySource(licenses), index).execute(plan, ExcerptAnswerGenerator())
        assertEquals(DocumentIntent.LIST_DOCUMENTS, plan.intent)
        // Both driver's licenses must be returned, and Medical Record must be excluded!
        assertEquals(listOf("10", "11"), result.matchedDocuments.map { it.id })
        assertEquals(2, result.references.size)
        assertTrue(result.matchedDocuments.none { it.id == "12" })
    }

    @Test fun irrelevantQuestionsReturnNoEvidenceWithoutHallucinations() = runBlocking {
        val index = HybridSimulatedIndex()
        val plan = IntentRouter().classify("Who is the president of Philippines?", documents)
        val result = pipeline(index).execute(plan, ExcerptAnswerGenerator())
        assertEquals(AnswerMode.NO_EVIDENCE, result.mode)
        assertTrue(result.references.isEmpty())
        assertTrue(result.text.contains("can't find anything related", ignoreCase = true) || result.text.contains("only answer questions about documents", ignoreCase = true))
    }

    @Test fun missingContextReturnsHelpfulNoEvidenceMessage() = runBlocking {
        val index = HybridSimulatedIndex()
        // Query about something not present in documents ("hospital discharge summary")
        val plan = IntentRouter().planForIntent(DocumentIntent.LOOKUP_FACT, "what are the key terms in my hospital discharge summary", documents)
        val result = pipeline(index).execute(plan, ExcerptAnswerGenerator())
        assertEquals(AnswerMode.NO_EVIDENCE, result.mode)
        assertTrue(result.references.isEmpty())
        assertEquals(RagPipeline.NO_EVIDENCE_MESSAGE, result.text)
    }

    @Test fun lookupPassesTheFullPromptToSemanticRetrieval() = runBlocking {
        val index = HybridSimulatedIndex()
        val plan = IntentRouter().classify("What is the lease contract about?", documents)
        val result = pipeline(index).execute(plan, ExcerptAnswerGenerator())
        assertTrue(result.references.any { it.chunk.documentId == "3" })
    }

    @Test fun listingHonorsRequestedTopKAfterDocumentAggregation() = runBlocking {
        val index = HybridSimulatedIndex(returnAll = true)
        val plan = IntentRouter().classify("List top 1 related documents", documents)
        val result = pipeline(index).execute(plan, ExcerptAnswerGenerator())
        assertEquals(1, result.matchedDocuments.size)
    }

    @Test fun strictChunkFilteringExcludesUnrelatedDocuments() = runBlocking {
        val index = HybridSimulatedIndex()
        // Query matches GCash (doc 1 and 2), Apartment Lease (doc 3) is unrelated
        val plan = IntentRouter().planForIntent(DocumentIntent.LOOKUP_FACT, "how much money sent gcash", documents)
        val result = pipeline(index).execute(plan, ExcerptAnswerGenerator())
        // References should only contain the relevant gcash document, never doc 3
        assertTrue(result.references.isNotEmpty())
        assertTrue(result.references.all { it.chunk.documentId in listOf("1", "2") })
        assertTrue(result.references.none { it.chunk.documentId == "3" })
    }

    private fun pipeline(index: RetrievalIndex): RagPipeline = RagPipeline(MemorySource(documents), index)

    private class MemorySource(private val documents: List<RagDocument>) : DocumentSource {
        override suspend fun snapshot(): List<RagDocument> = documents
    }

    private class HybridSimulatedIndex(private val returnAll: Boolean = false) : RetrievalIndex {
        private var chunks: List<ReferenceChunk> = emptyList()
        var lastQuery: String? = null

        override suspend fun replace(chunks: List<ReferenceChunk>) { this.chunks = chunks }

        override suspend fun search(query: String, scope: SearchScope, limit: Int): List<ReferenceChunk> {
            lastQuery = query
            val scoped = chunks.filter {
                when (scope) {
                    SearchScope.All -> true
                    is SearchScope.Folder -> it.folderId == scope.id
                    is SearchScope.Document -> it.documentId == scope.id
                }
            }
            val terms = KeywordQuery.terms(query)
            if (returnAll) {
                return scoped.mapIndexed { index, chunk -> chunk.copy(similarity = 0.90f - index * 0.05f) }.take(limit)
            }
            if (terms.isEmpty()) return emptyList()

            return scoped.mapNotNull { chunk ->
                val lexScore = KeywordQuery.lexicalScore(chunk.text, chunk.title, terms)
                if (lexScore > 0f) {
                    chunk.copy(similarity = (0.75f * 0.40f + lexScore * 0.60f).coerceIn(0f, 1f))
                } else null
            }.sortedByDescending { it.similarity }.take(limit)
        }
    }
}

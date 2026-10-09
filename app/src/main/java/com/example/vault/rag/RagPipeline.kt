package com.example.vault.rag

import com.example.vault.rag.generation.AnswerGenerator
import com.example.vault.rag.generation.CitationChecker
import com.example.vault.rag.generation.ExcerptAnswerGenerator
import com.example.vault.rag.index.KeywordQuery
import com.example.vault.rag.index.PageChunker
import com.example.vault.rag.index.RetrievalIndex
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.Evidence
import com.example.vault.rag.model.RagAnswer
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.ReferenceChunk
import com.example.vault.rag.model.SearchScope
import com.example.vault.rag.model.SourcePage
import com.example.vault.rag.source.DocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RagPipeline(
    private val source: DocumentSource,
    private val index: RetrievalIndex,
    initialConfig: RagConfig = RagConfig(),
) {
    private val mutex = Mutex()
    private val chunker = PageChunker()
    private val excerpts = ExcerptAnswerGenerator()
    private var config = initialConfig
    private var indexedDocuments: List<RagDocument>? = null
    private var indexedChunkConfig: Pair<Int, Int>? = null

    /** A future settings screen can pass a new immutable configuration here. */
    suspend fun updateConfig(value: RagConfig) = mutex.withLock { config = value }

    suspend fun refresh(): List<RagDocument> = withContext(Dispatchers.Default) {
        mutex.withLock { synchronize() }
    }

    private suspend fun synchronize(): List<RagDocument> {
        val documents = source.snapshot()
        require(documents.map { it.id }.distinct().size == documents.size) { "Duplicate document IDs." }
        val chunkConfig = config.chunkChars to config.overlapChars
        if (documents != indexedDocuments || indexedChunkConfig != chunkConfig) {
            val chunks = documents.flatMap { document ->
                kotlin.coroutines.coroutineContext.ensureActive()
                chunker.chunk(document, config)
            }
            index.replace(chunks)
            // Publish the new snapshot only after the atomic cache replacement succeeds.
            indexedDocuments = documents
            indexedChunkConfig = chunkConfig
        }
        return documents
    }

    suspend fun ask(question: String, scope: SearchScope, generator: AnswerGenerator): RagAnswer =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val cleaned = question.trim()
                require(cleaned.isNotEmpty() && cleaned.length <= config.maxQuestionChars) {
                    "Enter a question of at most ${config.maxQuestionChars} characters."
                }
                val documents = synchronize()
                val chunks = index.search(
                    KeywordQuery.terms(cleaned, config.maxQueryTerms), scope, config.maxRetrievedChunks,
                )
                val evidence = boundEvidence(chunks, config.maxContextChars)
                if (evidence.isEmpty()) return@withLock RagAnswer(
                    text = if (documents.none { it.pages.any { page -> page.text.isNotBlank() } })
                        "No extracted text is available. Add document text before asking a question."
                    else "I could not find relevant evidence in the documents for that question.",
                    references = emptyList(), mode = AnswerMode.NO_EVIDENCE,
                )
                val generated = generator.generate(cleaned, evidence, config)
                val checked = CitationChecker.resolve(generated, evidence)
                if (checked == null) RagAnswer(
                    text = excerpts.generate(cleaned, evidence, config), references = evidence,
                    mode = AnswerMode.EXCERPTS,
                    notice = "The generated answer lacked valid source references. Showing document excerpts.",
                ) else RagAnswer(text = generated, references = checked, mode = generator.mode)
            }
        }

    suspend fun openSource(chunk: ReferenceChunk): SourcePage? = withContext(Dispatchers.IO) {
        val document = source.findDocument(chunk.documentId) ?: return@withContext null
        val page = document.pages.find { it.number == chunk.pageNumber }
        // Missing extracted text must not block navigation to an existing original document.
        SourcePage(document, chunk.pageNumber, page, revisionChanged = page == null || page.revision != chunk.revision)
    }

    private fun boundEvidence(chunks: List<ReferenceChunk>, budget: Int): List<Evidence> = buildList {
        var remaining = budget
        chunks.forEach { chunk ->
            if (remaining <= 0) return@forEach
            val text = chunk.text.take(remaining)
            if (text.isNotBlank()) {
                add(Evidence("S${size + 1}", chunk.copy(text = text, endOffset = chunk.startOffset + text.length)))
                remaining -= text.length
            }
        }
    }
}

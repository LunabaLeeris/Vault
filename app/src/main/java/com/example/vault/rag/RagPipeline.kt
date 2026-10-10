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
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.ln

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

    companion object {
        const val NO_EVIDENCE_MESSAGE = "I can't find anything related to that context, are you sure the question is correct or the intent you specified is accurate?"
    }

    suspend fun refresh(): List<RagDocument> = withContext(Dispatchers.Default) {
        mutex.withLock { synchronize() }
    }

    private suspend fun synchronize(): List<RagDocument> {
        val documents = source.snapshot()
        require(documents.map { it.id }.distinct().size == documents.size) { "Duplicate document IDs." }
        if (documents != indexedDocuments) {
            val chunks = documents.flatMap {
                coroutineContext.ensureActive()
                chunker.chunk(it, config)
            }
            index.replace(chunks)
            indexedDocuments = documents
        }
        return documents
    }

    suspend fun execute(plan: IntentPlan, generator: AnswerGenerator): RagAnswer = withContext(Dispatchers.Default) {
        mutex.withLock {
            val documents = synchronize()
            val scopedDocuments = documents.filter { inScope(it, plan.scope) }
            if (scopedDocuments.isEmpty()) {
                return@withLock RagAnswer("No documents are available in the selected scope.", emptyList(), AnswerMode.NO_EVIDENCE)
            }
            if (scopedDocuments.none { document -> document.pages.any { it.searchable(null).isNotBlank() } }) {
                return@withLock RagAnswer(
                    "No extracted or translated text is available in the selected documents. Extract or save document text before searching.",
                    emptyList(),
                    AnswerMode.NO_EVIDENCE,
                )
            }
            when (plan.intent) {
                DocumentIntent.LIST_DOCUMENTS -> listAnswer(documents, plan)
                DocumentIntent.LOOKUP_FACT -> lookupAnswer(plan, generator)
                DocumentIntent.DOCUMENT_IRRELEVANT -> RagAnswer(
                    plan.clarification ?: "I can only answer questions about documents stored in Vault.",
                    emptyList(),
                    AnswerMode.NO_EVIDENCE,
                )
                else -> RagAnswer(
                    plan.clarification ?: NO_EVIDENCE_MESSAGE,
                    emptyList(),
                    AnswerMode.NO_EVIDENCE,
                )
            }
        }
    }

    private suspend fun listAnswer(documents: List<RagDocument>, plan: IntentPlan): RagAnswer {
        val chunkHits = index.search(plan.normalizedRequest, plan.scope, config.maxVectorCandidates)
        val documentsById = documents.associateBy { it.id }
        val ranked = chunkHits
            .groupBy { it.documentId }
            .mapNotNull { (documentId, hits) ->
                val document = documentsById[documentId] ?: return@mapNotNull null
                val best = hits.maxOf { it.similarity }
                val score = best + (ln(hits.size.toDouble() + 1.0) * 0.015).toFloat()
                Triple(document, score, hits.maxBy { it.similarity })
            }
            .sortedByDescending { it.second }
            .distinctBy { doc ->
                val contentKey = doc.first.pages.map { it.searchable(null).trim() }.joinToString(" ")
                "${doc.first.title.trim().lowercase(Locale.ROOT)}::$contentKey"
            }
            .take(plan.resultLimit)

        if (ranked.isEmpty()) {
            return RagAnswer(NO_EVIDENCE_MESSAGE, emptyList(), AnswerMode.NO_EVIDENCE)
        }

        val terms = KeywordQuery.terms(plan.normalizedRequest)
        val hasKeywordHits = terms.isNotEmpty() && ranked.any {
            KeywordQuery.lexicalScore(it.third.text, it.first.title, terms) >= 0.15f
        }
        val substantialRanked = ranked.filter { match ->
            if (terms.isNotEmpty()) {
                val lexScore = KeywordQuery.lexicalScore(match.third.text, match.first.title, terms)
                if (hasKeywordHits) {
                    lexScore >= 0.15f && match.second >= config.minimumVectorSimilarity
                } else {
                    match.second >= maxOf(config.minimumVectorSimilarity, 0.60f)
                }
            } else {
                match.second >= maxOf(config.minimumVectorSimilarity, 0.60f)
            }
        }
        if (substantialRanked.isEmpty()) {
            return RagAnswer(NO_EVIDENCE_MESSAGE, emptyList(), AnswerMode.NO_EVIDENCE)
        }

        val maxScore = substantialRanked.maxOfOrNull { it.second } ?: 0f
        val filteredRanked = if (maxScore > 0f) {
            substantialRanked.filter { it.second >= maxScore * 0.70f }
        } else {
            substantialRanked
        }
        if (filteredRanked.isEmpty()) {
            return RagAnswer(NO_EVIDENCE_MESSAGE, emptyList(), AnswerMode.NO_EVIDENCE)
        }

        val references = filteredRanked.mapIndexed { index, match -> Evidence("S${index + 1}", match.third) }
        val topic = extractListingTopic(plan.normalizedRequest)
        val header = if (!topic.isNullOrBlank()) {
            "Here are related documents regarding $topic:"
        } else {
            "Here are related documents:"
        }
        val text = buildString {
            append(header)
            append("\n\n")
            filteredRanked.forEachIndexed { index, (document, _, hit) ->
                append("${index + 1}. ${document.title} — ${document.folderName} [S${index + 1}]\n")
            }
        }.trim()
        return RagAnswer(text, references, AnswerMode.EXCERPTS, matchedDocuments = filteredRanked.map { it.first })
    }

    private fun extractListingTopic(prompt: String): String? {
        val trimmed = prompt.trim().removeSuffix("?").removeSuffix(".").trim()
        val pattern1 = Regex(
            "^(?:please\\s+)?(?:list|show|find|get|display)\\s+(?:all\\s+|top\\s+\\d+\\s+|first\\s+\\d+\\s+)?(?:the\\s+)?(?:related\\s+)?(?:documents|files|docs)\\s+(?:that\\s+are\\s+|which\\s+are\\s+|related\\s+to|realted\\s+to|regarding|about|concerning|with|for|on|in)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        val match1 = pattern1.find(trimmed)
        if (match1 != null) {
            val candidate = match1.groupValues[1].trim().removeSuffix("?").removeSuffix(".").trim()
            if (candidate.isNotBlank()) return candidate
        }

        val pattern2 = Regex(
            "^(?:documents|files|docs)\\s+(?:related\\s+to|realted\\s+to|regarding|about|concerning|with|for|on)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        val match2 = pattern2.find(trimmed)
        if (match2 != null) {
            val candidate = match2.groupValues[1].trim().removeSuffix("?").removeSuffix(".").trim()
            if (candidate.isNotBlank()) return candidate
        }

        val pattern3 = Regex(
            "^(?:which|what)\\s+(?:documents|files|docs)\\s+(?:are\\s+)?(?:related\\s+to|realted\\s+to|regarding|about|concerning|with|for|on)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        val match3 = pattern3.find(trimmed)
        if (match3 != null) {
            val candidate = match3.groupValues[1].trim().removeSuffix("?").removeSuffix(".").trim()
            if (candidate.isNotBlank()) return candidate
        }

        val pattern4 = Regex(
            "^(.+?)\\s+(?:related\\s+)?(?:documents|files|docs)$",
            RegexOption.IGNORE_CASE
        )
        val match4 = pattern4.find(trimmed)
        if (match4 != null) {
            val candidate = match4.groupValues[1].trim()
            val candidateLower = candidate.lowercase(Locale.ROOT)
            val nonTopicPrefixes = listOf("list", "show", "find", "get", "display", "all", "the", "which", "what")
            if (candidate.isNotBlank() && nonTopicPrefixes.none { candidateLower == it || candidateLower.startsWith("$it ") }) {
                return candidate.removeSuffix("?").removeSuffix(".").trim()
            }
        }

        val terms = KeywordQuery.terms(trimmed)
        return if (terms.isNotEmpty()) terms.joinToString(" ") else null
    }

    private suspend fun lookupAnswer(plan: IntentPlan, generator: AnswerGenerator): RagAnswer {
        val chunks = index.search(plan.normalizedRequest, plan.scope, config.maxRetrievedChunks)
        val evidence = boundEvidence(chunks, config.maxContextChars)
        val terms = KeywordQuery.terms(plan.normalizedRequest)
        val hasKeywordHits = terms.isNotEmpty() && evidence.any {
            KeywordQuery.lexicalScore(it.chunk.text, it.chunk.title, terms) >= 0.15f
        }
        val isSubstantial = evidence.isNotEmpty() && (
            if (terms.isNotEmpty()) {
                if (hasKeywordHits) {
                    evidence.any { item ->
                        val lexScore = KeywordQuery.lexicalScore(item.chunk.text, item.chunk.title, terms)
                        lexScore >= 0.15f && item.chunk.similarity >= config.minimumVectorSimilarity
                    }
                } else {
                    evidence.any { it.chunk.similarity >= maxOf(config.minimumVectorSimilarity, 0.60f) }
                }
            } else {
                evidence.any { it.chunk.similarity >= maxOf(config.minimumVectorSimilarity, 0.60f) }
            }
        )
        if (!isSubstantial) {
            return RagAnswer(NO_EVIDENCE_MESSAGE, emptyList(), AnswerMode.NO_EVIDENCE)
        }

        val generated = generator.generate(plan.normalizedRequest, evidence, config)
        val lowerGen = generated.lowercase(Locale.ROOT)
        if (lowerGen.contains("can't find anything related") ||
            lowerGen.contains("could not find") ||
            lowerGen.contains("not found in your vault") ||
            lowerGen.contains("is insufficient to answer")
        ) {
            return RagAnswer(NO_EVIDENCE_MESSAGE, emptyList(), AnswerMode.NO_EVIDENCE)
        }

        val latest = source.snapshot().associateBy { it.id }
        val stale = evidence.any { item ->
            val document = latest[item.chunk.documentId]
            val page = document?.pages?.find { it.number == item.chunk.pageNumber }
            val searchable = page?.searchable(null).orEmpty()
            document == null || page == null || page.revision != item.chunk.revision ||
                searchable.substring(
                    item.chunk.startOffset.coerceAtMost(searchable.length),
                    item.chunk.endOffset.coerceAtMost(searchable.length),
                ) != item.chunk.text
        }
        if (stale) {
            return RagAnswer("Document text changed while answering. Please confirm the request again.", emptyList(), AnswerMode.NO_EVIDENCE)
        }
        val currentEvidence = evidence.map { item ->
            val document = latest.getValue(item.chunk.documentId)
            item.copy(chunk = item.chunk.copy(title = document.title, folderId = document.folderId))
        }
        val checked = CitationChecker.resolve(generated, currentEvidence)
        return if (checked == null) {
            if (generator.mode == AnswerMode.LOCAL_MODEL && generated.isNotBlank() && !generated.startsWith("I can't find anything related")) {
                RagAnswer(generated, currentEvidence.take(2), AnswerMode.LOCAL_MODEL)
            } else {
                val (excerptText, usedEvidence) = excerpts.generateWithEvidence(plan.normalizedRequest, currentEvidence, config)
                if (excerptText.startsWith("I can't find anything related") || usedEvidence.isEmpty()) {
                    RagAnswer(NO_EVIDENCE_MESSAGE, emptyList(), AnswerMode.NO_EVIDENCE)
                } else {
                    RagAnswer(
                        excerptText,
                        usedEvidence,
                        AnswerMode.EXCERPTS,
                        "The answer model was unavailable or returned invalid citations. Showing the retrieved semantic evidence.",
                    )
                }
            }
        } else {
            RagAnswer(generated, checked, generator.mode)
        }
    }

    private fun inScope(document: RagDocument, scope: SearchScope): Boolean = when (scope) {
        SearchScope.All -> true
        is SearchScope.Folder -> document.folderId == scope.id
        is SearchScope.Document -> document.id == scope.id
    }

    private fun boundEvidence(chunks: List<ReferenceChunk>, budget: Int): List<Evidence> = buildList {
        var remaining = budget
        chunks.forEach { chunk ->
            if (remaining > 0) {
                val text = chunk.text.take(remaining)
                if (text.isNotBlank()) {
                    add(Evidence("S${size + 1}", chunk.copy(text = text, endOffset = chunk.startOffset + text.length)))
                    remaining -= text.length
                }
            }
        }
    }

    suspend fun searchDocuments(query: String, scope: SearchScope = SearchScope.All, limit: Int = 30): List<RagSearchResult> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val documents = synchronize()
            val trimmed = query.trim()
            if (trimmed.isBlank() || documents.isEmpty()) return@withLock emptyList()
            val chunkHits = index.search(trimmed, scope, config.maxVectorCandidates)
            val documentsById = documents.associateBy { it.id }
            val terms = KeywordQuery.terms(trimmed)
            val hasKeywordHits = terms.isNotEmpty() && chunkHits.any {
                KeywordQuery.lexicalScore(it.text, it.title, terms) >= 0.15f
            }

            val matchedFromChunks = chunkHits
                .groupBy { it.documentId }
                .mapNotNull { (documentId, hits) ->
                    val document = documentsById[documentId] ?: return@mapNotNull null
                    val best = hits.maxOf { it.similarity }
                    val score = best + (ln(hits.size.toDouble() + 1.0) * 0.015).toFloat()
                    val bestHit = hits.maxBy { it.similarity }
                    Triple(document, score, bestHit)
                }
                .filter { match ->
                    if (terms.isNotEmpty()) {
                        val lexScore = KeywordQuery.lexicalScore(match.third.text, match.first.title, terms)
                        if (hasKeywordHits) {
                            lexScore >= 0.15f && match.second >= config.minimumVectorSimilarity
                        } else {
                            match.second >= maxOf(config.minimumVectorSimilarity, 0.60f)
                        }
                    } else {
                        match.second >= maxOf(config.minimumVectorSimilarity, 0.60f)
                    }
                }

            val chunkDocIds = matchedFromChunks.map { it.first.id }.toSet()
            val titleMatches = documents.filter { doc ->
                doc.id !in chunkDocIds && inScope(doc, scope) && (
                    doc.title.contains(trimmed, ignoreCase = true) ||
                    doc.folderName.contains(trimmed, ignoreCase = true) ||
                    (terms.isNotEmpty() && KeywordQuery.lexicalScore("", doc.title, terms) >= 0.20f)
                )
            }.map { doc ->
                Triple(doc, 0.75f, null as ReferenceChunk?)
            }

            val allRanked = (matchedFromChunks + titleMatches)
                .sortedByDescending { it.second }
                .distinctBy { doc ->
                    val contentKey = doc.first.pages.map { it.searchable(null).trim() }.joinToString(" ")
                    "${doc.first.title.trim().lowercase(Locale.ROOT)}::$contentKey"
                }
                .take(limit)

            allRanked.map { (doc, score, bestHit) ->
                val snippet = bestHit?.let { hit ->
                    val text = hit.text.trim().replace("\n", " ")
                    if (text.length > 120) text.take(117) + "..." else text
                }
                RagSearchResult(doc, score, bestHit, snippet)
            }
        }
    }

    suspend fun openSource(chunk: ReferenceChunk): SourcePage? = withContext(Dispatchers.IO) {
        val document = source.findDocument(chunk.documentId) ?: return@withContext null
        val page = document.pages.find { it.number == chunk.pageNumber }
        SourcePage(document, chunk.pageNumber, page, page == null || page.revision != chunk.revision)
    }
}

data class RagSearchResult(
    val document: RagDocument,
    val score: Float,
    val bestChunk: ReferenceChunk? = null,
    val matchingSnippet: String? = null
)

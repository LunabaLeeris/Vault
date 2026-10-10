package com.example.vault.rag

import com.example.vault.rag.index.KeywordQuery
import com.example.vault.rag.model.SearchScope
import com.example.vault.rag.model.RagDocument
import java.util.Locale

enum class DocumentIntent { LIST_DOCUMENTS, LOOKUP_FACT, SUMMARIZE, DOCUMENT_IRRELEVANT, UNSUPPORTED }
enum class IntentReviewState { NONE, PENDING, CONFIRMED, CANCELLED, COMPLETED }
data class IntentPlan(val intent: DocumentIntent, val confidence: Float, val normalizedRequest: String, val searchTerms: List<String> = emptyList(), val scope: SearchScope = SearchScope.All, val scopeLabel: String? = null, val clarification: String? = null, val resultLimit: Int = 10)

class IntentRouter(private val defaultListLimit: Int = 10) {

    fun classify(prompt: String, documents: List<com.example.vault.rag.model.RagDocument>): IntentPlan {
        val q = prompt.trim(); val lower = q.lowercase(Locale.ROOT)
        if (q.isBlank()) return IntentPlan(DocumentIntent.UNSUPPORTED, 1f, q, emptyList(), clarification = "Please describe which documents you want me to use.")
        val irrelevant = listOf("weather", "recipe", "joke", "bali", "sports", "music", "president of", "capital of", "who won", "celebrity").any { lower.contains(it) }
        if (irrelevant) return IntentPlan(DocumentIntent.DOCUMENT_IRRELEVANT, 0.98f, q, clarification = "I can only answer questions about documents stored in Vault.")
        if (listOf("summarize", "summary", "summarise", "overview").any { lower.contains(it) }) {
            return IntentPlan(DocumentIntent.UNSUPPORTED, 1f, q, clarification = "Summarization is disabled for now. I can semantically list related documents or look up facts from them.")
        }
        val kind = when {
            listOf("list", "show", "find all", "find documents", "find files", "which documents", "what documents", "documents related", "documents about", "identification card", "id card").any { lower.contains(it) } -> DocumentIntent.LIST_DOCUMENTS
            listOf("when", "what", "due", "deadline", "how", "who", "where", "why", "tell me", "define", "explain", "look up", "search for").any { lower.startsWith(it) || lower.contains(" $it ") } -> DocumentIntent.LOOKUP_FACT
            else -> DocumentIntent.UNSUPPORTED
        }
        val clarification = if (kind == DocumentIntent.UNSUPPORTED) "I can semantically list related documents or look up facts from them. Which would you like?" else null
        return planForIntent(kind, q, documents, if (kind == DocumentIntent.UNSUPPORTED) 0.35f else 0.85f, clarification)
    }

    fun planForIntent(intent: DocumentIntent, prompt: String, documents: List<RagDocument>): IntentPlan = planForIntent(intent, prompt.trim(), documents, 1f, null)

    private fun planForIntent(intent: DocumentIntent, prompt: String, documents: List<RagDocument>, confidence: Float, clarification: String?): IntentPlan {
        val folder = documents
            .filter { document -> Regex("\\b(?:folder|in)\\s+${Regex.escape(document.folderName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(prompt) }
            .maxByOrNull { it.folderName.length }
        val scope = folder?.let { SearchScope.Folder(it.folderId) } ?: SearchScope.All
        val requestedLimit = Regex("\\b(?:top|first|limit|show|list)\\s+(\\d{1,2})\\b", RegexOption.IGNORE_CASE)
            .find(prompt)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 50)
        val terms = KeywordQuery.terms(prompt)
        return IntentPlan(intent, confidence, prompt, terms, scope, folder?.folderName, clarification, requestedLimit ?: defaultListLimit)
    }
}

fun interface IntentModelClassifier { suspend fun classify(prompt: String, documents: List<RagDocument>): IntentPlan? }

class HybridIntentRouter(private val deterministic: IntentRouter, private val modelClassifier: IntentModelClassifier? = null) {
    fun planForIntent(intent: DocumentIntent, prompt: String, documents: List<RagDocument>): IntentPlan = deterministic.planForIntent(intent, prompt, documents)
    suspend fun classify(prompt: String, documents: List<RagDocument>): IntentPlan {
        val first = deterministic.classify(prompt, documents)
        if (first.intent != DocumentIntent.UNSUPPORTED || first.confidence >= 0.8f || modelClassifier == null) return first
        val classifier = modelClassifier ?: return first
        return runCatching { classifier.classify(prompt, documents) }.getOrNull()?.takeIf { it.confidence >= 0.55f && it.intent != DocumentIntent.SUMMARIZE } ?: first
    }
}

package com.example.vault.rag.generation

import com.example.vault.rag.RagConfig
import com.example.vault.rag.index.KeywordQuery
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.Evidence

/** Exact quotations, with no claim that these are model-generated answers. */
class ExcerptAnswerGenerator : AnswerGenerator {
    override val mode = AnswerMode.EXCERPTS

    override suspend fun generate(question: String, evidence: List<Evidence>, config: RagConfig): String {
        val terms = KeywordQuery.terms(question, config.maxQueryTerms)
        return "Relevant excerpts from your documents:\n\n" + evidence.joinToString("\n\n") { source ->
            val sentences = source.chunk.text.split(Regex("(?<=[.!?])\\s+|\\n+"))
                .filter { it.isNotBlank() }
            val best = sentences.maxByOrNull { KeywordQuery.score(it, "", terms) }
                ?: source.chunk.text
            "“${best.trim()}” [${source.sourceId}]"
        }
    }
}

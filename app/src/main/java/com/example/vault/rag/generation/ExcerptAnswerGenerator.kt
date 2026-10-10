package com.example.vault.rag.generation

import com.example.vault.rag.RagConfig
import com.example.vault.rag.index.KeywordQuery
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.Evidence

class ExcerptAnswerGenerator : AnswerGenerator {
    override val mode = AnswerMode.EXCERPTS

    override suspend fun generate(question: String, evidence: List<Evidence>, config: RagConfig): String {
        return generateWithEvidence(question, evidence, config).first
    }

    fun generateWithEvidence(question: String, evidence: List<Evidence>, config: RagConfig): Pair<String, List<Evidence>> {
        if (evidence.isEmpty()) {
            return Pair("I can't find anything related to that context, are you sure the question is correct or the intent you specified is accurate?", emptyList())
        }
        val terms = KeywordQuery.terms(question, config.maxQueryTerms)
        val validSources = evidence.filter { source ->
            if (terms.isNotEmpty()) {
                val lexScore = KeywordQuery.lexicalScore(source.chunk.text, source.chunk.title, terms)
                (lexScore >= 0.18f && source.chunk.similarity >= config.minimumVectorSimilarity) || source.chunk.similarity >= 0.65f
            } else {
                source.chunk.similarity >= maxOf(config.minimumVectorSimilarity, 0.65f)
            }
        }
        if (validSources.isEmpty()) {
            return Pair("I can't find anything related to that context, are you sure the question is correct or the intent you specified is accurate?", emptyList())
        }
        val text = "Relevant excerpts from your documents:\n\n" + validSources.joinToString("\n\n") { source ->
            val cleanText = source.chunk.text.trim()
            val formattedPassage = if (cleanText.length <= 360) {
                cleanText
            } else {
                val sentences = cleanText.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.isNotBlank() }
                val matchingSentences = sentences.filter { KeywordQuery.score(it, source.chunk.title, terms) > 0 }
                if (matchingSentences.isNotEmpty()) {
                    matchingSentences.take(3).joinToString(" ")
                } else {
                    cleanText.take(350).trimEnd() + "..."
                }
            }
            "“${formattedPassage.trim()}” [${source.sourceId}]"
        }
        return Pair(text, validSources)
    }
}

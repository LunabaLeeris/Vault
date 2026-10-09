package com.example.vault.rag.generation

import com.example.vault.rag.model.Evidence

object CitationChecker {
    private val marker = Regex("\\[s[^\\]\\r\\n]*\\]", RegexOption.IGNORE_CASE)

    /** Null means an unknown, malformed, or missing citation. Never create sources from model text. */
    fun resolve(answer: String, evidence: List<Evidence>): List<Evidence>? {
        val ids = marker.findAll(answer).map { it.value.removeSurrounding("[", "]") }.distinct().toList()
        if (answer.isBlank() || ids.isEmpty()) return null
        val allowed = evidence.associateBy { it.sourceId }
        if (ids.any { it !in allowed }) return null
        return ids.map { allowed.getValue(it) }
    }
}

package com.example.vault.rag.generation

import com.example.vault.rag.model.Evidence

object CitationChecker {
    private val ids = Regex("\\[(S\\d+)\\]")
    fun resolve(answer: String, evidence: List<Evidence>): List<Evidence>? {
        val byId = evidence.associateBy { it.sourceId }; val found = ids.findAll(answer).map { it.groupValues[1] }.distinct().toList()
        return if (found.isNotEmpty() && found.all { it in byId }) found.map { byId.getValue(it) } else null
    }
}

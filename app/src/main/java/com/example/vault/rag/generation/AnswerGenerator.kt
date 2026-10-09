package com.example.vault.rag.generation

import com.example.vault.rag.RagConfig
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.Evidence

interface AnswerGenerator {
    val mode: AnswerMode
    suspend fun generate(question: String, evidence: List<Evidence>, config: RagConfig): String
}

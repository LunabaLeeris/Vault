package com.example.vault.ui.chat

import com.example.vault.rag.model.Evidence
import com.example.vault.rag.model.RagAnswer
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.SearchScope
import com.example.vault.rag.model.SourcePage

data class ChatMessage(val id: Long, val question: String, val answer: RagAnswer? = null)
data class SourcePreviewState(val evidence: Evidence, val source: SourcePage)

data class ChatUiState(
    val draft: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val documents: List<RagDocument> = emptyList(),
    val scope: SearchScope = SearchScope.All,
    val isBusy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val canRetry: Boolean = false,
    val hasLocalModel: Boolean = false,
    val useLocalModel: Boolean = false,
    val preview: SourcePreviewState? = null,
)

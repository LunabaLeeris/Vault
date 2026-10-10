package com.example.vault.rag.model

data class TextPage(val number: Int, val text: String, val revision: Long, val isEdited: Boolean = false, val translatedText: String? = null, val translatedLanguage: String? = null) {
    init { require(number > 0 && revision > 0) }
    fun searchable(language: String?): String = if (!language.isNullOrBlank() && translatedLanguage.equals(language, true) && translatedText != null) translatedText else text + (translatedText?.let { "\n$it" } ?: "")
}

data class RagDocument(val id: String, val title: String, val folderId: String, val folderName: String, val pages: List<TextPage>) {
    init { require(id.isNotBlank() && title.isNotBlank() && folderId.isNotBlank()) }
}

sealed interface SearchScope { data object All : SearchScope; data class Folder(val id: String) : SearchScope; data class Document(val id: String) : SearchScope }
data class ReferenceChunk(val id: String, val documentId: String, val title: String, val folderId: String, val pageNumber: Int, val revision: Long, val text: String, val startOffset: Int, val endOffset: Int, val isEdited: Boolean, val similarity: Float = 0f)
data class Evidence(val sourceId: String, val chunk: ReferenceChunk)
data class SourceNavigationRequest(val documentId: String, val pageNumber: Int, val textRevision: Long, val isEdited: Boolean)
enum class AnswerMode { EXCERPTS, LOCAL_MODEL, NO_EVIDENCE }
data class RagAnswer(val text: String, val references: List<Evidence>, val mode: AnswerMode, val notice: String? = null, val matchedDocuments: List<RagDocument> = emptyList())
data class SourcePage(val document: RagDocument, val requestedPageNumber: Int, val page: TextPage?, val revisionChanged: Boolean)

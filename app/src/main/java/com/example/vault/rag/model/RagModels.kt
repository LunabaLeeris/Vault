package com.example.vault.rag.model

data class TextPage(
    val number: Int,
    val text: String,
    val revision: Long,
    val isEdited: Boolean = false,
) {
    init {
        require(number > 0 && revision > 0)
    }
}

data class RagDocument(
    val id: String,
    val title: String,
    val folderId: String,
    val folderName: String,
    val pages: List<TextPage>,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && folderId.isNotBlank())
        require(pages.map { it.number }.distinct().size == pages.size)
    }
}

sealed interface SearchScope {
    data object All : SearchScope
    data class Folder(val id: String) : SearchScope
    data class Document(val id: String) : SearchScope
}

data class ReferenceChunk(
    val id: String,
    val documentId: String,
    val title: String,
    val folderId: String,
    val pageNumber: Int,
    val revision: Long,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val isEdited: Boolean,
)

data class Evidence(val sourceId: String, val chunk: ReferenceChunk)

data class SourceNavigationRequest(
    val documentId: String,
    val pageNumber: Int,
    val textRevision: Long,
    val isEdited: Boolean,
)

enum class AnswerMode { EXCERPTS, LOCAL_MODEL, NO_EVIDENCE }

data class RagAnswer(
    val text: String,
    val references: List<Evidence>,
    val mode: AnswerMode,
    val notice: String? = null,
)

data class SourcePage(
    val document: RagDocument,
    val requestedPageNumber: Int,
    val page: TextPage?,
    val revisionChanged: Boolean,
)

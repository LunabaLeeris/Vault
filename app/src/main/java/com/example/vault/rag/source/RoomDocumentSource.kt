package com.example.vault.rag.source

import com.example.vault.data.repository.VaultRepository
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.TextPage

class RoomDocumentSource(private val repository: VaultRepository) : DocumentSource {
    override suspend fun snapshot(): List<RagDocument> = repository.readDocumentSnapshot().map { item ->
        RagDocument(item.document.id.toString(), item.document.title, item.folder.id.toString(), item.folder.name,
            item.pages.sortedBy { it.pageNumber }.map { page -> TextPage(page.pageNumber, page.content, item.document.textRevision, page.editedText != null, page.translatedText, page.translatedLanguage) })
    }
}

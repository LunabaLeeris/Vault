package com.example.vault.rag.source

import com.example.vault.data.repository.VaultRepository
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.TextPage

class RoomDocumentSource(private val repository: VaultRepository) : DocumentSource {
    override suspend fun snapshot(): List<RagDocument> = repository.readDocumentSnapshot().map { entry ->
        RagDocument(
            id = entry.document.id.toString(), title = entry.document.title,
            folderId = entry.folder.id.toString(), folderName = entry.folder.name,
            pages = entry.pages.sortedBy { it.pageNumber }.map { page ->
                TextPage(page.pageNumber, page.content, entry.document.textRevision, page.editedText != null)
            },
        )
    }
}

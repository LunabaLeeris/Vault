package com.example.vault.rag.source

import com.example.vault.rag.model.RagDocument

/** Return an immutable, consistent snapshot of current metadata and authoritative text. */
interface DocumentSource {
    suspend fun snapshot(): List<RagDocument>
    suspend fun findDocument(id: String): RagDocument? = snapshot().find { it.id == id }
}

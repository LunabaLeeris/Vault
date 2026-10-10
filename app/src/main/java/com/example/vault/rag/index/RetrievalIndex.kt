package com.example.vault.rag.index

import com.example.vault.rag.model.ReferenceChunk
import com.example.vault.rag.model.SearchScope

interface RetrievalIndex {
    suspend fun replace(chunks: List<ReferenceChunk>)
    suspend fun search(query: String, scope: SearchScope, limit: Int): List<ReferenceChunk>
}

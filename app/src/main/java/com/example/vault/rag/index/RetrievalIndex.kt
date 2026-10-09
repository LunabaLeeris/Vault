package com.example.vault.rag.index

import com.example.vault.rag.model.ReferenceChunk
import com.example.vault.rag.model.SearchScope

interface RetrievalIndex {
    /** Atomically replace the cache with current chunks, including removals. */
    suspend fun replace(chunks: List<ReferenceChunk>)
    suspend fun search(terms: List<String>, scope: SearchScope, limit: Int): List<ReferenceChunk>
}

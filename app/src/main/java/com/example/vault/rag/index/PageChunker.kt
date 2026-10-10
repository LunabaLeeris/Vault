package com.example.vault.rag.index

import com.example.vault.rag.RagConfig
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.ReferenceChunk

class PageChunker {
    fun chunk(document: RagDocument, config: RagConfig): List<ReferenceChunk> = document.pages.flatMap { page ->
        val text = page.searchable(null)
        buildList {
            var start = 0
            while (start < text.length) {
                var end = (start + config.chunkChars).coerceAtMost(text.length)
                if (end < text.length) page.searchable(null).lastIndexOf(' ', end - 1).takeIf { it > start + config.chunkChars / 2 }?.let { end = it }
                val part = text.substring(start, end)
                if (part.isNotBlank()) add(ReferenceChunk("${document.id}:${page.number}:${page.revision}:$start", document.id, document.title, document.folderId, page.number, page.revision, part, start, end, page.isEdited))
                if (end == text.length) break
                start = (end - config.overlapChars).coerceAtLeast(start + 1)
            }
        }
    }
}

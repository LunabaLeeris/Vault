package com.example.vault.rag.index

import com.example.vault.rag.RagConfig
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.ReferenceChunk

class PageChunker {
    fun chunk(document: RagDocument, config: RagConfig): List<ReferenceChunk> = document.pages.flatMap { page ->
        buildList {
            var start = 0
            while (start < page.text.length) {
                var end = (start + config.chunkChars).coerceAtMost(page.text.length)
                if (end < page.text.length) {
                    val boundary = page.text.lastIndexOf(' ', end - 1)
                    if (boundary > start + config.chunkChars / 2) end = boundary
                }
                val text = page.text.substring(start, end)
                if (text.isNotBlank()) add(ReferenceChunk(
                    id = "${document.id}:${page.number}:${page.revision}:$start",
                    documentId = document.id, title = document.title, folderId = document.folderId,
                    pageNumber = page.number, revision = page.revision, text = text,
                    startOffset = start, endOffset = end, isEdited = page.isEdited,
                ))
                if (end == page.text.length) break
                start = (end - config.overlapChars).coerceAtLeast(start + 1)
            }
        }
    }
}

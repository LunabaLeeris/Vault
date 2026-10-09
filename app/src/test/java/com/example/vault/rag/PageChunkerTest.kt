package com.example.vault.rag

import com.example.vault.rag.index.PageChunker
import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.TextPage
import org.junit.Assert.*
import org.junit.Test

class PageChunkerTest {
    @Test fun preservesPageRevisionOffsetsAndCompleteText() {
        val text = "Rent is due every month. ".repeat(60)
        val document = RagDocument("doc", "Lease", "home", "Home", listOf(
            TextPage(1, text, 3, true), TextPage(2, "Another page", 1), TextPage(3, "   ", 1)))
        val config = RagConfig(chunkChars = 90, overlapChars = 15)
        val chunks = PageChunker().chunk(document, config)
        val firstPage = chunks.filter { it.pageNumber == 1 }
        assertTrue(firstPage.size > 1)
        assertEquals(0, firstPage.first().startOffset)
        assertEquals(text.length, firstPage.last().endOffset)
        firstPage.forEach {
            assertEquals(text.substring(it.startOffset, it.endOffset), it.text)
            assertTrue(it.text.length <= config.chunkChars)
            assertEquals(3L, it.revision)
            assertTrue(it.isEdited)
        }
        firstPage.zipWithNext().forEach { (left, right) ->
            assertTrue(right.startOffset <= left.endOffset)
            assertTrue(right.startOffset > left.startOffset)
        }
        assertEquals("Another page", chunks.single { it.pageNumber == 2 }.text)
        assertFalse(chunks.any { it.pageNumber == 3 })
        assertEquals(chunks.size, chunks.map { it.id }.distinct().size)
    }

    @Test fun handlesUnbrokenTextAndRejectsInvalidOverlap() {
        val document = RagDocument("doc", "File", "folder", "Folder", listOf(TextPage(1, "x".repeat(100), 1)))
        val chunks = PageChunker().chunk(document, RagConfig(chunkChars = 10, overlapChars = 0))
        assertEquals(10, chunks.size)
        assertEquals("x".repeat(100), chunks.joinToString("") { it.text })
        assertThrows(IllegalArgumentException::class.java) { RagConfig(chunkChars = 10, overlapChars = 10) }
    }
}

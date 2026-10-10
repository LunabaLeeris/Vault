package com.example.vault.rag

import com.example.vault.rag.index.KeywordQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordQueryTest {

    @Test
    fun extractsCleanTermsAndRemovesStopWordsAndTypos() {
        val terms = KeywordQuery.terms("list all documents realted to gcash")
        assertEquals(listOf("gcash"), terms)
        assertEquals("gcash", KeywordQuery.cleanQuery("list all documents realted to gcash"))
    }

    @Test
    fun computesHighLexicalScoreForMatchingContent() {
        val terms = listOf("gcash")
        val score = KeywordQuery.lexicalScore("Paid via GCash mobile wallet 500 PHP", "Receipt", terms)
        assertTrue("Expected score > 0.8 but got $score", score > 0.8f)

        val zeroScore = KeywordQuery.lexicalScore("Standard residential lease agreement", "Lease Contract", terms)
        assertEquals(0f, zeroScore)
    }

    @Test
    fun titleMatchGivesBonus() {
        val terms = listOf("receipt")
        val titleScore = KeywordQuery.lexicalScore("Paid 500 PHP", "GCash Receipt", terms)
        assertTrue(titleScore > 0.8f)
    }

    @Test
    fun extractsMultipleEntities() {
        val terms = KeywordQuery.terms("When is my apartment lease contract due?")
        assertTrue(terms.contains("apartment"))
        assertTrue(terms.contains("lease"))
        assertTrue(terms.contains("contract"))
        assertTrue(terms.contains("due"))
    }
}

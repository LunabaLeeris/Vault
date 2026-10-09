package com.example.vault.rag

import com.example.vault.rag.index.KeywordQuery
import org.junit.Assert.*
import org.junit.Test

class KeywordQueryTest {
    @Test fun stripsSyntaxAndCommonQuestionWords() {
        val terms = KeywordQuery.terms("When is rent due? \" OR * : ( ) ' --", 16)
        assertEquals(listOf("rent", "due"), terms)
        assertEquals("\"rent\" OR \"due\"", KeywordQuery.matchExpression(terms))
        assertEquals(emptyList<String>(), KeywordQuery.terms("What is it?", 16))
    }

    @Test fun limitsAndDeduplicatesTermsAndRetainsUnicode() {
        assertEquals(listOf("rent", "due"), KeywordQuery.terms("RENT rent due monthly", 2))
        assertEquals(listOf("café", "租金"), KeywordQuery.terms("café 租金", 16))
        assertTrue(KeywordQuery.score("rent is due", "", listOf("rent", "due")) >
            KeywordQuery.score("rent", "", listOf("rent", "due")))
    }
}

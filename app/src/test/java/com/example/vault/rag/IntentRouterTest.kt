package com.example.vault.rag

import com.example.vault.rag.model.RagDocument
import com.example.vault.rag.model.SearchScope
import com.example.vault.rag.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {
    private val docs = listOf(
        RagDocument("1", "Identity card", "10", "Personal", listOf(TextPage(1, "Government identification card", 1))),
        RagDocument("2", "Mathematics assignment", "11", "School", listOf(TextPage(1, "Due date: January 20", 1))),
    )

    @Test fun classifiesListingAndKeepsSearchTerms() {
        val plan = IntentRouter().classify("List all documents that are identification cards", docs)
        assertEquals(DocumentIntent.LIST_DOCUMENTS, plan.intent)
        assertEquals("List all documents that are identification cards", plan.normalizedRequest)
    }

    @Test fun classifiesFactLookupAndFolderScope() {
        val plan = IntentRouter().classify("When is my mathematics assignment due in School", docs)
        assertEquals(DocumentIntent.LOOKUP_FACT, plan.intent)
        assertTrue(plan.scope is SearchScope.Folder)
    }

    @Test fun rejectsIrrelevantAndUnsupportedRequests() {
        assertEquals(DocumentIntent.DOCUMENT_IRRELEVANT, IntentRouter().classify("What is the weather today?", docs).intent)
        assertEquals(DocumentIntent.UNSUPPORTED, IntentRouter().classify("Delete my folder", docs).intent)
    }

    @Test fun summaryIsDisabledForNow() {
        val router = IntentRouter()
        assertEquals(DocumentIntent.UNSUPPORTED, router.classify("Summarize all folders", docs).intent)
        assertTrue(router.classify("Summarize folder School", docs).clarification!!.contains("disabled"))
    }

    @Test fun recognizesWhatAreAsFactLookup() {
        assertEquals(DocumentIntent.LOOKUP_FACT, IntentRouter().classify("What are Doppler studies?", docs).intent)
    }

    @Test fun parsesRequestedDocumentLimit() {
        assertEquals(5, IntentRouter().classify("List top 5 documents about payments", docs).resultLimit)
    }
}

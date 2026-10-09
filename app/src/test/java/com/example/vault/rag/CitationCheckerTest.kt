package com.example.vault.rag

import com.example.vault.rag.generation.CitationChecker
import com.example.vault.rag.model.Evidence
import com.example.vault.rag.model.ReferenceChunk
import org.junit.Assert.*
import org.junit.Test

class CitationCheckerTest {
    private val evidence = listOf(Evidence("S1", ReferenceChunk("c", "doc", "Lease", "home", 1, 1, "Rent", 0, 4, false)))

    @Test fun onlyResolvesApplicationAssignedSources() {
        assertEquals(evidence, CitationChecker.resolve("Rent is due. [S1] Another claim. [S1]", evidence))
        assertNull(CitationChecker.resolve("Rent is due. [S99]", evidence))
        assertNull(CitationChecker.resolve("Rent is due. [S1] [S99]", evidence))
        assertNull(CitationChecker.resolve("Rent is due.", evidence))
        assertNull(CitationChecker.resolve("Rent is due. [S1, S2]", evidence))
        assertNull(CitationChecker.resolve("Rent is due. [s1]", evidence))
    }
}

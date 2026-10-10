package com.example.vault.rag

import com.example.vault.rag.generation.CitationChecker
import com.example.vault.rag.model.Evidence
import com.example.vault.rag.model.ReferenceChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CitationCheckerTest {
    private val evidence = listOf(Evidence("S1", ReferenceChunk("1", "1", "Lease", "10", 1, 1, "Rent is due Friday.", 0, 20, false)))

    @Test fun acceptsOnlySuppliedIds() { assertEquals(evidence, CitationChecker.resolve("Rent is due Friday. [S1]", evidence)) }
    @Test fun rejectsInventedIds() { assertNull(CitationChecker.resolve("Rent is due Monday. [S99]", evidence)) }
}

package com.example.vault.rag

import com.example.vault.rag.index.VectorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMathTest {
    @Test fun cosineRanksAlignedVectorsAboveUnrelatedVectors() {
        val query = floatArrayOf(1f, 0f, 0f)
        assertEquals(1f, VectorMath.cosine(query, floatArrayOf(1f, 0f, 0f)), 0.0001f)
        assertEquals(0f, VectorMath.cosine(query, floatArrayOf(0f, 1f, 0f)), 0.0001f)
        assertTrue(VectorMath.cosine(query, floatArrayOf(0.8f, 0.2f, 0f)) > 0.9f)
    }
}

package com.example.vault.rag.index

import kotlin.math.sqrt

object VectorMath {
    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.isEmpty() || left.size != right.size) return -1f
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (index in left.indices) {
            val a = left[index].toDouble()
            val b = right[index].toDouble()
            dot += a * b
            leftNorm += a * a
            rightNorm += b * b
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) return -1f
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat()
    }
}

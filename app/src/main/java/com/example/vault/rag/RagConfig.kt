package com.example.vault.rag

data class RagConfig(
    val chunkChars: Int = 480, val overlapChars: Int = 80, val maxRetrievedChunks: Int = 8, val maxVectorCandidates: Int = 200, val maxContextChars: Int = 4200,
    val maxQuestionChars: Int = 1000, val maxQueryTerms: Int = 16, val modelContextTokens: Int = 2048, val maxOutputTokens: Int = 320,
    val enableThinking: Boolean = false, val temperature: Double = 0.2, val topK: Int = 10, val topP: Double = 0.9, val modelFileName: String = "chat.litertlm",
    val embeddingModelAssetName: String = "mobile_bert.tflite", val minimumVectorSimilarity: Float = 0.40f, val defaultListLimit: Int = 10,
) {
    init { require(chunkChars > 0 && overlapChars in 0 until chunkChars); require(maxRetrievedChunks > 0 && maxVectorCandidates >= maxRetrievedChunks && maxContextChars > 0); require(maxQuestionChars > 0 && maxQueryTerms > 0); require(modelContextTokens > maxOutputTokens); require(temperature >= 0 && topK > 0 && topP in 0.0..1.0); require(modelFileName.isNotBlank() && '/' !in modelFileName && '\\' !in modelFileName); require(embeddingModelAssetName.isNotBlank() && minimumVectorSimilarity in -1f..1f && defaultListLimit in 1..50) }
}

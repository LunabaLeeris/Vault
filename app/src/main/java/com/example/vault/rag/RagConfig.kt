package com.example.vault.rag

/** All tuning values live here. Pass a new value to the pipeline for a future settings screen. */
data class RagConfig(
    val chunkChars: Int = 700,
    val overlapChars: Int = 100,
    val maxRetrievedChunks: Int = 4,
    val maxContextChars: Int = 2_800,
    val maxQuestionChars: Int = 1_000,
    val maxQueryTerms: Int = 16,
    val modelContextTokens: Int = 2_048,
    val maxOutputTokens: Int = 256,
    val enableThinking: Boolean = false,
    val temperature: Double = 0.2,
    val topK: Int = 10,
    val topP: Double = 0.9,
    val modelFileName: String = "chat.litertlm",
) {
    init {
        require(chunkChars > 0)
        require(overlapChars in 0 until chunkChars)
        require(maxRetrievedChunks > 0 && maxContextChars > 0)
        require(maxQuestionChars > 0 && maxQueryTerms > 0)
        require(maxOutputTokens > 0 && modelContextTokens > maxOutputTokens)
        require(temperature >= 0 && topK > 0 && topP in 0.0..1.0)
        require(modelFileName.isNotBlank() && '/' !in modelFileName && '\\' !in modelFileName)
    }
}

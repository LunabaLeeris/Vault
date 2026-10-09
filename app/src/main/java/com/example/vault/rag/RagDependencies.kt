package com.example.vault.rag

import android.content.Context
import com.example.vault.rag.generation.AnswerGenerator
import com.example.vault.rag.generation.ExcerptAnswerGenerator
import com.example.vault.rag.generation.LiteRtAnswerGenerator
import com.example.vault.rag.index.RetrievalIndex
import com.example.vault.rag.index.SqliteRetrievalIndex
import com.example.vault.rag.source.AssetDocumentSource
import com.example.vault.rag.source.DocumentSource
import java.io.File

/** Manual composition root. Other milestones replace source and connect ChatRoute's callback. */
class RagDependencies(
    context: Context,
    source: DocumentSource = AssetDocumentSource(context.applicationContext.assets),
    index: RetrievalIndex = SqliteRetrievalIndex(context.applicationContext),
    val config: RagConfig = RagConfig(),
    val corpusLabel: String = "Sample documents",
) {
    private val modelDirectory = File(context.applicationContext.filesDir, "models")
    val pipeline = RagPipeline(source, index, config)
    val excerptGenerator: AnswerGenerator = ExcerptAnswerGenerator()
    val modelGenerator: AnswerGenerator = LiteRtAnswerGenerator(
        modelDirectory, File(context.applicationContext.cacheDir, "rag_model"),
    )

    fun hasLocalModel(): Boolean = File(modelDirectory, config.modelFileName).let { it.isFile && it.length() > 0 }
}

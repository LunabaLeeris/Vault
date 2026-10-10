package com.example.vault.rag

import android.content.Context
import com.example.vault.data.repository.VaultRepository
import com.example.vault.rag.generation.AnswerGenerator
import com.example.vault.rag.generation.ExcerptAnswerGenerator
import com.example.vault.rag.generation.LiteRtAnswerGenerator
import com.example.vault.rag.index.RetrievalIndex
import com.example.vault.rag.index.VectorRetrievalIndex
import com.example.vault.rag.source.DocumentSource
import com.example.vault.rag.source.RoomDocumentSource
import java.io.File

class RagDependencies(context: Context, source: DocumentSource = RoomDocumentSource(VaultRepository.getInstance(context)), val config: RagConfig = RagConfig(), private val index: RetrievalIndex = VectorRetrievalIndex(context.applicationContext, config.embeddingModelAssetName, config.minimumVectorSimilarity)) {
    private val modelDirectory = File(context.applicationContext.filesDir, "models")
    val pipeline = RagPipeline(source, index, config)
    val intentRouter = HybridIntentRouter(IntentRouter(config.defaultListLimit), LiteRtIntentClassifier(modelDirectory, File(context.applicationContext.cacheDir, "intent_model")))
    val excerptGenerator: AnswerGenerator = ExcerptAnswerGenerator()
    val modelGenerator: AnswerGenerator = LiteRtAnswerGenerator(modelDirectory, File(context.applicationContext.cacheDir, "rag_model"))
    fun hasLocalModel() = File(modelDirectory, config.modelFileName).let { it.isFile && it.length() > 0 }
    fun close() {
        (index as? AutoCloseable)?.close()
        (modelGenerator as? AutoCloseable)?.close()
    }
    companion object {
        @Volatile
        private var INSTANCE: RagDependencies? = null

        fun getInstance(context: Context): RagDependencies =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RagDependencies(context.applicationContext).also { INSTANCE = it }
            }
    }
}

package com.example.vault.rag.generation

import com.example.vault.rag.RagConfig
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.Evidence
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class LiteRtAnswerGenerator(
    private val modelDirectory: File,
    private val cacheDirectory: File
) : AnswerGenerator, AutoCloseable {
    override val mode = AnswerMode.LOCAL_MODEL
    private var cachedEngine: Engine? = null
    private var loadedModelPath: String? = null
    private val lock = Any()

    private fun getOrInitEngine(config: RagConfig): Engine {
        val model = File(modelDirectory, config.modelFileName)
        check(model.isFile && model.length() > 0) { "No local chat model is installed." }
        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "Cannot create model cache." }
        synchronized(lock) {
            val existing = cachedEngine
            if (existing != null && loadedModelPath == model.absolutePath) {
                return existing
            }
            existing?.close()
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            val newEngine = Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = config.modelContextTokens,
                    cacheDir = cacheDirectory.absolutePath
                )
            )
            newEngine.initialize()
            cachedEngine = newEngine
            loadedModelPath = model.absolutePath
            return newEngine
        }
    }

    override suspend fun generate(question: String, evidence: List<Evidence>, config: RagConfig): String = withContext(Dispatchers.IO) {
        val engine = getOrInitEngine(config)
        ensureActive()
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(EvidencePrompt.INSTRUCTION),
                samplerConfig = SamplerConfig(config.topK, config.topP, config.temperature),
                maxOutputToken = config.maxOutputTokens,
                thinkingConfig = ThinkingConfig(enableThinking = config.enableThinking),
                automaticToolCalling = false
            )
        ).use { conversation ->
            awaitAnswer(conversation, EvidencePrompt.build(question, evidence)).trim()
        }
    }

    private suspend fun awaitAnswer(conversation: Conversation, prompt: String): String = suspendCancellableCoroutine { continuation ->
        val response = StringBuilder()
        conversation.sendMessageAsync(prompt, object : MessageCallback {
            override fun onMessage(message: Message) { if (continuation.isActive) response.append(message.toString()) }
            override fun onDone() { if (continuation.isActive) continuation.resumeWith(Result.success(response.toString())) }
            override fun onError(throwable: Throwable) { if (continuation.isActive) continuation.resumeWith(Result.failure(throwable)) }
        })
        continuation.invokeOnCancellation { runCatching { if (conversation.isAlive) conversation.cancelProcess() } }
    }

    override fun close() {
        synchronized(lock) {
            cachedEngine?.close()
            cachedEngine = null
            loadedModelPath = null
        }
    }
}

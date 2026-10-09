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

class LiteRtAnswerGenerator(private val modelDirectory: File, private val cacheDirectory: File) : AnswerGenerator {
    override val mode = AnswerMode.LOCAL_MODEL

    override suspend fun generate(question: String, evidence: List<Evidence>, config: RagConfig): String =
        withContext(Dispatchers.IO) {
            val model = File(modelDirectory, config.modelFileName)
            check(model.isFile && model.length() > 0) { "No local chat model is installed." }
            check(cacheDirectory.isDirectory || cacheDirectory.mkdirs()) { "Cannot create the model cache." }
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            // Release native memory after each question. Disk cache helps subsequent loads.
            Engine(EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = config.modelContextTokens,
                cacheDir = cacheDirectory.absolutePath,
            )).use { engine ->
                engine.initialize()
                ensureActive()
                engine.createConversation(ConversationConfig(
                    systemInstruction = Contents.of(EvidencePrompt.INSTRUCTION),
                    samplerConfig = SamplerConfig(config.topK, config.topP, config.temperature),
                    maxOutputToken = config.maxOutputTokens,
                    thinkingConfig = ThinkingConfig(enableThinking = config.enableThinking),
                    automaticToolCalling = false,
                )).use { conversation ->
                    awaitAnswer(conversation, EvidencePrompt.build(question, evidence)).trim()
                }
            }
        }

    // The SDK's Flow wrapper has a SendChannel ABI mismatch with its declared coroutine dependency.
    // Use the supported native callback API and our own cancellable coroutine instead.
    private suspend fun awaitAnswer(conversation: Conversation, prompt: String): String =
        suspendCancellableCoroutine { continuation ->
            val response = StringBuilder()
            conversation.sendMessageAsync(prompt, object : MessageCallback {
                override fun onMessage(message: Message) {
                    if (continuation.isActive) response.append(message.toString())
                }

                override fun onDone() {
                    if (continuation.isActive) continuation.resumeWith(Result.success(response.toString()))
                }

                override fun onError(throwable: Throwable) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(throwable))
                }
            })
            continuation.invokeOnCancellation {
                // Completion and cancellation can race. Cleanup errors must not escape onto the caller's thread.
                runCatching { if (conversation.isAlive) conversation.cancelProcess() }
            }
        }
}

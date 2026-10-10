package com.example.vault.rag

import com.example.vault.rag.model.RagDocument
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
import org.json.JSONObject

class LiteRtIntentClassifier(private val modelDirectory: File, private val cacheDirectory: File) : IntentModelClassifier {
    override suspend fun classify(prompt: String, documents: List<RagDocument>): IntentPlan? = withContext(Dispatchers.IO) {
        val model = File(modelDirectory, "chat.litertlm"); if (!model.isFile || model.length() == 0L) return@withContext null
        check(cacheDirectory.isDirectory || cacheDirectory.mkdirs())
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), maxNumTokens = 1024, cacheDir = cacheDirectory.absolutePath)).use { engine ->
            engine.initialize(); ensureActive(); engine.createConversation(ConversationConfig(
                systemInstruction = Contents.of("Return JSON only with keys intent, confidence, normalizedRequest, folderName. intent must be LIST_DOCUMENTS, LOOKUP_FACT, DOCUMENT_IRRELEVANT, or UNSUPPORTED. Summarization is unsupported. Never answer the document question."),
                samplerConfig = SamplerConfig(5, 0.8, 0.0), maxOutputToken = 180, thinkingConfig = ThinkingConfig(enableThinking = false), automaticToolCalling = false
            )).use { conversation ->
                val raw = await(conversation, "USER REQUEST:\n$prompt\nFOLDERS:\n${documents.map { it.folderName }.distinct().joinToString(", ")}")
                parse(raw, prompt, documents)
            }
        }
    }
    private fun parse(raw: String, prompt: String, documents: List<RagDocument>): IntentPlan? = runCatching {
        val json = JSONObject(raw.substringAfter('{').substringBeforeLast('}') + "}")
        val intent = DocumentIntent.valueOf(json.getString("intent")); val folderName = json.optString("folderName").takeIf { it.isNotBlank() }; val folder = folderName?.let { name -> documents.firstOrNull { it.folderName.equals(name, true) } }
        IntentPlan(intent, json.optDouble("confidence", 0.6).toFloat().coerceIn(0f, 1f), json.optString("normalizedRequest", prompt), scope = folder?.let { com.example.vault.rag.model.SearchScope.Folder(it.folderId) } ?: com.example.vault.rag.model.SearchScope.All, scopeLabel = folder?.folderName)
    }.getOrNull()
    private suspend fun await(conversation: Conversation, prompt: String): String = suspendCancellableCoroutine { continuation ->
        val response = StringBuilder(); conversation.sendMessageAsync(prompt, object : MessageCallback { override fun onMessage(message: Message) { if (continuation.isActive) response.append(message.toString()) }; override fun onDone() { if (continuation.isActive) continuation.resumeWith(Result.success(response.toString())) }; override fun onError(throwable: Throwable) { if (continuation.isActive) continuation.resumeWith(Result.failure(throwable)) } }); continuation.invokeOnCancellation { runCatching { if (conversation.isAlive) conversation.cancelProcess() } }
    }
}

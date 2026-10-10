package com.example.vault.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.data.local.ChatStorage
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.repository.VaultRepository
import com.example.vault.rag.DocumentIntent
import com.example.vault.rag.IntentPlan
import com.example.vault.rag.IntentReviewState
import com.example.vault.rag.RagDependencies
import com.example.vault.rag.RagSyncCoordinator
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.RagAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

enum class MessageSender { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLiked: Boolean = false,
    val relatedDocs: List<DocumentEntity> = emptyList(),
    val intentPlan: IntentPlan? = null,
    val intentState: IntentReviewState = IntentReviewState.NONE,
    val groundedAnswer: RagAnswer? = null,
    val intent: DocumentIntent? = null,
)

data class ChatSession(val id: String = UUID.randomUUID().toString(), val title: String, val messages: List<ChatMessage>, val updatedAt: Long = System.currentTimeMillis())

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository.getInstance(application)
    private val dependencies = RagDependencies.getInstance(application)
    private val syncCoordinator = RagSyncCoordinator(repository, dependencies.pipeline, viewModelScope)
    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()
    private val _activeSessionId = MutableStateFlow("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()
    private val _selectedIntent = MutableStateFlow<DocumentIntent?>(null)
    val selectedIntent: StateFlow<DocumentIntent?> = _selectedIntent.asStateFlow()
    private val _userFeedbackEvent = MutableSharedFlow<String>()
    val userFeedbackEvent: SharedFlow<String> = _userFeedbackEvent.asSharedFlow()
    private val defaultGreeting = ChatMessage(
        sender = MessageSender.ASSISTANT,
        text = "Hello! Specify an intent below to search your Vault documents or find specific facts."
    )

    init {
        val (persisted, active) = ChatStorage.loadChatData(application)
        if (persisted.isEmpty()) {
            val session = ChatSession(title = "Vault Assistant", messages = listOf(defaultGreeting))
            _chatSessions.value = listOf(session)
            _activeSessionId.value = session.id
            _messages.value = session.messages
        } else {
            _chatSessions.value = persisted
            val target = active?.takeIf { id -> persisted.any { it.id == id } } ?: persisted.first().id
            _activeSessionId.value = target
            _messages.value = persisted.first { it.id == target }.messages
        }
        saveToStorage()
    }

    fun createNewChat() {
        val session = ChatSession(title = "New Chat", messages = listOf(defaultGreeting))
        _chatSessions.value = listOf(session) + _chatSessions.value
        _activeSessionId.value = session.id
        _messages.value = session.messages
        saveToStorage()
        emitToast("Created new chat")
    }

    fun selectChat(sessionId: String) {
        _chatSessions.value.find { it.id == sessionId }?.let {
            _activeSessionId.value = it.id
            _messages.value = it.messages
            saveToStorage()
        }
    }

    fun renameChat(sessionId: String, title: String) {
        val value = title.trim()
        if (value.isBlank()) return
        _chatSessions.value = _chatSessions.value.map { if (it.id == sessionId) it.copy(title = value) else it }
        saveToStorage()
        emitToast("Chat renamed")
    }

    fun deleteChat(sessionId: String) {
        val remaining = _chatSessions.value.filterNot { it.id == sessionId }
        val sessions = if (remaining.isEmpty()) listOf(ChatSession(title = "Vault Assistant", messages = listOf(defaultGreeting))) else remaining
        _chatSessions.value = sessions
        if (_activeSessionId.value == sessionId || remaining.isEmpty()) {
            _activeSessionId.value = sessions.first().id
            _messages.value = sessions.first().messages
        }
        saveToStorage()
        emitToast("Chat deleted")
    }

    fun setSelectedIntent(intent: DocumentIntent?) {
        _selectedIntent.value = intent
    }

    fun sendMessage(query: String, intent: DocumentIntent = _selectedIntent.value ?: DocumentIntent.LOOKUP_FACT) {
        val prompt = query.trim()
        if (prompt.isBlank() || _isThinking.value) return
        val plan = IntentPlan(intent = intent, confidence = 1f, normalizedRequest = prompt)
        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = prompt,
            timestamp = System.currentTimeMillis(),
            intentPlan = plan,
            intent = intent
        )
        syncActiveSessionMessages(_messages.value + userMessage, prompt)
        _selectedIntent.value = null

        viewModelScope.launch {
            _isThinking.value = true
            try {
                val documents = dependencies.pipeline.refresh()
                val fullPlan = dependencies.intentRouter.planForIntent(intent, prompt, documents)
                val hasModel = withContext(Dispatchers.IO) { dependencies.hasLocalModel() }
                val generator = if (hasModel) dependencies.modelGenerator else dependencies.excerptGenerator
                val result = runCatching { dependencies.pipeline.execute(fullPlan, generator) }.getOrElse {
                    dependencies.pipeline.execute(fullPlan, dependencies.excerptGenerator).copy(
                        notice = "The local answer model was unavailable. Showing retrieved semantic evidence instead."
                    )
                }
                val isNoEvidence = result.mode == AnswerMode.NO_EVIDENCE ||
                    result.text.startsWith("I can't find anything related") ||
                    result.text.startsWith("No documents are available") ||
                    result.text.startsWith("No extracted or translated text")
                val docs = withContext(Dispatchers.IO) { repository.getAllDocuments().associateBy { it.id } }
                val related = if (isNoEvidence) {
                    emptyList()
                } else {
                    val ids = (result.matchedDocuments.mapNotNull { it.id.toLongOrNull() } + result.references.mapNotNull { it.chunk.documentId.toLongOrNull() }).distinct()
                    ids.mapNotNull { docs[it] }.distinctBy { it.id }
                }
                val response = ChatMessage(
                    sender = MessageSender.ASSISTANT,   
                    text = result.text,
                    relatedDocs = related,
                    groundedAnswer = result,
                    intentPlan = fullPlan,
                    intentState = IntentReviewState.COMPLETED
                )
                syncActiveSessionMessages(_messages.value + response)
            } catch (_: Exception) {
                syncActiveSessionMessages(
                    _messages.value + ChatMessage(sender = MessageSender.ASSISTANT, text = "The document search failed. Please try again.")
                )
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun toggleLike(messageId: String) {
        val updated = _messages.value.map { if (it.id == messageId) it.copy(isLiked = !it.isLiked) else it }
        syncActiveSessionMessages(updated)
        emitToast("Feedback updated")
    }

    fun regenerateLastMessage() {
        val lastUserMsg = _messages.value.lastOrNull { it.sender == MessageSender.USER } ?: return
        val intent = lastUserMsg.intent ?: lastUserMsg.intentPlan?.intent ?: DocumentIntent.LOOKUP_FACT
        sendMessage(lastUserMsg.text, intent)
    }

    fun clearChat() {
        syncActiveSessionMessages(listOf(defaultGreeting))
    }

    fun emitToast(message: String) {
        viewModelScope.launch { _userFeedbackEvent.emit(message) }
    }

    private fun syncActiveSessionMessages(messages: List<ChatMessage>, userPrompt: String? = null) {
        _messages.value = messages
        val activeId = _activeSessionId.value
        _chatSessions.value = _chatSessions.value.map { session ->
            if (session.id == activeId) session.copy(
                title = if ((session.title == "New Chat" || session.title == "Vault Assistant") && !userPrompt.isNullOrBlank()) userPrompt.take(28) else session.title,
                messages = messages,
                updatedAt = System.currentTimeMillis()
            ) else session
        }
        saveToStorage()
    }

    private fun saveToStorage() {
        val sessions = _chatSessions.value
        val active = _activeSessionId.value
        viewModelScope.launch(Dispatchers.IO) { ChatStorage.saveChatData(getApplication(), sessions, active) }
    }

    override fun onCleared() {
        syncCoordinator.close()
        dependencies.close()
        super.onCleared()
    }
}

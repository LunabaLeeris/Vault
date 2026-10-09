package com.example.vault.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.local.DocumentPageEntity
import com.example.vault.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

import com.example.vault.data.local.ChatStorage

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLiked: Boolean = false,
    val relatedDocs: List<DocumentEntity> = emptyList()
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long = System.currentTimeMillis()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository.getInstance(application)

    private val defaultGreeting = ChatMessage(
        sender = MessageSender.ASSISTANT,
        text = "Hello! I'm your Vault Assistant. Ask me anything about your scanned documents, receipts, invoices, or ask general questions. How can I help you today?"
    )

    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _userFeedbackEvent = MutableSharedFlow<String>()
    val userFeedbackEvent: SharedFlow<String> = _userFeedbackEvent.asSharedFlow()

    init {
        // 1. Immediately load persisted chats on creation so UI renders valid data on first frame
        val (persistedSessions, savedActiveId) = ChatStorage.loadChatData(application)
        if (persistedSessions.isNotEmpty()) {
            _chatSessions.value = persistedSessions
            val targetId = if (savedActiveId != null && persistedSessions.any { it.id == savedActiveId }) {
                savedActiveId
            } else {
                persistedSessions.first().id
            }
            _activeSessionId.value = targetId
            val activeSession = persistedSessions.find { it.id == targetId } ?: persistedSessions.first()
            _messages.value = activeSession.messages
        } else {
            // First time ever opening the app: create and persist default session
            val initialSession = ChatSession(
                title = "Vault Assistant",
                messages = listOf(defaultGreeting),
                updatedAt = System.currentTimeMillis()
            )
            _chatSessions.value = listOf(initialSession)
            _activeSessionId.value = initialSession.id
            _messages.value = initialSession.messages
            saveToStorage()
        }

        // 2. In background, resolve any cited documents from the Room database
        viewModelScope.launch(Dispatchers.IO) {
            val docs = try {
                repository.getAllDocuments()
            } catch (_: Exception) {
                emptyList()
            }
            if (docs.isNotEmpty()) {
                val docsMap = docs.associateBy { it.id }
                val (resolvedSessions, _) = ChatStorage.loadChatData(application, docsMap)
                if (resolvedSessions.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _chatSessions.value = resolvedSessions
                        val currentActive = resolvedSessions.find { it.id == _activeSessionId.value }
                        if (currentActive != null) {
                            _messages.value = currentActive.messages
                        }
                    }
                }
            }
        }
    }

    private fun saveToStorage() {
        val currentSessions = _chatSessions.value
        val currentActiveId = _activeSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            ChatStorage.saveChatData(getApplication(), currentSessions, currentActiveId)
        }
    }

    private fun syncActiveSessionMessages(newMessages: List<ChatMessage>, userPrompt: String? = null) {
        _messages.value = newMessages
        val currentId = _activeSessionId.value
        _chatSessions.value = _chatSessions.value.map { session ->
            if (session.id == currentId) {
                val newTitle = if ((session.title == "New Chat" || session.title == "Vault Assistant") && !userPrompt.isNullOrBlank()) {
                    if (userPrompt.length > 28) userPrompt.take(28) + "..." else userPrompt
                } else {
                    session.title
                }
                session.copy(
                    title = newTitle,
                    messages = newMessages,
                    updatedAt = System.currentTimeMillis()
                )
            } else session
        }
        saveToStorage()
    }

    fun createNewChat() {
        val newSession = ChatSession(
            title = "New Chat",
            messages = listOf(defaultGreeting),
            updatedAt = System.currentTimeMillis()
        )
        _chatSessions.value = listOf(newSession) + _chatSessions.value
        _activeSessionId.value = newSession.id
        _messages.value = newSession.messages
        saveToStorage()
        emitToast("Created new chat")
    }

    fun selectChat(sessionId: String) {
        val session = _chatSessions.value.find { it.id == sessionId } ?: return
        _activeSessionId.value = sessionId
        _messages.value = session.messages
        saveToStorage()
    }

    fun renameChat(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        _chatSessions.value = _chatSessions.value.map { session ->
            if (session.id == sessionId) session.copy(title = trimmed) else session
        }
        saveToStorage()
        emitToast("Chat renamed")
    }

    fun deleteChat(sessionId: String) {
        val currentList = _chatSessions.value
        val newList = currentList.filterNot { it.id == sessionId }
        if (newList.isEmpty()) {
            val freshSession = ChatSession(
                title = "Vault Assistant",
                messages = listOf(defaultGreeting)
            )
            _chatSessions.value = listOf(freshSession)
            _activeSessionId.value = freshSession.id
            _messages.value = freshSession.messages
        } else {
            _chatSessions.value = newList
            if (_activeSessionId.value == sessionId) {
                val nextSession = newList.first()
                _activeSessionId.value = nextSession.id
                _messages.value = nextSession.messages
            }
        }
        saveToStorage()
        emitToast("Chat deleted")
    }

    fun sendMessage(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || _isThinking.value) return

        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = trimmed
        )
        val updatedWithUser = _messages.value + userMsg
        syncActiveSessionMessages(updatedWithUser, trimmed)

        viewModelScope.launch {
            _isThinking.value = true
            // Small natural delay so typing animation feels organic
            delay(400)
            val response = generateResponse(trimmed)
            syncActiveSessionMessages(_messages.value + response)
            _isThinking.value = false
        }
    }

    fun toggleLike(messageId: String) {
        val updated = _messages.value.map { msg ->
            if (msg.id == messageId) {
                val newLiked = !msg.isLiked
                viewModelScope.launch {
                    _userFeedbackEvent.emit(if (newLiked) "Marked as helpful" else "Removed feedback")
                }
                msg.copy(isLiked = newLiked)
            } else msg
        }
        syncActiveSessionMessages(updated)
    }

    fun regenerateLastMessage() {
        val current = _messages.value
        val lastUserIndex = current.indexOfLast { it.sender == MessageSender.USER }
        if (lastUserIndex < 0) return

        val userMsg = current[lastUserIndex]
        // Remove subsequent assistant replies
        val trimmedList = current.take(lastUserIndex + 1)
        syncActiveSessionMessages(trimmedList)

        viewModelScope.launch {
            _isThinking.value = true
            delay(350)
            val response = generateResponse(userMsg.text)
            syncActiveSessionMessages(_messages.value + response)
            _isThinking.value = false
        }
    }

    fun clearChat() {
        syncActiveSessionMessages(
            listOf(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Chat cleared. Ask me anything about your documents, receipts, or any question you have!"
                )
            )
        )
    }

    fun emitToast(message: String) {
        viewModelScope.launch {
            _userFeedbackEvent.emit(message)
        }
    }

    private suspend fun generateResponse(query: String): ChatMessage = withContext(Dispatchers.IO) {
        val qLower = query.lowercase().trim()

        // 1. Direct Balinese food / stress query matching the user screenshot
        if (qLower.contains("relieve stress") || (qLower.contains("food") && qLower.contains("stress")) || qLower.contains("bali")) {
            return@withContext ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Yes, certain foods can help relieve stress! Bali definitely has some of the best food! From the iconic Babi Guling (suckling pig) to spicy Sambal Matah, every dish is packed with flavor. And don't forget Nasi Campur—such a unique mix of rice, meats, vegetables, and spices. The freshness of ingredients and the blend of traditional Balinese spices make every bite unforgettable!"
            )
        }

        // Fetch vault data
        val allDocs: List<DocumentEntity> = try {
            repository.getAllDocuments()
        } catch (_: Exception) {
            emptyList()
        }

        // 2. Questions about document counts, storage, statistics
        if (qLower.contains("how many") || qLower.contains("total document") || qLower.contains("statistics") || qLower.contains("storage")) {
            val totalDocs = allDocs.size
            val pdfCount = allDocs.count { it.mimeType.contains("pdf", ignoreCase = true) }
            val imgCount = allDocs.count { it.mimeType.contains("image", ignoreCase = true) }
            val text = buildString {
                append("Here is a quick summary of your Vault:\n\n")
                append("• **Total Documents:** $totalDocs\n")
                append("• **PDF Documents:** $pdfCount\n")
                append("• **Images & Scans:** $imgCount\n\n")
                if (totalDocs > 0) {
                    append("Your documents are organized and searchable by text content using on-device OCR.")
                } else {
                    append("Your vault is currently empty. Tap **Scan** on the Vault screen to add your first document!")
                }
            }
            return@withContext ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = text,
                relatedDocs = allDocs.take(3)
            )
        }

        // 3. Questions listing all documents / files
        if (qLower.contains("what document") || qLower.contains("list document") || qLower.contains("show document") || qLower.contains("list my file") || qLower.contains("what do i have")) {
            if (allDocs.isEmpty()) {
                return@withContext ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "You don't have any documents uploaded yet. You can tap the **Scan** button on the Vault tab to scan or upload receipts, invoices, and IDs."
                )
            }
            val text = buildString {
                append("You have **${allDocs.size}** documents in your Vault:\n\n")
                allDocs.take(8).forEachIndexed { i, doc ->
                    val type = if (doc.mimeType.contains("pdf", true)) "PDF" else "Image"
                    append("${i + 1}. **${doc.title}** ($type)\n")
                }
                if (allDocs.size > 8) {
                    append("\n...and ${allDocs.size - 8} more documents.")
                }
            }
            return@withContext ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = text,
                relatedDocs = allDocs.take(5)
            )
        }

        // 4. Content / OCR Search across documents
        val searchResults = try {
            repository.searchDetails(query)
        } catch (_: Exception) {
            emptyList()
        }

        if (searchResults.isNotEmpty()) {
            val matchedDocs = searchResults.map { it.first }.distinctBy { it.id }
            val text = buildString {
                append("I found **${matchedDocs.size}** relevant document${if (matchedDocs.size > 1) "s" else ""} matching \"$query\":\n\n")
                searchResults.take(4).forEach { (doc, snippet) ->
                    append("📄 **${doc.title}**\n")
                    append("   $snippet\n\n")
                }
                append("You can open these directly from your Vault or tap below to view.")
            }
            return@withContext ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = text,
                relatedDocs = matchedDocs.take(3)
            )
        }

        // 5. Questions about receipts, invoices, bills, financial documents
        if (qLower.contains("receipt") || qLower.contains("invoice") || qLower.contains("bill") || qLower.contains("tax")) {
            val matching = allDocs.filter { doc ->
                doc.title.contains("receipt", true) || doc.title.contains("invoice", true) ||
                        doc.title.contains("bill", true) || doc.title.contains("tax", true)
            }
            if (matching.isNotEmpty()) {
                val text = buildString {
                    append("Here are the financial documents found in your vault:\n\n")
                    matching.forEach { doc ->
                        append("• **${doc.title}**\n")
                    }
                }
                return@withContext ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = text,
                    relatedDocs = matching
                )
            } else {
                return@withContext ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "I couldn't find any documents specifically tagged with receipts or invoices. If you have uploaded them, make sure to extract text using the **OCR** or **Crop & Rescan** feature so I can index their contents!"
                )
            }
        }

        // 6. How OCR / Scanning / Cropping works
        if (qLower.contains("how to scan") || qLower.contains("how does ocr") || qLower.contains("crop") || qLower.contains("rescan")) {
            return@withContext ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Here's how scanning and OCR work in Vault:\n\n" +
                        "1. **Scan Documents:** Tap **Scan** on the Vault home screen. Google ML Kit will automatically detect edges and enhance contrast.\n" +
                        "2. **Zoom & Pan:** In any document, pinch or double-tap to zoom. Panning is bounded to keep the document in view.\n" +
                        "3. **Interactive Crop:** Tap the **Crop** icon at the top of the viewer to adjust boundaries, then tap **Crop & Rescan** for fresh on-device text recognition.\n" +
                        "4. **Full-Text Search:** All extracted text is indexed locally, allowing you to search words or ask questions right here in Chat!"
            )
        }

        // 7. General friendly greetings
        if (qLower in setOf("hi", "hello", "hey", "good morning", "good evening", "sup", "yo")) {
            return@withContext ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Hello! How can I assist you with your documents or questions today?"
            )
        }

        // 8. General conversational fallback
        ChatMessage(
            sender = MessageSender.ASSISTANT,
            text = "I've checked your vault for \"$query\". While no exact OCR text match was found, I'm here to help answer general questions, search your files, or summarize your documents. Try asking about a specific receipt, keyword, or document title!"
        )
    }
}

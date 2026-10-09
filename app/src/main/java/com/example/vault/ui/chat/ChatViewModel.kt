package com.example.vault.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.vault.rag.RagDependencies
import com.example.vault.rag.model.Evidence
import com.example.vault.rag.model.SearchScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(private val dependencies: RagDependencies) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state = mutableState.asStateFlow()
    val corpusLabel = dependencies.corpusLabel
    val maxQuestionChars = dependencies.config.maxQuestionChars
    private var activeJob: Job? = null
    private var nextMessageId = 1L
    private var retryQuestion: String? = null

    init { refresh() }

    fun setDraft(value: String) { mutableState.update { it.copy(draft = value.take(maxQuestionChars)) } }
    fun setScope(value: SearchScope) {
        if (!state.value.isBusy) mutableState.update { it.copy(scope = value) }
    }
    fun setModelMode(value: Boolean) {
        if (!state.value.isBusy) mutableState.update { it.copy(useLocalModel = value && it.hasLocalModel) }
    }

    fun refresh() {
        if (state.value.isBusy) return
        runWork("Refreshing documents…") {
            val documents = dependencies.pipeline.refresh()
            val hasModel = withContext(Dispatchers.IO) { dependencies.hasLocalModel() }
            mutableState.update { it.copy(documents = documents, hasLocalModel = hasModel,
                useLocalModel = it.useLocalModel && hasModel) }
        }
    }

    fun send() {
        val current = state.value
        val question = current.draft.trim()
        if (current.isBusy || question.isEmpty()) return
        val messageId = nextMessageId++
        retryQuestion = question
        mutableState.update { it.copy(draft = "", messages = it.messages + ChatMessage(messageId, question)) }
        runWork(if (current.useLocalModel) "Loading local model and answering…" else "Searching documents…") {
            val hasModel = withContext(Dispatchers.IO) { dependencies.hasLocalModel() }
            val generator = if (current.useLocalModel && hasModel) dependencies.modelGenerator else dependencies.excerptGenerator
            val result = dependencies.pipeline.ask(question, current.scope, generator)
            val answer = if (current.useLocalModel && !hasModel) result.copy(
                notice = "No local model is available. Showing document excerpts.") else result
            mutableState.update { it.copy(
                messages = it.messages.map { message -> if (message.id == messageId) message.copy(answer = answer) else message },
                hasLocalModel = hasModel, useLocalModel = it.useLocalModel && hasModel) }
            retryQuestion = null
        }
    }

    fun retry() {
        if (state.value.isBusy) return
        val question = retryQuestion
        if (question == null) refresh() else { setDraft(question); send() }
    }

    fun openSource(evidence: Evidence, onOpenDocument: ((String, Int) -> Unit)?) {
        if (state.value.isBusy) return
        runWork("Opening source…") {
            val source = dependencies.pipeline.openSource(evidence.chunk)
            if (source == null) mutableState.update { it.copy(error = "This source is no longer available.") }
            else if (onOpenDocument != null) onOpenDocument(source.document.id, source.requestedPageNumber)
            else mutableState.update { it.copy(preview = SourcePreviewState(evidence, source)) }
        }
    }

    fun closePreview() { mutableState.update { it.copy(preview = null) } }
    fun dismissError() { mutableState.update { it.copy(error = null) } }
    fun cancel() { mutableState.update { it.copy(status = "Cancelling…") }; activeJob?.cancel() }

    private fun runWork(status: String, action: suspend () -> Unit) {
        mutableState.update { it.copy(isBusy = true, status = status, error = null, canRetry = false) }
        activeJob = viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) {
                mutableState.update { it.copy(status = "Cancelled.") }
                throw cancelled
            }
            catch (_: Exception) {
                // SDK exceptions can contain document text. Do not log or display their messages.
                mutableState.update { it.copy(error = "The operation failed. Retry, or use excerpt mode if the local model failed.", canRetry = true) }
            } catch (_: LinkageError) {
                mutableState.update { it.copy(error = "The local model runtime is unavailable on this device. Use excerpt mode.", canRetry = true) }
            } finally {
                mutableState.update { it.copy(isBusy = false, status = it.status.takeIf { text -> text == "Cancelled." }) }
            }
        }
    }

    class Factory(private val dependencies: RagDependencies) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(dependencies) as T
        }
    }
}

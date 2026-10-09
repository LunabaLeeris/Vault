package com.example.vault.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.local.DocumentPageEntity
import com.example.vault.data.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentTextUiState(
    val document: DocumentEntity? = null,
    val pages: List<DocumentPageEntity> = emptyList(),
    val isExtracting: Boolean = false,
    val extractionProgress: String = "",
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class DocumentTextViewModel(
    application: Application,
    private val documentId: Long
) : AndroidViewModel(application) {

    private val repository = VaultRepository.getInstance(application)

    private val _uiState = MutableStateFlow(DocumentTextUiState())
    val uiState: StateFlow<DocumentTextUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            repository.getDocumentById(documentId).collect { doc ->
                _uiState.update { it.copy(document = doc) }
            }
        }
        viewModelScope.launch {
            repository.getPagesForDocument(documentId).collect { pageList ->
                _uiState.update { it.copy(pages = pageList) }
            }
        }
    }

    fun extractText() {
        _uiState.update {
            it.copy(
                isExtracting = true,
                extractionProgress = "Reading document...",
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = repository.extractAndSaveTextForDocument(
                documentId = documentId,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(extractionProgress = "Extracting page $current of $total...")
                    }
                }
            )
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        userMessage = "Text extracted successfully"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isExtracting = false,
                        errorMessage = err.message ?: "Text extraction failed"
                    )
                }
            }
        }
    }

    fun updatePageText(pageId: Long, newText: String) {
        viewModelScope.launch {
            val result = repository.updatePageText(pageId, newText)
            result.onSuccess {
                _uiState.update { it.copy(userMessage = "Text updated") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to update text") }
            }
        }
    }

    fun deleteExtractedText() {
        viewModelScope.launch {
            val result = repository.deleteExtractedText(documentId)
            result.onSuccess {
                _uiState.update { it.copy(userMessage = "Extracted text deleted") }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to delete text") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}

package com.example.vault.ui.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.local.DocumentWithTextStatus
import com.example.vault.data.local.FolderEntity
import com.example.vault.data.repository.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderDetailViewModel(
    application: Application,
    val folderId: Long
) : AndroidViewModel(application) {

    private val repository = VaultRepository.getInstance(application)

    val folder: StateFlow<FolderEntity?> = repository.getFolderById(folderId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val documents: StateFlow<List<DocumentWithTextStatus>> = repository.getDocumentsWithTextStatusInFolder(folderId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _extractingDocumentIds = MutableStateFlow<Set<Long>>(emptySet())
    val extractingDocumentIds: StateFlow<Set<Long>> = _extractingDocumentIds.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun extractText(documentId: Long) {
        viewModelScope.launch {
            _extractingDocumentIds.value = _extractingDocumentIds.value + documentId
            _userMessage.value = "Extracting text with OCR..."
            val result = repository.extractAndSaveTextForDocument(documentId)
            result.onSuccess { pages ->
                _userMessage.value = "Text extracted (${pages.size} page(s))"
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to extract text"
            }
            _extractingDocumentIds.value = _extractingDocumentIds.value - documentId
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch {
            val result = repository.renameDocument(documentId, newTitle)
            result.onSuccess {
                _userMessage.value = "Document renamed"
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to rename document"
            }
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            val result = repository.deleteDocument(documentId)
            result.onSuccess {
                _userMessage.value = "Document deleted"
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to delete document"
            }
        }
    }

    fun renameFolder(newName: String) {
        viewModelScope.launch {
            val result = repository.renameFolder(folderId, newName)
            result.onSuccess {
                _userMessage.value = "Folder renamed"
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to rename folder"
            }
        }
    }

    fun deleteFolder(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteFolder(folderId)
            result.onSuccess {
                _userMessage.value = "Folder deleted"
                onDeleted()
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to delete folder"
            }
        }
    }
}

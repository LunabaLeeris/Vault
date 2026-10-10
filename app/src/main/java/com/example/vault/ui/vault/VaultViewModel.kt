package com.example.vault.ui.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.local.FolderEntity
import com.example.vault.data.local.FolderWithCount
import com.example.vault.data.repository.PendingDocument
import com.example.vault.data.repository.VaultRepository
import com.example.vault.rag.RagDependencies
import com.example.vault.rag.RagSyncCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultUiState(
    val folders: List<FolderWithCount> = emptyList(),
    val isLoading: Boolean = false,
    val userMessage: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    val repository: VaultRepository = VaultRepository.getInstance(application)
    private val dependencies = RagDependencies.getInstance(application)
    private val syncCoordinator = RagSyncCoordinator(repository, dependencies.pipeline, viewModelScope)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _ragSearchResults = MutableStateFlow<List<Pair<DocumentEntity, String?>>>(emptyList())
    val ragSearchResults: StateFlow<List<Pair<DocumentEntity, String?>>> = _ragSearchResults.asStateFlow()

    private var searchJob: Job? = null

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val foldersWithCount: StateFlow<List<FolderWithCount>> = repository.getFoldersWithCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allFolders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentDocuments: StateFlow<List<DocumentEntity>> = repository.getRecentDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.ensureDefaultFolderExists()
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _ragSearchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(150)
            try {
                val results = dependencies.pipeline.searchDocuments(query.trim())
                val allDocs: Map<String, DocumentEntity> = repository.getAllDocumentsSync().associateBy { it.id.toString() }
                val mapped: List<Pair<DocumentEntity, String?>> = results.mapNotNull { result ->
                    val entity = allDocs[result.document.id] ?: return@mapNotNull null
                    entity to result.matchingSnippet
                }
                _ragSearchResults.value = mapped
            } catch (_: Exception) {
                val allDocs: List<DocumentEntity> = repository.getAllDocumentsSync()
                val fallback: List<Pair<DocumentEntity, String?>> = allDocs
                    .filter { it.title.contains(query.trim(), ignoreCase = true) }
                    .map { it to null }
                _ragSearchResults.value = fallback
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun createFolder(name: String, onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val result = repository.createFolder(name)
            result.onSuccess { folderId ->
                _userMessage.value = "Folder created"
                onCreated?.invoke(folderId)
            }.onFailure { error ->
                _userMessage.value = error.message ?: "Failed to create folder"
            }
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            val result = repository.renameFolder(folderId, newName)
            result.onSuccess {
                _userMessage.value = "Folder renamed"
            }.onFailure { error ->
                _userMessage.value = error.message ?: "Failed to rename folder"
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            val result = repository.deleteFolder(folderId)
            result.onSuccess {
                _userMessage.value = "Folder deleted"
            }.onFailure { error ->
                _userMessage.value = error.message ?: "Failed to delete folder"
            }
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch {
            val result = repository.renameDocument(documentId, newTitle)
            result.onSuccess {
                _userMessage.value = "Document renamed"
            }.onFailure { error ->
                _userMessage.value = error.message ?: "Failed to rename document"
            }
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            val result = repository.deleteDocument(documentId)
            result.onSuccess {
                _userMessage.value = "Document deleted"
            }.onFailure { error ->
                _userMessage.value = error.message ?: "Failed to delete document"
            }
        }
    }

    fun setPendingDocument(pending: PendingDocument?) {
        repository.setPendingDocument(pending)
    }

    fun autoSortDocuments(onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = repository.autoSortDocuments()
            result.onSuccess { msg ->
                _userMessage.value = msg
                onComplete?.invoke(msg)
            }.onFailure { err ->
                _userMessage.value = err.message ?: "Failed to auto-sort documents"
            }
        }
    }

    suspend fun searchDetails(query: String): List<Pair<DocumentEntity, String>> {
        return repository.searchDetails(query)
    }

    suspend fun findSimilarDocuments(prompt: String): List<Pair<DocumentEntity, String>> {
        return repository.findSimilarDocuments(prompt)
    }
}

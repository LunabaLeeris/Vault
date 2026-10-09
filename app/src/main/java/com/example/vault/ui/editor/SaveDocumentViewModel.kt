package com.example.vault.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.data.local.FolderEntity
import com.example.vault.data.repository.PendingDocument
import com.example.vault.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SaveDocumentUiState(
    val title: String = "",
    val selectedFolderId: Long? = null,
    val previewBitmap: Bitmap? = null,
    val isPdf: Boolean = false,
    val extractTextOnSave: Boolean = true,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "Saving document...",
    val saveCompleted: Boolean = false,
    val savedDocumentId: Long? = null,
    val extractionWarning: String? = null,
    val errorMessage: String? = null
)

class SaveDocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SaveDocumentUiState())
    val uiState: StateFlow<SaveDocumentUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingDocument: StateFlow<PendingDocument?> = repository.pendingDocument

    init {
        viewModelScope.launch {
            val defaultFolderId = repository.ensureDefaultFolderExists()
            _uiState.update { current ->
                if (current.selectedFolderId == null) {
                    current.copy(selectedFolderId = defaultFolderId)
                } else current
            }
        }

        viewModelScope.launch {
            pendingDocument.collect { pending ->
                if (pending != null) {
                    initPendingDocument(pending)
                }
            }
        }
    }

    private fun initPendingDocument(pending: PendingDocument) {
        val isPdf = pending.mimeType.contains("pdf", ignoreCase = true)
        _uiState.update {
            it.copy(
                title = pending.defaultTitle,
                selectedFolderId = pending.suggestedFolderId ?: it.selectedFolderId,
                isPdf = isPdf
            )
        }
        loadPreview(pending.uri, isPdf)
    }

    private fun loadPreview(uri: Uri, isPdf: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isPdf) {
                    val pfd = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
                    pfd?.use { descriptor ->
                        val renderer = PdfRenderer(descriptor)
                        renderer.use { pdfRenderer ->
                            if (pdfRenderer.pageCount > 0) {
                                val page = pdfRenderer.openPage(0)
                                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                canvas.drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                _uiState.update { it.copy(previewBitmap = bmp) }
                            }
                        }
                    }
                } else {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    input?.use { stream ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bmp = BitmapFactory.decodeStream(stream, null, opts)
                        _uiState.update { it.copy(previewBitmap = bmp) }
                    }
                }
            } catch (e: Exception) {
                // If preview fails, the document can still be saved
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.update { it.copy(title = newTitle, errorMessage = null) }
    }

    fun onFolderSelected(folderId: Long) {
        _uiState.update { it.copy(selectedFolderId = folderId) }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val result = repository.createFolder(name)
            result.onSuccess { newFolderId ->
                _uiState.update { it.copy(selectedFolderId = newFolderId, errorMessage = null) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to create folder") }
            }
        }
    }

    fun onToggleExtractText(enabled: Boolean) {
        _uiState.update { it.copy(extractTextOnSave = enabled) }
    }

    fun saveDocument() {
        val pending = repository.pendingDocument.value ?: run {
            _uiState.update { it.copy(errorMessage = "No document to save") }
            return
        }
        val folderId = _uiState.value.selectedFolderId ?: run {
            _uiState.update { it.copy(errorMessage = "Please select a destination folder") }
            return
        }
        val title = _uiState.value.title.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Document title cannot be blank") }
            return
        }

        _uiState.update { it.copy(isSaving = true, statusMessage = "Saving document...", errorMessage = null) }
        viewModelScope.launch {
            val result = repository.saveDocument(
                sourceUri = pending.uri,
                title = title,
                mimeType = pending.mimeType,
                folderId = folderId,
                onProgress = { p ->
                    _uiState.update { it.copy(progress = p) }
                }
            )

            result.onSuccess { docId ->
                if (_uiState.value.extractTextOnSave) {
                    _uiState.update { it.copy(statusMessage = "Extracting text with on-device OCR...") }
                    val extraction = repository.extractAndSaveTextForDocument(docId)
                    if (extraction.isFailure) _uiState.update { it.copy(
                        extractionWarning = "Document saved. Text extraction failed. You can retry from the document's text screen.") }
                }
                repository.setPendingDocument(null)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveCompleted = true,
                        savedDocumentId = docId
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.message ?: "Failed to save document"
                    )
                }
            }
        }
    }

    fun cancel() {
        repository.setPendingDocument(null)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

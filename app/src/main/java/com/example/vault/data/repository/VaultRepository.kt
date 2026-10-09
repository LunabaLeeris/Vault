package com.example.vault.data.repository

import android.content.Context
import android.net.Uri
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.local.DocumentPageEntity
import com.example.vault.data.local.DocumentWithTextStatus
import com.example.vault.data.local.FolderEntity
import com.example.vault.data.local.FolderWithCount
import com.example.vault.data.local.VaultDao
import com.example.vault.data.local.VaultDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class PendingDocument(
    val uri: Uri,
    val mimeType: String,
    val defaultTitle: String,
    val suggestedFolderId: Long? = null
)

class VaultRepository(
    private val vaultDao: VaultDao,
    private val fileStorageManager: FileStorageManager
) {

    private val _pendingDocument = MutableStateFlow<PendingDocument?>(null)
    val pendingDocument = _pendingDocument.asStateFlow()

    fun setPendingDocument(pending: PendingDocument?) {
        _pendingDocument.value = pending
    }

    suspend fun ensureDefaultFolderExists(): Long = withContext(Dispatchers.IO) {
        val existing = vaultDao.findFolderByName("Inbox")
        if (existing != null) {
            existing.id
        } else {
            vaultDao.insertFolder(FolderEntity(name = "Inbox"))
        }
    }

    fun getFoldersWithCount(): Flow<List<FolderWithCount>> =
        vaultDao.getFoldersWithCount()

    fun getAllFolders(): Flow<List<FolderEntity>> =
        vaultDao.getAllFolders()

    fun getFolderById(folderId: Long): Flow<FolderEntity?> =
        vaultDao.getFolderById(folderId)

    fun getDocumentsInFolder(folderId: Long): Flow<List<DocumentEntity>> =
        vaultDao.getDocumentsInFolder(folderId)

    fun getDocumentsWithTextStatusInFolder(folderId: Long): Flow<List<DocumentWithTextStatus>> =
        vaultDao.getDocumentsWithTextStatusInFolder(folderId)

    fun getDocumentById(documentId: Long): Flow<DocumentEntity?> =
        vaultDao.getDocumentById(documentId)

    suspend fun createFolder(name: String): Result<Long> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Folder name cannot be blank"))
        }
        val existing = vaultDao.findFolderByName(trimmed)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("A folder named '$trimmed' already exists"))
        }
        try {
            val id = vaultDao.insertFolder(FolderEntity(name = trimmed))
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folderId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Cascade delete will delete document records, but we should also clean up files
            val existing = vaultDao.getDocumentByIdSync(folderId)
            // Get folder entity
            val foldersFlow = vaultDao.getAllFolders()
            // Room foreign key CASCADE will delete document DB records
            val folder = FolderEntity(id = folderId, name = "")
            vaultDao.deleteFolder(folder)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveDocument(
        sourceUri: Uri,
        title: String,
        mimeType: String,
        folderId: Long,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Document title cannot be blank"))
        }

        var savedRelativePath: String? = null
        try {
            // Step 1: Copy file to internal storage first
            savedRelativePath = fileStorageManager.copyUriToInternalStorage(
                sourceUri = sourceUri,
                mimeType = mimeType,
                onProgress = onProgress
            )

            // Step 2: Save database record only after file is written
            val now = System.currentTimeMillis()
            val documentEntity = DocumentEntity(
                folderId = folderId,
                title = trimmedTitle,
                mimeType = mimeType,
                relativePath = savedRelativePath,
                createdAt = now,
                updatedAt = now
            )
            val docId = vaultDao.insertDocument(documentEntity)
            Result.success(docId)
        } catch (e: Exception) {
            // Clean up written file if saving fails
            if (savedRelativePath != null) {
                fileStorageManager.deleteFile(savedRelativePath)
            }
            Result.failure(e)
        }
    }

    suspend fun renameDocument(documentId: Long, newTitle: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Document title cannot be blank"))
        }
        try {
            vaultDao.updateDocumentTitle(documentId, trimmed)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(documentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val doc = vaultDao.getDocumentByIdSync(documentId)
                ?: return@withContext Result.failure(IllegalStateException("Document not found"))

            // Remove database record
            vaultDao.deleteDocument(doc)

            // Clean up file in app-private storage
            val fileDeleted = fileStorageManager.deleteFile(doc.relativePath)
            if (!fileDeleted) {
                // File deletion failed or file wasn't present, but DB was removed
                return@withContext Result.failure(
                    IllegalStateException("Document was removed from database, but stored file could not be deleted")
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Document Page / Text Extraction Operations

    fun getPagesForDocument(documentId: Long): Flow<List<DocumentPageEntity>> =
        vaultDao.getPagesForDocument(documentId)

    fun hasExtractedText(documentId: Long): Flow<Boolean> =
        vaultDao.hasExtractedText(documentId)

    suspend fun extractAndSaveTextForDocument(
        documentId: Long,
        onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): Result<List<DocumentPageEntity>> = withContext(Dispatchers.IO) {
        try {
            val doc = vaultDao.getDocumentByIdSync(documentId)
                ?: return@withContext Result.failure(IllegalStateException("Document not found"))

            val file = fileStorageManager.getFile(doc.relativePath)
            val extracted = com.example.vault.ocr.TextRecognitionHelper.extractTextFromFile(
                file = file,
                mimeType = doc.mimeType,
                onProgress = onProgress
            )

            val now = System.currentTimeMillis()
            val entities = extracted.map { page ->
                DocumentPageEntity(
                    documentId = documentId,
                    pageNumber = page.pageNumber,
                    rawText = page.text,
                    editedText = null,
                    updatedAt = now
                )
            }

            vaultDao.deletePagesForDocument(documentId)
            vaultDao.insertPages(entities)
            Result.success(entities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveExtractedPagesForDocument(
        documentId: Long,
        pages: List<com.example.vault.ocr.ExtractedPage>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val entities = pages.map { page ->
                DocumentPageEntity(
                    documentId = documentId,
                    pageNumber = page.pageNumber,
                    rawText = page.text,
                    editedText = null,
                    updatedAt = now
                )
            }
            vaultDao.deletePagesForDocument(documentId)
            vaultDao.insertPages(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePageText(pageId: Long, editedText: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.updatePageText(pageId, editedText)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteExtractedText(documentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultDao.deletePagesForDocument(documentId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFile(relativePath: String) = fileStorageManager.getFile(relativePath)

    companion object {
        @Volatile
        private var INSTANCE: VaultRepository? = null

        fun getInstance(context: Context): VaultRepository {
            return INSTANCE ?: synchronized(this) {
                val db = VaultDatabase.getDatabase(context)
                val fileStorageManager = FileStorageManager(context)
                val instance = VaultRepository(db.vaultDao(), fileStorageManager)
                INSTANCE = instance
                instance
            }
        }
    }
}

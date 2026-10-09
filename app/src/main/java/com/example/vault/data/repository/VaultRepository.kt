package com.example.vault.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.example.vault.data.local.*
import com.example.vault.ocr.ExtractedPage
import com.example.vault.ocr.TextRecognitionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class PendingDocument(val uri: Uri, val mimeType: String, val defaultTitle: String, val suggestedFolderId: Long? = null)

class VaultRepository(
    private val vaultDao: VaultDao,
    private val fileStorageManager: FileStorageManager,
    private val database: VaultDatabase,
) {
    private val mutablePending = MutableStateFlow<PendingDocument?>(null)
    val pendingDocument = mutablePending.asStateFlow()
    fun setPendingDocument(pending: PendingDocument?) { mutablePending.value = pending }

    suspend fun ensureDefaultFolderExists(): Long = database.withTransaction {
        vaultDao.findFolderByName("Inbox")?.id ?: vaultDao.insertFolder(FolderEntity(name = "Inbox"))
    }

    fun getFoldersWithCount() = vaultDao.getFoldersWithCount()
    fun getAllFolders() = vaultDao.getAllFolders()
    fun getFolderById(folderId: Long) = vaultDao.getFolderById(folderId)
    fun getDocumentsInFolder(folderId: Long) = vaultDao.getDocumentsInFolder(folderId)
    fun getDocumentsWithTextStatusInFolder(folderId: Long) = vaultDao.getDocumentsWithTextStatusInFolder(folderId)
    fun getDocumentById(documentId: Long) = vaultDao.getDocumentById(documentId)
    suspend fun findDocument(documentId: Long) = vaultDao.getDocumentByIdSync(documentId)
    fun getPagesForDocument(documentId: Long) = vaultDao.getPagesForDocument(documentId)
    fun hasExtractedText(documentId: Long) = vaultDao.hasExtractedText(documentId)
    suspend fun readDocumentSnapshot(): List<DocumentSnapshot> = vaultDao.getDocumentSnapshots()
    fun getFile(relativePath: String) = fileStorageManager.getFile(relativePath)

    fun observeDocumentChanges(): Flow<Unit> = callbackFlow {
        val observer = object : InvalidationTracker.Observer("documents", "document_pages", "folders") {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        database.invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { database.invalidationTracker.removeObserver(observer) }
    }.conflate().flowOn(Dispatchers.IO)

    suspend fun createFolder(name: String): Result<Long> = operation {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Folder name cannot be blank" }
        database.withTransaction {
            require(vaultDao.findFolderByName(trimmed) == null) { "A folder with this name already exists" }
            vaultDao.insertFolder(FolderEntity(name = trimmed))
        }
    }

    suspend fun deleteFolder(folderId: Long): Result<Unit> = operation {
        withContext(NonCancellable) {
        val files = database.withTransaction {
            val folder = checkNotNull(vaultDao.getFolderByIdSync(folderId)) { "Folder not found" }
            val documents = vaultDao.getDocumentsInFolderSync(folderId)
            check(vaultDao.deleteFolder(folder) == 1)
            documents.map { it.relativePath }
        }
        val failed = files.map { fileStorageManager.deleteFile(it) }.any { !it }
        check(!failed) { "Folder deleted, but some stored files could not be removed" }
        }
    }

    suspend fun saveDocument(
        sourceUri: Uri, title: String, mimeType: String, folderId: Long, onProgress: ((Float) -> Unit)? = null,
    ): Result<Long> {
        var ownedPath: String? = null
        var committed = false
        return try {
            require(title.trim().isNotBlank()) { "Document title cannot be blank" }
            val path = fileStorageManager.copyUriToInternalStorage(sourceUri, mimeType, onProgress)
            ownedPath = path
            // Finish publication once bytes are owned; cancellation must not delete an already committed original.
            val id = withContext(NonCancellable) {
                database.withTransaction {
                    vaultDao.insertDocument(DocumentEntity(folderId = folderId, title = title.trim(), mimeType = mimeType, relativePath = path))
                }.also { committed = true }
            }
            Result.success(id)
        } catch (error: Exception) {
            if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                ownedPath?.let { fileStorageManager.deleteFile(it) }
            }
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    suspend fun renameDocument(documentId: Long, newTitle: String): Result<Unit> = operation {
        require(newTitle.trim().isNotBlank()) { "Document title cannot be blank" }
        check(vaultDao.updateDocumentTitle(documentId, newTitle.trim()) == 1) { "Document not found" }
    }

    suspend fun deleteDocument(documentId: Long): Result<Unit> = operation {
        withContext(NonCancellable) {
        val document = database.withTransaction {
            val doc = checkNotNull(vaultDao.getDocumentByIdSync(documentId)) { "Document not found" }
            check(vaultDao.deleteDocument(doc) == 1)
            doc
        }
        check(fileStorageManager.deleteFile(document.relativePath)) {
            "Document deleted, but its stored file could not be removed"
        }
        }
    }

    suspend fun extractAndSaveTextForDocument(
        documentId: Long, onProgress: ((Int, Int) -> Unit)? = null,
    ): Result<List<DocumentPageEntity>> = operation {
        val document = checkNotNull(vaultDao.getDocumentByIdSync(documentId)) { "Document not found" }
        val extracted = TextRecognitionHelper.extractTextFromFile(getFile(document.relativePath), document.mimeType, onProgress)
        publishPages(documentId, document.textRevision, extracted)
    }

    suspend fun saveExtractedPagesForDocument(
        documentId: Long, pages: List<ExtractedPage>, expectedRevision: Long? = null,
    ): Result<Unit> = operation {
        val revision = expectedRevision ?: checkNotNull(vaultDao.getDocumentByIdSync(documentId)).textRevision
        publishPages(documentId, revision, pages)
        Unit
    }

    private suspend fun publishPages(documentId: Long, expectedRevision: Long, pages: List<ExtractedPage>) = database.withTransaction {
        val document = checkNotNull(vaultDao.getDocumentByIdSync(documentId)) { "Document was deleted during extraction" }
        check(document.textRevision == expectedRevision) { "Text changed during extraction. Retry extraction." }
        require(pages.isNotEmpty() && pages.all { it.pageNumber > 0 } && pages.map { it.pageNumber }.distinct().size == pages.size) {
            "Extraction did not return a valid page set"
        }
        val existing = vaultDao.getPagesForDocumentSync(documentId).associateBy { it.pageNumber }
        val replacements = pages.map { page ->
            val old = existing[page.pageNumber]
            DocumentPageEntity(id = old?.id ?: 0, documentId = documentId, pageNumber = page.pageNumber,
                rawText = page.text, editedText = old?.editedText)
        }
        vaultDao.deletePagesForDocument(documentId)
        vaultDao.insertPages(replacements)
        check(vaultDao.incrementTextRevision(documentId) == 1)
        vaultDao.getPagesForDocumentSync(documentId)
    }

    suspend fun updatePageText(pageId: Long, editedText: String): Result<Unit> = operation {
        database.withTransaction {
            val page = checkNotNull(vaultDao.getPageByIdSync(pageId)) { "Text page no longer exists" }
            check(vaultDao.updatePageText(pageId, editedText) == 1)
            check(vaultDao.incrementTextRevision(page.documentId) == 1)
        }
    }

    suspend fun deleteExtractedText(documentId: Long): Result<Unit> = operation {
        database.withTransaction {
            checkNotNull(vaultDao.getDocumentByIdSync(documentId)) { "Document not found" }
            vaultDao.deletePagesForDocument(documentId)
            check(vaultDao.incrementTextRevision(documentId) == 1)
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }

    companion object {
        @Volatile private var instance: VaultRepository? = null
        fun getInstance(context: Context): VaultRepository = instance ?: synchronized(this) {
            instance ?: VaultDatabase.getDatabase(context).let { db ->
                VaultRepository(db.vaultDao(), FileStorageManager(context.applicationContext), db).also { instance = it }
            }
        }
    }
}

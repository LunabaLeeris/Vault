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

    fun getRecentDocuments(): Flow<List<DocumentEntity>> =
        vaultDao.getRecentDocuments()

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

    suspend fun autoSortDocuments(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val docs = vaultDao.getAllDocumentsSync()
            if (docs.isEmpty()) {
                return@withContext Result.success("No documents to organize yet")
            }

            val categoryFolders = listOf(
                "Receipts & Invoices",
                "Scans & Documents",
                "Photos & Images",
                "General"
            )

            val folderMap = mutableMapOf<String, Long>()
            for (cat in categoryFolders) {
                val existing = vaultDao.findFolderByName(cat)
                val id = existing?.id ?: vaultDao.insertFolder(FolderEntity(name = cat))
                folderMap[cat] = id
            }

            var movedCount = 0
            for (doc in docs) {
                val titleLower = doc.title.lowercase()
                val targetCategory = when {
                    titleLower.contains("receipt") || titleLower.contains("invoice") ||
                    titleLower.contains("bill") || titleLower.contains("tax") ||
                    titleLower.contains("pay") || titleLower.contains("order") -> "Receipts & Invoices"

                    doc.mimeType.contains("image", ignoreCase = true) ||
                    titleLower.contains("photo") || titleLower.contains("img") ||
                    titleLower.contains("picture") -> "Photos & Images"

                    titleLower.contains("scan") || doc.mimeType.contains("pdf", ignoreCase = true) -> "Scans & Documents"

                    else -> "General"
                }

                val targetFolderId = folderMap[targetCategory] ?: continue
                if (doc.folderId != targetFolderId) {
                    vaultDao.updateDocumentFolder(doc.id, targetFolderId)
                    movedCount++
                }
            }

            Result.success("Organized ${docs.size} document(s) into folders ($movedCount reorganized)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchDetails(query: String): List<Pair<DocumentEntity, String>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val trimmed = query.trim()
        val allDocs = vaultDao.getAllDocumentsSync()
        val matchedPages = try {
            vaultDao.searchDocumentPages(trimmed)
        } catch (_: Exception) {
            emptyList()
        }

        val pageMap = matchedPages.groupBy { it.documentId }
        val results = mutableListOf<Pair<DocumentEntity, String>>()

        for (doc in allDocs) {
            val pages = pageMap[doc.id]
            if (pages != null && pages.isNotEmpty()) {
                val firstPage = pages.first()
                val fullText = firstPage.editedText ?: firstPage.rawText
                val index = fullText.indexOf(trimmed, ignoreCase = true)
                val snippet = if (index >= 0) {
                    val start = (index - 20).coerceAtLeast(0)
                    val end = (index + trimmed.length + 30).coerceAtMost(fullText.length)
                    "Page ${firstPage.pageNumber}: \"..." + fullText.substring(start, end).replace("\n", " ").trim() + "...\""
                } else {
                    "Found in extracted text (Page ${firstPage.pageNumber})"
                }
                results.add(doc to snippet)
            } else if (doc.title.contains(trimmed, ignoreCase = true)) {
                results.add(doc to "Matches document title")
            }
        }
        results
    }

    suspend fun findSimilarDocuments(prompt: String): List<Pair<DocumentEntity, String>> = withContext(Dispatchers.IO) {
        val allDocs = vaultDao.getAllDocumentsSync()
        if (prompt.isBlank()) {
            return@withContext allDocs.take(6).map { it to "Recent document" }
        }

        val stopWords = setOf("a", "an", "the", "all", "and", "or", "to", "of", "in", "for", "with", "show", "list", "find", "get", "my", "similar", "documents", "document", "files", "file", "that", "which", "me")
        val keywords = prompt.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() && it !in stopWords }

        if (keywords.isEmpty()) {
            return@withContext allDocs.take(6).map { it to "Recent document" }
        }

        val scored = mutableListOf<Triple<DocumentEntity, Int, String>>()

        for (doc in allDocs) {
            var score = 0
            var reason = ""
            val titleLower = doc.title.lowercase()
            val isPdf = doc.mimeType.contains("pdf", ignoreCase = true)
            val isImage = doc.mimeType.contains("image", ignoreCase = true)

            for (kw in keywords) {
                if (titleLower.contains(kw)) {
                    score += 3
                    if (reason.isBlank()) reason = "Matches title \"$kw\""
                }
                if ((kw == "pdf" || kw == "doc") && isPdf) {
                    score += 2
                    if (reason.isBlank()) reason = "PDF format match"
                }
                if ((kw == "photo" || kw == "image" || kw == "picture") && isImage) {
                    score += 2
                    if (reason.isBlank()) reason = "Image format match"
                }
            }

            if (score > 0) {
                scored.add(Triple(doc, score, reason))
            }
        }

        scored.sortedByDescending { it.second }.map { it.first to it.third }
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

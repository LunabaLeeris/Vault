package com.example.vault.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class FolderWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val documentCount: Int
)

data class DocumentWithTextStatus(
    @Embedded val document: DocumentEntity,
    val hasExtractedText: Boolean
)

@Dao
interface VaultDao {

    @Transaction
    @Query("SELECT * FROM documents ORDER BY id")
    suspend fun getDocumentSnapshots(): List<DocumentSnapshot>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderByIdSync(folderId: Long): FolderEntity?

    @Query("UPDATE documents SET textRevision = textRevision + 1, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun incrementTextRevision(documentId: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("""
        SELECT f.id, f.name, f.createdAt, COUNT(d.id) AS documentCount
        FROM folders f
        LEFT JOIN documents d ON f.id = d.folderId
        GROUP BY f.id
        ORDER BY f.createdAt ASC
    """)
    fun getFoldersWithCount(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :folderId LIMIT 1")
    fun getFolderById(folderId: Long): Flow<FolderEntity?>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY id")
    suspend fun getDocumentsInFolderSync(folderId: Long): List<DocumentEntity>

    @Query("SELECT * FROM folders WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findFolderByName(name: String): FolderEntity?

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :newName WHERE id = :id")
    suspend fun updateFolderName(id: Long, newName: String): Int

    @Delete
    suspend fun deleteFolder(folder: FolderEntity): Int

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long): Int


    @Query("SELECT * FROM documents ORDER BY createdAt DESC LIMIT 20")
    fun getRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getDocumentsInFolder(folderId: Long): Flow<List<DocumentEntity>>

    @Query("""
        SELECT d.*, (COUNT(p.id) > 0) AS hasExtractedText
        FROM documents d
        LEFT JOIN document_pages p ON d.id = p.documentId
        WHERE d.folderId = :folderId
        GROUP BY d.id
        ORDER BY d.createdAt DESC
    """)
    fun getDocumentsWithTextStatusInFolder(folderId: Long): Flow<List<DocumentWithTextStatus>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    fun getDocumentById(documentId: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentByIdSync(documentId: Long): DocumentEntity?

    @Query("SELECT * FROM document_pages WHERE id = :pageId")
    suspend fun getPageByIdSync(pageId: Long): DocumentPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Query("UPDATE documents SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDocumentTitle(id: Long, newTitle: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Delete
    suspend fun deleteDocument(document: DocumentEntity): Int

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long): Int

    // Document Pages / Extracted Text Operations
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    fun getPagesForDocument(documentId: Long): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getPagesForDocumentSync(documentId: Long): List<DocumentPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<DocumentPageEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: DocumentPageEntity): Long

    @Query("UPDATE document_pages SET editedText = :editedText, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updatePageText(pageId: Long, editedText: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE document_pages SET translatedText = :translatedText, translatedLanguage = :language, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updateTranslatedPageText(pageId: Long, translatedText: String?, language: String?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM document_pages WHERE documentId = :documentId")
    suspend fun deletePagesForDocument(documentId: Long): Int

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsSync(): List<DocumentEntity>

    @Query("UPDATE documents SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDocumentFolder(id: Long, folderId: Long, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM document_pages WHERE rawText LIKE '%' || :query || '%' OR (editedText IS NOT NULL AND editedText LIKE '%' || :query || '%')")
    suspend fun searchDocumentPages(query: String): List<DocumentPageEntity>

    @Query("SELECT COUNT(*) > 0 FROM document_pages WHERE documentId = :documentId")
    fun hasExtractedText(documentId: Long): Flow<Boolean>
}

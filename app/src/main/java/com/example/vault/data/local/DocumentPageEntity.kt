package com.example.vault.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId", "pageNumber"], unique = true),
        Index(value = ["documentId"])
    ]
)
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageNumber: Int,
    val rawText: String,
    val editedText: String? = null,
    val translatedText: String? = null,
    val translatedLanguage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Authoritative text content for display, search, and future RAG citations.
     */
    val content: String
        get() = editedText ?: rawText

    fun searchableContent(preferredLanguage: String? = null): String =
        if (!preferredLanguage.isNullOrBlank() && translatedLanguage.equals(preferredLanguage, true) && translatedText != null) {
            translatedText
        } else content + (translatedText?.let { "\n$it" } ?: "")
}

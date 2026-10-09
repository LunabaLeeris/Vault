package com.example.vault.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val title: String,
    val mimeType: String,
    val relativePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

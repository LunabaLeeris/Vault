package com.example.vault.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class DocumentSnapshot(
    @Embedded val document: DocumentEntity,
    @Relation(parentColumn = "folderId", entityColumn = "id") val folder: FolderEntity,
    @Relation(parentColumn = "id", entityColumn = "documentId") val pages: List<DocumentPageEntity>,
)

package com.example.vault.rag

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vault.data.local.VaultDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VaultMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), VaultDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun versionOneVaultPreservesDocumentsPagesEditsAndPaths() {
        val name = "migration-test-vault"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO folders (id,name,createdAt) VALUES (1,'Preserved',10)")
            execSQL("INSERT INTO documents (id,folderId,title,mimeType,relativePath,createdAt,updatedAt) VALUES (2,1,'Original','application/pdf','documents/stable.pdf',20,30)")
            execSQL("INSERT INTO document_pages (id,documentId,pageNumber,rawText,editedText,updatedAt) VALUES (3,2,3,'Raw OCR','User correction',40)")
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, VaultDatabase.MIGRATION_1_2).apply {
            query("SELECT id,relativePath,textRevision FROM documents").use {
                assertTrue(it.moveToFirst()); assertEquals(2L, it.getLong(0)); assertEquals("documents/stable.pdf", it.getString(1)); assertEquals(1L, it.getLong(2))
            }
            query("SELECT pageNumber,rawText,editedText FROM document_pages").use {
                assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(0)); assertEquals("Raw OCR", it.getString(1)); assertEquals("User correction", it.getString(2))
            }
            close()
        }
    }
}

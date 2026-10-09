package com.example.vault.rag.index

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.vault.rag.model.ReferenceChunk
import com.example.vault.rag.model.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Disposable cache, separate from the authoritative vault Room database. */
class SqliteRetrievalIndex(context: Context, name: String? = "rag_index.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1), RetrievalIndex {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE chunks (
            row_id INTEGER PRIMARY KEY, chunk_id TEXT NOT NULL, document_id TEXT NOT NULL,
            title TEXT NOT NULL, folder_id TEXT NOT NULL, page_number INTEGER NOT NULL,
            revision INTEGER NOT NULL, text TEXT NOT NULL, start_offset INTEGER NOT NULL,
            end_offset INTEGER NOT NULL, is_edited INTEGER NOT NULL)""")
        db.execSQL("CREATE VIRTUAL TABLE chunk_fts USING fts4(text, title, tokenize=unicode61)")
        db.execSQL("CREATE INDEX chunks_scope ON chunks(document_id, folder_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is only a rebuildable cache.
        db.execSQL("DROP TABLE IF EXISTS chunk_fts")
        db.execSQL("DROP TABLE IF EXISTS chunks")
        onCreate(db)
    }

    override suspend fun replace(chunks: List<ReferenceChunk>): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chunk_fts", null, null)
            db.delete("chunks", null, null)
            chunks.forEach { chunk ->
                ensureActive()
                val rowId = db.insertOrThrow("chunks", null, ContentValues().apply {
                    put("chunk_id", chunk.id)
                    put("document_id", chunk.documentId)
                    put("title", chunk.title)
                    put("folder_id", chunk.folderId)
                    put("page_number", chunk.pageNumber)
                    put("revision", chunk.revision)
                    put("text", chunk.text)
                    put("start_offset", chunk.startOffset)
                    put("end_offset", chunk.endOffset)
                    put("is_edited", if (chunk.isEdited) 1 else 0)
                })
                db.insertOrThrow("chunk_fts", null, ContentValues().apply {
                    put("rowid", rowId)
                    put("text", chunk.text)
                    put("title", chunk.title)
                })
            }
            ensureActive()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun search(terms: List<String>, scope: SearchScope, limit: Int): List<ReferenceChunk> =
        withContext(Dispatchers.IO) {
            require(limit > 0)
            require(terms.all { it.matches(Regex("[\\p{L}\\p{N}]+")) })
            if (terms.isEmpty()) return@withContext emptyList()
            val args = mutableListOf(KeywordQuery.matchExpression(terms))
            val scopeSql = when (scope) {
                SearchScope.All -> ""
                is SearchScope.Document -> " AND c.document_id = ?".also { args.add(scope.id) }
                is SearchScope.Folder -> " AND c.folder_id = ?".also { args.add(scope.id) }
            }
            val best = mutableListOf<Pair<ReferenceChunk, Int>>()
            readableDatabase.rawQuery(
                "SELECT c.* FROM chunks c JOIN chunk_fts f ON c.row_id = f.rowid " +
                    "WHERE chunk_fts MATCH ?$scopeSql ORDER BY c.row_id",
                args.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    ensureActive()
                    val chunk = ReferenceChunk(
                        id = cursor.getString(1), documentId = cursor.getString(2),
                        title = cursor.getString(3), folderId = cursor.getString(4),
                        pageNumber = cursor.getInt(5), revision = cursor.getLong(6),
                        text = cursor.getString(7), startOffset = cursor.getInt(8),
                        endOffset = cursor.getInt(9), isEdited = cursor.getInt(10) != 0,
                    )
                    best.add(chunk to KeywordQuery.score(chunk.text, chunk.title, terms))
                    best.sortByDescending { it.second }
                    if (best.size > limit) best.removeAt(best.lastIndex)
                }
            }
            best.map { it.first }
        }
}

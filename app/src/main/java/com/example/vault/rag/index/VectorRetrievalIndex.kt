package com.example.vault.rag.index

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.vault.rag.model.ReferenceChunk
import com.example.vault.rag.model.SearchScope
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Persistent on-device vector index backed by MediaPipe MobileBERT embeddings. */
class VectorRetrievalIndex(
    context: Context,
    private val modelAssetName: String,
    private val minimumSimilarity: Float,
    name: String = "rag_vector_index.db",
) : SQLiteOpenHelper(context.applicationContext, name, null, 1), RetrievalIndex {
    private val appContext = context.applicationContext
    private val embedderDelegate = lazy {
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetName).build())
            .setL2Normalize(true)
            .build()
        TextEmbedder.createFromOptions(appContext, options)
    }
    private val embedder: TextEmbedder get() = embedderDelegate.value

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE vectors (
                chunk_id TEXT PRIMARY KEY,
                source_hash TEXT NOT NULL,
                document_id TEXT NOT NULL,
                title TEXT NOT NULL,
                folder_id TEXT NOT NULL,
                page_number INTEGER NOT NULL,
                revision INTEGER NOT NULL,
                text TEXT NOT NULL,
                start_offset INTEGER NOT NULL,
                end_offset INTEGER NOT NULL,
                is_edited INTEGER NOT NULL,
                embedding BLOB NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX vectors_scope ON vectors(document_id, folder_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS vectors")
        onCreate(db)
    }

    override suspend fun replace(chunks: List<ReferenceChunk>) = withContext(Dispatchers.IO) {
        val cached = mutableMapOf<String, Pair<String, ByteArray>>()
        readableDatabase.query("vectors", arrayOf("chunk_id", "source_hash", "embedding"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) cached[cursor.getString(0)] = cursor.getString(1) to cursor.getBlob(2)
        }
        val prepared = chunks.map { chunk ->
            ensureActive()
            val embeddingSource = "${chunk.title}\n${chunk.text}"
            val sourceHash = sha256("$modelAssetName\n$embeddingSource")
            val blob = cached[chunk.id]?.takeIf { it.first == sourceHash }?.second
                ?: embed(embeddingSource).toBlob()
            Triple(chunk, sourceHash, blob)
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("vectors", null, null)
            prepared.forEach { (chunk, sourceHash, blob) ->
                ensureActive()
                db.insertOrThrow("vectors", null, ContentValues().apply {
                    put("chunk_id", chunk.id)
                    put("source_hash", sourceHash)
                    put("document_id", chunk.documentId)
                    put("title", chunk.title)
                    put("folder_id", chunk.folderId)
                    put("page_number", chunk.pageNumber)
                    put("revision", chunk.revision)
                    put("text", chunk.text)
                    put("start_offset", chunk.startOffset)
                    put("end_offset", chunk.endOffset)
                    put("is_edited", if (chunk.isEdited) 1 else 0)
                    put("embedding", blob)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun search(query: String, scope: SearchScope, limit: Int): List<ReferenceChunk> = withContext(Dispatchers.IO) {
        if (query.isBlank() || limit <= 0) return@withContext emptyList()
        val terms = KeywordQuery.terms(query)
        val cleaned = KeywordQuery.cleanQuery(query)

        val queryVector = embed(query)
        val cleanedVector = if (cleaned != query && cleaned.isNotBlank()) embed(cleaned) else null

        val selection: String?
        val args: Array<String>?
        when (scope) {
            SearchScope.All -> { selection = null; args = null }
            is SearchScope.Document -> { selection = "document_id = ?"; args = arrayOf(scope.id) }
            is SearchScope.Folder -> { selection = "folder_id = ?"; args = arrayOf(scope.id) }
        }
        val results = mutableListOf<ReferenceChunk>()
        readableDatabase.query(
            "vectors",
            arrayOf("chunk_id", "document_id", "title", "folder_id", "page_number", "revision", "text", "start_offset", "end_offset", "is_edited", "embedding"),
            selection,
            args,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                ensureActive()
                val chunkEmbedding = cursor.getBlob(10).toFloatArray()
                val fullSim = VectorMath.cosine(queryVector, chunkEmbedding)
                val cleanSim = if (cleanedVector != null) VectorMath.cosine(cleanedVector, chunkEmbedding) else -1f
                val denseSim = maxOf(fullSim, cleanSim)

                val chunkText = cursor.getString(6)
                val chunkTitle = cursor.getString(2)
                val lexScore = KeywordQuery.lexicalScore(chunkText, chunkTitle, terms)

                // Hybrid scoring & relevance gating:
                val finalScore = if (terms.isNotEmpty()) {
                    if (lexScore > 0f) {
                        // Keyword match: require either sufficient lexical coverage or strong semantic support
                        if (lexScore >= 0.15f || denseSim >= 0.50f) {
                            (denseSim * 0.40f + lexScore * 0.60f).coerceIn(0f, 1f)
                        } else 0f
                    } else {
                        // Zero keyword matches: must pass high semantic confidence threshold to be relevant
                        if (denseSim >= 0.80f) (denseSim * 0.50f) else 0f
                    }
                } else {
                    if (denseSim >= maxOf(minimumSimilarity, 0.65f)) denseSim else 0f
                }

                if (finalScore >= minimumSimilarity && finalScore > 0f) {
                    results += ReferenceChunk(
                        id = cursor.getString(0),
                        documentId = cursor.getString(1),
                        title = chunkTitle,
                        folderId = cursor.getString(3),
                        pageNumber = cursor.getInt(4),
                        revision = cursor.getLong(5),
                        text = chunkText,
                        startOffset = cursor.getInt(7),
                        endOffset = cursor.getInt(8),
                        isEdited = cursor.getInt(9) != 0,
                        similarity = finalScore,
                    )
                }
            }
        }
        val hasKeywordMatches = terms.isNotEmpty() && results.any {
            KeywordQuery.lexicalScore(it.text, it.title, terms) >= 0.15f
        }
        val candidateList = if (hasKeywordMatches) {
            results.filter { chunk ->
                val lex = KeywordQuery.lexicalScore(chunk.text, chunk.title, terms)
                lex >= 0.15f || (lex > 0f && chunk.similarity >= 0.50f)
            }
        } else {
            results
        }
        val topScore = candidateList.maxOfOrNull { it.similarity } ?: 0f
        val pruned = if (topScore > 0f) {
            candidateList.filter { it.similarity >= topScore * 0.70f }
        } else {
            candidateList
        }
        pruned.sortedByDescending { it.similarity }.take(limit)
    }

    private fun embed(text: String): FloatArray = embedder.embed(text)
        .embeddingResult()
        .embeddings()
        .first()
        .floatEmbedding()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun FloatArray.toBlob(): ByteArray = ByteBuffer.allocate(size * Float.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .also { it.asFloatBuffer().put(this) }
        .array()

    private fun ByteArray.toFloatArray(): FloatArray {
        val floats = FloatArray(size / Float.SIZE_BYTES)
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
        return floats
    }

    override fun close() {
        if (embedderDelegate.isInitialized()) embedderDelegate.value.close()
        super.close()
    }
}

package com.example.vault.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class FileStorageManager(private val context: Context) {

    private val documentsDir: File
        get() {
            val dir = File(context.filesDir, "documents")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Copies content from a source Uri into app-private internal storage.
     * Returns the relative path (e.g. "documents/<uuid>.<ext>") if successful.
     * In case of any error, cleans up any partially written target file.
     */
    suspend fun copyUriToInternalStorage(
        sourceUri: Uri,
        mimeType: String,
        onProgress: ((Float) -> Unit)? = null
    ): String {
        val extension = when {
            mimeType.contains("pdf", ignoreCase = true) -> "pdf"
            mimeType.contains("png", ignoreCase = true) -> "png"
            else -> "jpg"
        }
        val filename = "${UUID.randomUUID()}.$extension"
        val targetFile = File(documentsDir, filename)

        try {
            return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Unable to open stream for URI: $sourceUri")

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalCopied = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, bytesRead)
                        totalCopied += bytesRead
                        onProgress?.invoke(1.0f)
                    }
                    output.flush()
                }
            }
            "documents/$filename"
            }
        } catch (e: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { if (targetFile.exists()) targetFile.delete() }
            throw e
        }
    }

    /**
     * Resolves a relative path to the absolute File.
     */
    fun getFile(relativePath: String): File {
        return File(context.filesDir, relativePath)
    }

    /**
     * Safely deletes a file in app-private storage.
     * Returns true if file was deleted or did not exist, false if deletion failed.
     */
    suspend fun deleteFile(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = getFile(relativePath)
            if (!file.exists()) true else file.delete()
        } catch (_: Exception) {
            false
        }
    }
}

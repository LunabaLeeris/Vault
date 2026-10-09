package com.example.vault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractedPage(
    val pageNumber: Int,
    val text: String
)

object TextRecognitionHelper {

    private fun getRecognizer(): TextRecognizer {
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extractTextFromImageBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val recognizer = getRecognizer()
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        try {
            recognizeText(recognizer, inputImage)
        } finally {
            recognizer.close()
        }
    }

    suspend fun extractTextFromFile(
        file: File,
        mimeType: String,
        onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): List<ExtractedPage> = withContext(Dispatchers.IO) {
        check(file.isFile) { "Document file is unavailable" }

        if (mimeType.contains("pdf", ignoreCase = true)) {
            extractTextFromPdfFile(file, onProgress)
        } else {
            extractTextFromImageFile(file)
        }
    }

    suspend fun extractTextFromUri(
        context: Context,
        uri: Uri,
        mimeType: String
    ): List<ExtractedPage> = withContext(Dispatchers.IO) {
        if (mimeType.contains("pdf", ignoreCase = true)) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext emptyList()
            pfd.use { descriptor ->
                extractTextFromPdfDescriptor(descriptor)
            }
        } else {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext emptyList()
            val bitmap = inputStream.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext emptyList()
            val text = extractTextFromImageBitmap(bitmap)
            bitmap.recycle()
            listOf(ExtractedPage(pageNumber = 1, text = text))
        }
    }

    private suspend fun extractTextFromImageFile(file: File): List<ExtractedPage> {
        val bitmap = checkNotNull(BitmapFactory.decodeFile(file.absolutePath)) { "Document image could not be decoded" }
        try {
            val text = extractTextFromImageBitmap(bitmap)
            return listOf(ExtractedPage(pageNumber = 1, text = text))
        } finally {
            if (coroutineContext.isActive) bitmap.recycle()
        }
    }

    private suspend fun extractTextFromPdfFile(
        file: File,
        onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): List<ExtractedPage> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            ?: return emptyList()
        return pfd.use { descriptor ->
            extractTextFromPdfDescriptor(descriptor, onProgress)
        }
    }

    private suspend fun extractTextFromPdfDescriptor(
        pfd: ParcelFileDescriptor,
        onProgress: ((currentPage: Int, totalPages: Int) -> Unit)? = null
    ): List<ExtractedPage> {
        val recognizer = getRecognizer()
        val results = mutableListOf<ExtractedPage>()
        try {
            val renderer = PdfRenderer(pfd)
            renderer.use { pdfRenderer ->
                val totalPages = pdfRenderer.pageCount
                for (i in 0 until totalPages) {
                    coroutineContext.ensureActive()
                    onProgress?.invoke(i + 1, totalPages)
                    val page = pdfRenderer.openPage(i)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val pageText = try { recognizeText(recognizer, inputImage) }
                    finally { if (coroutineContext.isActive) bitmap.recycle() }
                    results.add(ExtractedPage(pageNumber = i + 1, text = pageText))
                }
            }
        } finally {
            recognizer.close()
        }
        return results
    }

    private suspend fun recognizeText(
        recognizer: TextRecognizer,
        inputImage: InputImage
    ): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                if (continuation.isActive) continuation.resume(visionText.text)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }
}

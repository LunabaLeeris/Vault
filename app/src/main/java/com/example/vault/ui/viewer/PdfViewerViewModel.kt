package com.example.vault.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.repository.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class PdfViewerState(
    val document: DocumentEntity? = null, val bitmap: Bitmap? = null,
    val pageIndex: Int = 0, val pageCount: Int = 0, val isLoading: Boolean = true,
    val error: String? = null, val notice: String? = null,
)

class PdfViewerViewModel(application: Application, private val documentId: Long, private val initialPage: Int) : AndroidViewModel(application) {
    private val repository = VaultRepository.getInstance(application)
    private val mutableState = MutableStateFlow(PdfViewerState())
    val state = mutableState.asStateFlow()
    private val rendererMutex = Mutex()
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var loadedPath: String? = null
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getDocumentById(documentId).collect { doc ->
                mutableState.update { it.copy(document = doc) }
                if (doc == null) {
                    renderJob?.cancel()
                    mutableState.update { it.copy(bitmap = null, isLoading = false, error = "Document is no longer available") }
                    withContext(Dispatchers.IO) { rendererMutex.withLock { closeRenderer() } }
                } else if (loadedPath != doc.relativePath) {
                    loadedPath = doc.relativePath
                    loadDocument(doc)
                }
            }
        }
    }

    private fun loadDocument(doc: DocumentEntity) {
        renderJob?.cancel()
        mutableState.update { it.copy(isLoading = true, error = null) }
        renderJob = viewModelScope.launch {
            try {
                val frame = withContext(Dispatchers.IO) {
                    rendererMutex.withLock {
                        closeRenderer()
                        val file = repository.getFile(doc.relativePath)
                        check(file.isFile)
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val opened = try { PdfRenderer(pfd) } catch (error: Exception) { pfd.close(); throw error }
                        descriptor = pfd; renderer = opened
                        check(opened.pageCount > 0)
                        renderFrame((initialPage - 1).coerceIn(0, opened.pageCount - 1))
                    }
                }
                mutableState.update { it.copy(bitmap = frame.first, pageIndex = frame.second,
                    pageCount = frame.third, isLoading = false,
                    notice = if (frame.second + 1 != initialPage) "The requested page is unavailable. Showing a valid page." else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(isLoading = false, error = "The PDF could not be opened on this device") } }
        }
    }

    fun showPage(index: Int) {
        val current = state.value
        if (current.isLoading || index !in 0 until current.pageCount) return
        mutableState.update { it.copy(isLoading = true, error = null) }
        renderJob = viewModelScope.launch {
            try {
                val frame = withContext(Dispatchers.IO) { rendererMutex.withLock { renderFrame(index) } }
                mutableState.update { it.copy(bitmap = frame.first, pageIndex = frame.second, pageCount = frame.third, isLoading = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(isLoading = false, error = "This PDF page could not be rendered") } }
        }
    }

    /** Called only on IO while holding the mutex. UI owners and GC release old bitmaps. */
    private fun renderFrame(index: Int): Triple<Bitmap, Int, Int> {
        val active = checkNotNull(renderer)
        val bitmap = active.openPage(index).use { page ->
            val scale = (1600f / max(page.width, page.height)).coerceAtMost(2f)
            Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
                (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawColor(Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        return Triple(bitmap, index, active.pageCount)
    }

    private fun closeRenderer() {
        renderer?.close(); renderer = null
        descriptor?.close(); descriptor = null
    }

    override fun onCleared() {
        CoroutineScope(Dispatchers.IO).launch { rendererMutex.withLock { closeRenderer() } }
    }

    class Factory(private val app: Application, private val id: Long, private val page: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PdfViewerViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return PdfViewerViewModel(app, id, page) as T
        }
    }
}

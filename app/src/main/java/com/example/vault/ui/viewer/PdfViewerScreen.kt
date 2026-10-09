package com.example.vault.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vault.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: Long,
    onNavigateBack: () -> Unit,
    onViewText: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { VaultRepository.getInstance(context) }
    val documentState by repository.getDocumentById(documentId).collectAsState(initial = null)

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var rendererRef by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfdRef by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // Clean up PdfRenderer and ParcelFileDescriptor when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            rendererRef?.close()
            pfdRef?.close()
            currentBitmap?.recycle()
        }
    }

    LaunchedEffect(documentState) {
        val doc = documentState ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val file = repository.getFile(doc.relativePath)
                if (!file.exists()) {
                    errorMessage = "Document file not found on device"
                    isLoading = false
                    return@withContext
                }

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pfdRef = pfd
                rendererRef = renderer
                totalPages = renderer.pageCount

                if (totalPages > 0) {
                    val page = renderer.openPage(0)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    currentBitmap = bmp
                }
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to load PDF: ${e.message}"
                isLoading = false
            }
        }
    }

    fun loadPage(index: Int) {
        val renderer = rendererRef ?: return
        if (index < 0 || index >= totalPages) return
        isLoading = true
        currentPageIndex = index
        try {
            val page = renderer.openPage(index)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            currentBitmap?.recycle()
            currentBitmap = bmp
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Failed to render page: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = documentState?.title ?: "PDF Viewer",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onViewText) {
                        Icon(imageVector = Icons.Default.Description, contentDescription = "View Text")
                    }
                }
            )
        },
        bottomBar = {
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { loadPage(currentPageIndex - 1) },
                        enabled = currentPageIndex > 0 && !isLoading
                    ) {
                        Text("Previous")
                    }

                    Text(
                        text = "Page ${currentPageIndex + 1} of $totalPages",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { loadPage(currentPageIndex + 1) },
                        enabled = currentPageIndex < totalPages - 1 && !isLoading
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading && currentBitmap == null -> {
                    CircularProgressIndicator()
                }
                errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
                currentBitmap != null -> {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                else -> {
                    Text("No pages in this PDF")
                }
            }
        }
    }
}

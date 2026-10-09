package com.example.vault.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vault.data.repository.VaultRepository
import com.example.vault.ocr.ExtractedPage
import com.example.vault.ocr.TextRecognitionHelper
import com.example.vault.ui.theme.Manrope
import com.example.vault.ui.theme.SupabaseGreen
import com.example.vault.ui.theme.SupabaseGreenDarker
import com.example.vault.ui.theme.ThemeState
import com.example.vault.ui.theme.VaultCardBorderDark
import com.example.vault.ui.theme.VaultCardBorderLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: Long,
    onNavigateBack: () -> Unit,
    onViewText: () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = ThemeState.isDarkState ?: systemDark
    val scope = rememberCoroutineScope()

    val repository = remember { VaultRepository.getInstance(context) }
    val documentState by repository.getDocumentById(documentId).collectAsState(initial = null)
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isExtractingText by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var rendererRef by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfdRef by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Crop mode state
    var isCropMode by remember { mutableStateOf(false) }
    var cropNormRect by remember { mutableStateOf(Rect(0.08f, 0.08f, 0.92f, 0.92f)) }

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
                    val targetW = page.width * 2
                    val targetH = page.height * 2
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(AndroidColor.WHITE)
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
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        isCropMode = false
        try {
            val page = renderer.openPage(index)
            val targetW = page.width * 2
            val targetH = page.height * 2
            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(AndroidColor.WHITE)
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

    fun applyCropAndRescanPage() {
        val current = currentBitmap ?: return
        val doc = documentState ?: return

        scope.launch {
            isExtractingText = true
            withContext(Dispatchers.IO) {
                try {
                    val pxLeft = (cropNormRect.left * current.width).toInt().coerceIn(0, current.width - 2)
                    val pxTop = (cropNormRect.top * current.height).toInt().coerceIn(0, current.height - 2)
                    val pxRight = (cropNormRect.right * current.width).toInt().coerceIn(pxLeft + 10, current.width)
                    val pxBottom = (cropNormRect.bottom * current.height).toInt().coerceIn(pxTop + 10, current.height)

                    val cropped = Bitmap.createBitmap(current, pxLeft, pxTop, pxRight - pxLeft, pxBottom - pxTop)
                    isCropMode = false

                    // Run fresh OCR on the cropped area of this page
                    val extractedText = TextRecognitionHelper.extractTextFromImageBitmap(cropped)
                    val pages = listOf(ExtractedPage(pageNumber = currentPageIndex + 1, text = extractedText))
                    repository.saveExtractedPagesForDocument(doc.id, pages)

                    withContext(Dispatchers.Main) {
                        isExtractingText = false
                        val action = snackbarHostState.showSnackbar(
                            message = "Rescanned text from cropped region! (${extractedText.length} chars)",
                            actionLabel = "View Text",
                            duration = SnackbarDuration.Short
                        )
                        if (action == SnackbarResult.ActionPerformed) {
                            onViewText()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isExtractingText = false
                        snackbarHostState.showSnackbar("Crop & rescan failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun performFullRescan() {
        val doc = documentState ?: return
        scope.launch {
            isExtractingText = true
            val ocrResult = repository.extractAndSaveTextForDocument(doc.id)
            isExtractingText = false
            if (ocrResult.isSuccess) {
                val count = ocrResult.getOrNull()?.sumOf { it.rawText.length } ?: 0
                val action = snackbarHostState.showSnackbar(
                    message = "Fresh OCR completed! ($count chars extracted)",
                    actionLabel = "View Text",
                    duration = SnackbarDuration.Short
                )
                if (action == SnackbarResult.ActionPerformed) {
                    onViewText()
                }
            } else {
                snackbarHostState.showSnackbar("OCR scan failed: ${ocrResult.exceptionOrNull()?.message}")
            }
        }
    }

    Scaffold(
        containerColor = if (isDark) Color(0xFF101010) else Color(0xFFF8F9FA),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isCropMode) "Crop Page ${currentPageIndex + 1}" else (documentState?.title ?: "PDF Viewer"),
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    if (isCropMode) {
                        IconButton(onClick = { isCropMode = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cancel Crop",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    if (isCropMode) {
                        IconButton(
                            onClick = { applyCropAndRescanPage() },
                            enabled = !isExtractingText
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Apply Crop & Rescan",
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker
                            )
                        }
                    } else {
                        // 1. Crop Page & Rescan button
                        IconButton(
                            onClick = {
                                cropNormRect = Rect(0.08f, 0.08f, 0.92f, 0.92f)
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                isCropMode = true
                            },
                            enabled = currentBitmap != null && !isExtractingText
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Crop,
                                contentDescription = "Crop & Rescan Page",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // 2. Rescan OCR button
                        IconButton(
                            onClick = { performFullRescan() },
                            enabled = !isExtractingText
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DocumentScanner,
                                contentDescription = "Rescan Document",
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker
                            )
                        }

                        // 3. View Text button
                        IconButton(onClick = onViewText) {
                            Icon(
                                imageVector = Icons.Rounded.Description,
                                contentDescription = "View Text",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) Color(0xFF101010) else Color(0xFFF8F9FA)
                )
            )
        },
        bottomBar = {
            if (!isCropMode && totalPages > 1) {
                Surface(
                    color = if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF),
                    border = BorderStroke(1.dp, if (isDark) VaultCardBorderDark else VaultCardBorderLight),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { loadPage(currentPageIndex - 1) },
                            enabled = currentPageIndex > 0 && !isLoading,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Previous", fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
                        }

                        Text(
                            text = "Page ${currentPageIndex + 1} of $totalPages",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = { loadPage(currentPageIndex + 1) },
                            enabled = currentPageIndex < totalPages - 1 && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Next", fontFamily = Manrope, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isDark) Color(0xFF0C0C0C) else Color(0xFFF1F5F3)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading && currentBitmap == null -> {
                    CircularProgressIndicator(
                        color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                    )
                }
                errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "Unknown error",
                            fontFamily = Manrope,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back", fontFamily = Manrope)
                        }
                    }
                }
                currentBitmap != null -> {
                    val currentBmp = currentBitmap!!

                    if (isCropMode) {
                        // Crop Mode for PDF page
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = currentBmp.asImageBitmap(),
                                contentDescription = "PDF Page to crop",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            InteractiveCropOverlay(
                                bitmapWidth = currentBmp.width,
                                bitmapHeight = currentBmp.height,
                                normRect = cropNormRect,
                                onNormRectChange = { cropNormRect = it },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Floating Crop & Rescan button at bottom
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 24.dp),
                                shape = RoundedCornerShape(20.dp),
                                shadowElevation = 8.dp,
                                color = Color.Transparent
                            ) {
                                Button(
                                    onClick = { applyCropAndRescanPage() },
                                    enabled = !isExtractingText,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text(
                                        text = "Crop & Rescan Page",
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard PDF Page Viewer with Zoom & Pan
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                            val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                            val bmpW = currentBmp.width.toFloat().coerceAtLeast(1f)
                            val bmpH = currentBmp.height.toFloat().coerceAtLeast(1f)

                            val fitScale = minOf(containerW / bmpW, containerH / bmpH)
                            val dispW = bmpW * fitScale
                            val dispH = bmpH * fitScale

                            fun clampOffset(s: Float, ox: Float, oy: Float): Pair<Float, Float> {
                                val maxPanX = maxOf(0f, (dispW * s - containerW) / 2f)
                                val maxPanY = maxOf(0f, (dispH * s - containerH) / 2f)
                                return Pair(
                                    ox.coerceIn(-maxPanX, maxPanX),
                                    oy.coerceIn(-maxPanY, maxPanY)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(containerW, containerH, currentBmp) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                                            scale = newScale
                                            if (newScale > 1f) {
                                                val (cx, cy) = clampOffset(newScale, offsetX + pan.x, offsetY + pan.y)
                                                offsetX = cx
                                                offsetY = cy
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                        }
                                    }
                                    .pointerInput(containerW, containerH, currentBmp) {
                                        detectTapGestures(
                                            onDoubleTap = { tapOffset ->
                                                if (scale > 1.2f) {
                                                    scale = 1f
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                } else {
                                                    val newScale = 2.5f
                                                    scale = newScale
                                                    val tapFromCenterX = tapOffset.x - containerW / 2f
                                                    val tapFromCenterY = tapOffset.y - containerH / 2f
                                                    val targetX = -tapFromCenterX * (newScale - 1f)
                                                    val targetY = -tapFromCenterY * (newScale - 1f)
                                                    val (cx, cy) = clampOffset(newScale, targetX, targetY)
                                                    offsetX = cx
                                                    offsetY = cy
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = currentBmp.asImageBitmap(),
                                    contentDescription = "PDF Page ${currentPageIndex + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offsetX,
                                            translationY = offsetY
                                        ),
                                    contentScale = ContentScale.Fit
                                )

                                // Floating Zoom Toolbar at bottom
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = if (totalPages > 1) 16.dp else 24.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = if (isDark) Color(0xDD1E1E1E) else Color(0xEEFFFFFF),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) VaultCardBorderDark else VaultCardBorderLight
                                    ),
                                    shadowElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val newScale = (scale - 0.5f).coerceAtLeast(1f)
                                                scale = newScale
                                                if (newScale == 1f) {
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                } else {
                                                    val (cx, cy) = clampOffset(newScale, offsetX, offsetY)
                                                    offsetX = cx
                                                    offsetY = cy
                                                }
                                            },
                                            modifier = Modifier.size(34.dp),
                                            enabled = scale > 1f
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ZoomOut,
                                                contentDescription = "Zoom Out",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Text(
                                            text = "${(scale * 100).toInt()}%",
                                            fontFamily = Manrope,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .clickable {
                                                    scale = 1f
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                }
                                                .padding(horizontal = 6.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                val newScale = (scale + 0.5f).coerceAtMost(5f)
                                                scale = newScale
                                                val (cx, cy) = clampOffset(newScale, offsetX, offsetY)
                                                offsetX = cx
                                                offsetY = cy
                                            },
                                            modifier = Modifier.size(34.dp),
                                            enabled = scale < 5f
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ZoomIn,
                                                contentDescription = "Zoom In",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (scale > 1f) {
                                            IconButton(
                                                onClick = {
                                                    scale = 1f
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.FitScreen,
                                                    contentDescription = "Reset Zoom",
                                                    modifier = Modifier.size(17.dp),
                                                    tint = if (isDark) SupabaseGreen else SupabaseGreenDarker
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text("No pages in this PDF", fontFamily = Manrope)
                }
            }

            // OCR Extraction Loading Overlay
            if (isExtractingText) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Rescanning text with OCR...",
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

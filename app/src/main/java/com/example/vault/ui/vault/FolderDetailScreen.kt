package com.example.vault.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vault.data.local.DocumentEntity
import com.example.vault.ui.theme.Manrope
import com.example.vault.ui.theme.SupabaseGreen
import com.example.vault.ui.theme.SupabaseGreenDarker
import com.example.vault.ui.theme.SupabaseGreenSubtle
import com.example.vault.ui.theme.ThemeState
import com.example.vault.ui.theme.VaultCardBorderDark
import com.example.vault.ui.theme.VaultCardBorderLight
import com.example.vault.ui.theme.VaultDashboardBg
import com.example.vault.ui.theme.VaultTagImgBgDark
import com.example.vault.ui.theme.VaultTagImgBgLight
import com.example.vault.ui.theme.VaultTagImgTextDark
import com.example.vault.ui.theme.VaultTagImgTextLight
import com.example.vault.ui.theme.VaultTagPdfBgDark
import com.example.vault.ui.theme.VaultTagPdfBgLight
import com.example.vault.ui.theme.VaultTagPdfTextDark
import com.example.vault.ui.theme.VaultTagPdfTextLight
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FolderSortOption(val label: String) {
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Size (Largest first)")
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

private fun formatFileType(mimeType: String): String {
    return when {
        mimeType.contains("pdf", ignoreCase = true) -> "PDF"
        mimeType.contains("png", ignoreCase = true) -> "PNG"
        mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "JPG"
        mimeType.contains("image", ignoreCase = true) -> "IMG"
        mimeType.contains("word", ignoreCase = true) || mimeType.contains("doc", ignoreCase = true) -> "DOCX"
        else -> "DOC"
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Opened just now"
        minutes < 60 -> "Opened ${minutes} min ago"
        hours < 24 -> "Opened ${hours} hr ago"
        days < 7 -> "Opened ${days} days ago"
        else -> "Opened " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    onNavigateBack: () -> Unit,
    onOpenDocument: (doc: DocumentEntity) -> Unit,
    onViewText: (docId: Long) -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = ThemeState.isDarkState ?: systemDark

    val folder by viewModel.folder.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val extractingDocumentIds by viewModel.extractingDocumentIds.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isGridView by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(FolderSortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val sortedDocuments = remember(documents, selectedSort) {
        when (selectedSort) {
            FolderSortOption.DATE_DESC -> documents.sortedByDescending { it.document.createdAt }
            FolderSortOption.DATE_ASC -> documents.sortedBy { it.document.createdAt }
            FolderSortOption.NAME_ASC -> documents.sortedBy { it.document.title.lowercase(Locale.getDefault()) }
            FolderSortOption.NAME_DESC -> documents.sortedByDescending { it.document.title.lowercase(Locale.getDefault()) }
            FolderSortOption.SIZE_DESC -> documents.sortedByDescending {
                val f = File(context.filesDir, it.document.relativePath)
                if (f.exists()) f.length() else 0L
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = folder?.name ?: "Folder",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${documents.size} ${if (documents.size == 1) "file" else "files"}",
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Button 1: Filter / Sort Button
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showSortMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) VaultDashboardBg else SupabaseGreenSubtle,
                            border = BorderStroke(1.dp, if (isDark) VaultCardBorderDark else VaultCardBorderLight)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = "Sort files",
                                    tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            FolderSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontFamily = Manrope,
                                            fontWeight = if (selectedSort == option) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    trailingIcon = {
                                        if (selectedSort == option) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedSort = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Button 2: Adjust View Button (Toggle List / Grid View)
                    Surface(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isGridView = !isGridView },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) VaultDashboardBg else SupabaseGreenSubtle,
                        border = BorderStroke(1.dp, if (isDark) VaultCardBorderDark else VaultCardBorderLight)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = if (isDark) VaultDashboardBg else SupabaseGreenSubtle
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "No files in this folder",
                        fontFamily = Manrope,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scan or import documents from the vault to add them here.",
                        fontFamily = Manrope,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (isGridView) {
                // 2-Column Grid View (like Recent Files)
                val chunks = remember(sortedDocuments) { sortedDocuments.chunked(2) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chunks, key = { row -> row.joinToString("-") { it.document.id.toString() } }) { rowDocs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowDocs.forEach { item ->
                                val doc = item.document
                                val file = remember(doc.relativePath) { File(context.filesDir, doc.relativePath) }
                                val fileSizeText = remember(file) { formatFileSize(if (file.exists()) file.length() else 0L) }
                                val fileTypeText = remember(doc.mimeType) { formatFileType(doc.mimeType) }
                                val dateText = remember(doc.createdAt) {
                                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(doc.createdAt))
                                }

                                DocumentGridCard(
                                    document = doc,
                                    fileSizeText = fileSizeText,
                                    fileTypeText = fileTypeText,
                                    formattedDateText = dateText,
                                    hasExtractedText = item.hasExtractedText,
                                    isExtracting = extractingDocumentIds.contains(doc.id),
                                    isDark = isDark,
                                    onClick = { onOpenDocument(doc) },
                                    onViewText = { onViewText(doc.id) },
                                    onExtractText = { viewModel.extractText(doc.id) },
                                    onRename = { documentToRename = doc },
                                    onDelete = { documentToDelete = doc },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowDocs.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // List View (matching the reference design)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedDocuments, key = { it.document.id }) { item ->
                        val doc = item.document
                        val file = remember(doc.relativePath) { File(context.filesDir, doc.relativePath) }
                        val fileSizeText = remember(file) { formatFileSize(if (file.exists()) file.length() else 0L) }
                        val fileTypeText = remember(doc.mimeType) { formatFileType(doc.mimeType) }
                        val relativeTimeText = remember(doc.createdAt) { formatRelativeTime(doc.createdAt) }

                        DocumentListCard(
                            document = doc,
                            fileSizeText = fileSizeText,
                            fileTypeText = fileTypeText,
                            relativeTimeText = relativeTimeText,
                            hasExtractedText = item.hasExtractedText,
                            isExtracting = extractingDocumentIds.contains(doc.id),
                            isDark = isDark,
                            onClick = { onOpenDocument(doc) },
                            onViewText = { onViewText(doc.id) },
                            onExtractText = { viewModel.extractText(doc.id) },
                            onRename = { documentToRename = doc },
                            onDelete = { documentToDelete = doc }
                        )
                    }
                }
            }
        }
    }

    documentToRename?.let { doc ->
        RenameDocumentDialog(
            currentTitle = doc.title,
            onDismiss = { documentToRename = null },
            onConfirm = { newTitle ->
                viewModel.renameDocument(doc.id, newTitle)
                documentToRename = null
            }
        )
    }

    documentToDelete?.let { doc ->
        DeleteDocumentDialog(
            documentTitle = doc.title,
            onDismiss = { documentToDelete = null },
            onConfirm = {
                viewModel.deleteDocument(doc.id)
                documentToDelete = null
            }
        )
    }
}

/**
 * List View card matching the reference image layout:
 * [Preview Thumbnail]  Title
 *                      8.8 MB • DOCX
 *                      Opened 10 min ago       [•••]
 */
@Composable
fun DocumentListCard(
    document: DocumentEntity,
    fileSizeText: String,
    fileTypeText: String,
    relativeTimeText: String,
    hasExtractedText: Boolean,
    isExtracting: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onViewText: () -> Unit,
    onExtractText: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        ),
        shadowElevation = if (isDark) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Actual preview image of the document
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                DocumentThumbnail(
                    relativePath = document.relativePath,
                    mimeType = document.mimeType,
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Information Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    fontFamily = Manrope,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "$fileSizeText • $fileTypeText",
                    fontFamily = Manrope,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = relativeTimeText,
                    fontFamily = Manrope,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // More Options Dropdown Button (•••)
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open", fontFamily = Manrope) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    if (!hasExtractedText) {
                        DropdownMenuItem(
                            text = { Text("Extract Text", fontFamily = Manrope) },
                            leadingIcon = {
                                if (isExtracting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.DocumentScanner,
                                        contentDescription = null
                                    )
                                }
                            },
                            enabled = !isExtracting,
                            onClick = {
                                menuExpanded = false
                                onExtractText()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("View / Edit Text", fontFamily = Manrope) },
                        leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onViewText()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename", fontFamily = Manrope) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", fontFamily = Manrope, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 2-Column Grid View card matching the Recent Files design:
 * [Full Preview Image Thumbnail with Color Tag]
 * Title
 * Size • Date                              [•••]
 */
@Composable
fun DocumentGridCard(
    document: DocumentEntity,
    fileSizeText: String,
    fileTypeText: String,
    formattedDateText: String,
    hasExtractedText: Boolean,
    isExtracting: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onViewText: () -> Unit,
    onExtractText: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isPdf = document.mimeType.contains("pdf", ignoreCase = true)

    val tagBg = if (isPdf) {
        if (isDark) VaultTagPdfBgDark else VaultTagPdfBgLight
    } else {
        if (isDark) VaultTagImgBgDark else VaultTagImgBgLight
    }
    val tagText = if (isPdf) {
        if (isDark) VaultTagPdfTextDark else VaultTagPdfTextLight
    } else {
        if (isDark) VaultTagImgTextDark else VaultTagImgTextLight
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        ),
        shadowElevation = if (isDark) 0.dp else 1.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Preview Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                DocumentThumbnail(
                    relativePath = document.relativePath,
                    mimeType = document.mimeType,
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize()
                )

                // Color-coded tag badge (PDF: Red, Images: Blue)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = tagBg,
                    border = BorderStroke(1.dp, tagText.copy(alpha = 0.25f))
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = fileTypeText,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = tagText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = document.title,
                fontFamily = Manrope,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Subtitle & More Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$fileSizeText • $formattedDateText",
                    fontFamily = Manrope,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open", fontFamily = Manrope) },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        if (!hasExtractedText) {
                            DropdownMenuItem(
                                text = { Text("Extract Text", fontFamily = Manrope) },
                                leadingIcon = {
                                    if (isExtracting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.DocumentScanner,
                                            contentDescription = null
                                        )
                                    }
                                },
                                enabled = !isExtracting,
                                onClick = {
                                    menuExpanded = false
                                    onExtractText()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("View / Edit Text", fontFamily = Manrope) },
                            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onViewText()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", fontFamily = Manrope) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", fontFamily = Manrope, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

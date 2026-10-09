package com.example.vault.ui.vault

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vault.data.local.DocumentEntity
import com.example.vault.data.local.FolderWithCount
import com.example.vault.data.repository.PendingDocument
import com.example.vault.scanner.DocumentScannerHelper
import com.example.vault.ui.theme.Manrope
import com.example.vault.ui.theme.SupabaseGreen
import com.example.vault.ui.theme.SupabaseGreenDarker
import com.example.vault.ui.theme.SupabaseGreenSubtle
import com.example.vault.ui.theme.ThemeState
import com.example.vault.ui.theme.VaultCardBorderDark
import com.example.vault.ui.theme.VaultCardBorderLight
import com.example.vault.ui.theme.VaultDashboardBg
import com.example.vault.ui.theme.VaultDashboardBgGradientEnd
import com.example.vault.ui.theme.VaultDashboardGaugeTrack
import com.example.vault.ui.theme.VaultDocumentGreen
import com.example.vault.ui.theme.VaultImageGreen
import com.example.vault.ui.theme.VaultTagImgBgDark
import com.example.vault.ui.theme.VaultTagImgBgLight
import com.example.vault.ui.theme.VaultTagImgTextDark
import com.example.vault.ui.theme.VaultTagImgTextLight
import com.example.vault.ui.theme.VaultTagPdfBgDark
import com.example.vault.ui.theme.VaultTagPdfBgLight
import com.example.vault.ui.theme.VaultTagPdfTextDark
import com.example.vault.ui.theme.VaultTagPdfTextLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(Locale.getDefault(), "%.0f KB", kb.coerceAtLeast(1.0))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onFolderClick: (folderId: Long) -> Unit,
    onStartSaveFlow: (PendingDocument) -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isDark = ThemeState.isDarkState ?: isSystemInDarkTheme()
    val folders by viewModel.foldersWithCount.collectAsState()
    val recentDocuments by viewModel.recentDocuments.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    // Quick Action Bottom Sheets
    var showSimilarDocsSheet by remember { mutableStateOf(false) }
    var showSortDocsSheet by remember { mutableStateOf(false) }
    var showFindDetailSheet by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()) }

    // Scanner Launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = DocumentScannerHelper.parseResult(result.data)
            val pdfUri = scanResult?.pdf?.uri
            val pageUris = scanResult?.pages?.map { it.imageUri }

            if (pdfUri != null) {
                val pending = PendingDocument(
                    uri = pdfUri,
                    mimeType = "application/pdf",
                    defaultTitle = "Scan_${dateFormat.format(Date())}"
                )
                viewModel.setPendingDocument(pending)
                onStartSaveFlow(pending)
            } else if (!pageUris.isNullOrEmpty()) {
                val pending = PendingDocument(
                    uri = pageUris.first(),
                    mimeType = "image/jpeg",
                    defaultTitle = "Scan_${dateFormat.format(Date())}"
                )
                viewModel.setPendingDocument(pending)
                onStartSaveFlow(pending)
            }
        }
    }

    // Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val pending = PendingDocument(
                uri = uri,
                mimeType = "image/jpeg",
                defaultTitle = "Photo_${dateFormat.format(Date())}"
            )
            viewModel.setPendingDocument(pending)
            onStartSaveFlow(pending)
        }
    }

    // PDF SAF Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val pending = PendingDocument(
                uri = uri,
                mimeType = "application/pdf",
                defaultTitle = "Doc_${dateFormat.format(Date())}"
            )
            viewModel.setPendingDocument(pending)
            onStartSaveFlow(pending)
        }
    }

    fun triggerScan() {
        if (activity != null) {
            DocumentScannerHelper.startScan(
                activity = activity,
                onIntentReady = { request -> scannerLauncher.launch(request) },
                onError = { err ->
                    viewModel.showMessage(
                        "Scanner not ready: ${err.localizedMessage ?: "Google Play services is downloading the scanner module. Try again shortly."}"
                    )
                }
            )
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // Filtered lists
    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) folders
        else folders.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val filteredRecent = remember(recentDocuments, searchQuery) {
        if (searchQuery.isBlank()) recentDocuments
        else recentDocuments.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
    }

    // File Counts and Storage Calculation
    val pdfCount = recentDocuments.count { it.mimeType.contains("pdf", true) }
    val imageCount = recentDocuments.count { it.mimeType.contains("image", true) }

    val totalBytes = remember(recentDocuments) {
        var sum = 0L
        for (doc in recentDocuments) {
            val file = File(context.filesDir, doc.relativePath)
            if (file.exists()) {
                sum += file.length()
            }
        }
        sum
    }
    val formattedStorage = remember(totalBytes) { formatStorageSize(totalBytes) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            VaultBottomNavigationBar(
                isDark = isDark,
                onTabClick = { tabName ->
                    when (tabName) {
                        "Vault" -> { /* Already on Vault */ }
                        "Chat" -> viewModel.showMessage("Chat screen is coming soon")
                        "Settings" -> viewModel.showMessage("Settings screen is coming soon")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(bottom = 76.dp)
            ) {
                // Top Header Row: Bolder Title with Refined Font Size + Light/Dark mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vault",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Light / Dark Mode Toggle Button
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { ThemeState.isDarkState = !isDark },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isDark) VaultCardBorderDark else VaultCardBorderLight
                        ),
                        shadowElevation = 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 1. Search Bar Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search files, folders...",
                                fontFamily = Manrope,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                            unfocusedBorderColor = if (isDark) VaultCardBorderDark else VaultCardBorderLight,
                            cursorColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Dashboard Card Row
                DashboardCard(
                    pdfCount = pdfCount,
                    imageCount = imageCount,
                    totalStorageText = formattedStorage
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Quick Action Buttons Row (3 buttons: Similar docs, Sort docs, Find detail)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Button 1: List similar documents
                    QuickActionButton(
                        title = "List similar\ndocuments",
                        icon = Icons.Rounded.AutoAwesome,
                        iconTint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                        containerColor = if (isDark) Color(0xFF16251E) else Color(0xFFEAF7F0),
                        borderColor = if (isDark) Color(0xFF233B2E) else Color(0xFFD3EFE1),
                        isDark = isDark,
                        onClick = { showSimilarDocsSheet = true },
                        modifier = Modifier.weight(1f)
                    )

                    // Button 2: Sort documents
                    QuickActionButton(
                        title = "Sort\ndocuments",
                        icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                        iconTint = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488),
                        containerColor = if (isDark) Color(0xFF132525) else Color(0xFFE6F7F7),
                        borderColor = if (isDark) Color(0xFF1D3B3A) else Color(0xFFCCEFEF),
                        isDark = isDark,
                        onClick = { showSortDocsSheet = true },
                        modifier = Modifier.weight(1f)
                    )

                    // Button 3: Find
                    QuickActionButton(
                        title = "Find",
                        icon = Icons.AutoMirrored.Rounded.ManageSearch,
                        iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        containerColor = if (isDark) Color(0xFF142433) else Color(0xFFEBF5FA),
                        borderColor = if (isDark) Color(0xFF1E384D) else Color(0xFFD4E9F7),
                        isDark = isDark,
                        onClick = { showFindDetailSheet = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 4. Recent Files Header & Horizontal Scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Files",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (filteredRecent.isEmpty()) "0 files" else "${filteredRecent.size} files",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (filteredRecent.isEmpty()) {
                        item {
                            EmptyRecentPlaceholder(
                                isDark = isDark,
                                onScanClick = { triggerScan() }
                            )
                        }
                    } else {
                        items(filteredRecent, key = { it.id }) { doc ->
                            RecentFileHorizontalCard(
                                document = doc,
                                isDark = isDark,
                                onClick = { onOpenDocument(doc) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Folders Header & Horizontal Scroll (+ add folder button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Folders",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCreateFolderDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) VaultDashboardBg else SupabaseGreenSubtle
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (isDark) SupabaseGreen else SupabaseGreenDarker
                            )
                            Text(
                                text = "add folder",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2-Column Vertical Grid for Folders
                val folderChunks = remember(filteredFolders) {
                    filteredFolders.chunked(2)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    folderChunks.forEach { rowFolders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowFolders.forEach { folder ->
                                val folderDocs = remember(recentDocuments, folder.id) {
                                    recentDocuments.filter { it.folderId == folder.id }
                                }
                                FolderGridCard(
                                    folder = folder,
                                    previewDocs = folderDocs,
                                    isDark = isDark,
                                    onClick = { onFolderClick(folder.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowFolders.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Floating Scan Action Button with title "Scan"
            ExtendedFloatingActionButton(
                onClick = { triggerScan() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 18.dp),
                containerColor = VaultDashboardBg,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        tint = SupabaseGreen,
                        modifier = Modifier.size(22.dp)
                    )
                },
                text = {
                    Text(
                        text = "Scan",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            )
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }

    // Sheet 1: List Similar Documents
    if (showSimilarDocsSheet) {
        SimilarDocumentsBottomSheet(
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { showSimilarDocsSheet = false },
            onOpenDocument = { doc ->
                showSimilarDocsSheet = false
                onOpenDocument(doc)
            }
        )
    }

    // Sheet 2: Sort Documents
    if (showSortDocsSheet) {
        SortDocumentsBottomSheet(
            viewModel = viewModel,
            folders = folders,
            isDark = isDark,
            onDismiss = { showSortDocsSheet = false }
        )
    }

    // Sheet 3: Find Detail in Documents
    if (showFindDetailSheet) {
        FindDetailBottomSheet(
            viewModel = viewModel,
            isDark = isDark,
            onDismiss = { showFindDetailSheet = false },
            onOpenDocument = { doc ->
                showFindDetailSheet = false
                onOpenDocument(doc)
            }
        )
    }
}

// ==========================================
// Quick Action Button
// ==========================================

@Composable
fun QuickActionButton(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    borderColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isDark) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = if (isDark) 0.2f else 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = iconTint
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// Dashboard Component (Images & Documents in two green hues)
// ==========================================

@Composable
fun DashboardCard(
    pdfCount: Int,
    imageCount: Int,
    totalStorageText: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(22.dp),
        color = VaultDashboardBg,
        border = BorderStroke(1.dp, Color(0x333ECF8E)),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(VaultDashboardBg, VaultDashboardBgGradientEnd)
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular gauge showing proportion between Documents & Images, displaying total storage
                DashboardStorageRing(
                    pdfCount = pdfCount,
                    imageCount = imageCount,
                    totalStorageText = totalStorageText,
                    modifier = Modifier.size(96.dp)
                )

                Spacer(modifier = Modifier.width(22.dp))

                // Breakdown list: Documents and Images only, with amounts sticking to the right
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardStatRow(
                        label = "Documents",
                        count = pdfCount,
                        dotColor = VaultDocumentGreen
                    )
                    DashboardStatRow(
                        label = "Images",
                        count = imageCount,
                        dotColor = VaultImageGreen
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardStorageRing(
    pdfCount: Int,
    imageCount: Int,
    totalStorageText: String,
    modifier: Modifier = Modifier
) {
    val totalCount = pdfCount + imageCount

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            val strokeWidth = 9.dp.toPx()

            // Draw full background track
            drawArc(
                color = VaultDashboardGaugeTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (totalCount > 0) {
                val docSweep = (pdfCount.toFloat() / totalCount) * 360f
                val imageSweep = (imageCount.toFloat() / totalCount) * 360f

                if (pdfCount > 0 && imageCount > 0) {
                    // Document arc (first green hue)
                    drawArc(
                        color = VaultDocumentGreen,
                        startAngle = -90f,
                        sweepAngle = docSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    // Image arc (second green hue)
                    drawArc(
                        color = VaultImageGreen,
                        startAngle = -90f + docSweep,
                        sweepAngle = imageSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                } else if (pdfCount > 0) {
                    drawArc(
                        color = VaultDocumentGreen,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                } else if (imageCount > 0) {
                    drawArc(
                        color = VaultImageGreen,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalStorageText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (totalStorageText.length > 7) 14.sp else 16.sp
                ),
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = "Storage",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF9CB2A6)
            )
        }
    }
}

@Composable
fun DashboardStatRow(
    label: String,
    count: Int,
    dotColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = Color.White
            )
        }

        Text(
            text = if (count == 1) "1 file" else "$count files",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

// ==========================================
// Recent Files Components (Actual Preview Image & Color-Coded Tags)
// ==========================================

@Composable
fun DocumentThumbnail(
    relativePath: String,
    mimeType: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(relativePath) { mutableStateOf<Bitmap?>(null) }
    val isPdf = mimeType.contains("pdf", ignoreCase = true)

    LaunchedEffect(relativePath) {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, relativePath)
            if (file.exists()) {
                try {
                    if (isPdf) {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        if (renderer.pageCount > 0) {
                            val page = renderer.openPage(0)
                            val targetWidth = 320
                            val targetHeight = ((page.height.toFloat() / page.width) * targetWidth).toInt().coerceIn(160, 480)
                            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            renderer.close()
                            pfd.close()

                            // If document page is wide landscape, rotate 90 degrees to fill vertical preview card nicely
                            if (bmp.width > bmp.height * 1.25f) {
                                val matrix = Matrix().apply { postRotate(90f) }
                                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                thumbnailBitmap = rotated
                            } else {
                                thumbnailBitmap = bmp
                            }
                        } else {
                            renderer.close()
                            pfd.close()
                        }
                    } else {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        val sampleSize = (options.outWidth / 320).coerceAtLeast(1)
                        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        val rawBmp = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                        if (rawBmp != null) {
                            val exif = try { ExifInterface(file.absolutePath) } catch (_: Exception) { null }
                            val orientation = exif?.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            ) ?: ExifInterface.ORIENTATION_NORMAL
                            val matrix = Matrix()
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                            var processedBmp = if (!matrix.isIdentity) {
                                Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                            } else {
                                rawBmp
                            }
                            // If photo is still wide landscape, rotate 90 degrees so it stands upright and covers portrait preview
                            if (processedBmp.width > processedBmp.height * 1.3f) {
                                val rotMatrix = Matrix().apply { postRotate(90f) }
                                processedBmp = Bitmap.createBitmap(
                                    processedBmp,
                                    0,
                                    0,
                                    processedBmp.width,
                                    processedBmp.height,
                                    rotMatrix,
                                    true
                                )
                            }
                            thumbnailBitmap = processedBmp
                        }
                    }
                } catch (_: Exception) {
                    thumbnailBitmap = null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isDark) Color(0xFF1E1E1E) else Color(0xFFF1F5F3)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Image,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isPdf) Color(0xFFDC2626) else Color(0xFF2563EB)
            )
        }
    }
}

@Composable
fun RecentFileHorizontalCard(
    document: DocumentEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val isPdf = document.mimeType.contains("pdf", ignoreCase = true)
    val formattedDate = remember(document.createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(document.createdAt))
    }

    // Color-coded tags: PDF = Red, Images = Blue
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
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        ),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
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
                    Text(
                        text = if (isPdf) "PDF" else "IMG",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        ),
                        color = tagText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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

            Text(
                text = formattedDate,
                fontFamily = Manrope,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyRecentPlaceholder(
    isDark: Boolean,
    onScanClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onScanClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF1E2B23) else SupabaseGreenSubtle
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = null,
                        tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "No files yet",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap Scan to upload docs",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// Folder Components
// ==========================================

@Composable
fun FolderPreviewSheet(
    document: DocumentEntity,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val isPdf = document.mimeType.contains("pdf", ignoreCase = true)
    val isImage = document.mimeType.contains("image", ignoreCase = true)
    val badgeBg = when {
        isPdf -> Color(0xFFDC2626)
        isImage -> Color(0xFF2563EB)
        else -> Color(0xFF0D9488)
    }
    val badgeIcon = when {
        isPdf -> Icons.Rounded.PictureAsPdf
        isImage -> Icons.Rounded.Image
        else -> Icons.Rounded.Description
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(0.5.dp, Color(0x33000000)),
        shadowElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Actual file thumbnail covering the preview sheet
            DocumentThumbnail(
                relativePath = document.relativePath,
                mimeType = document.mimeType,
                isDark = isDark,
                modifier = Modifier.fillMaxSize()
            )

            // Crisp, perfectly centered type tag badge in top-left
            Surface(
                modifier = Modifier
                    .padding(5.dp)
                    .size(17.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(4.dp),
                color = badgeBg,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderGridCard(
    folder: FolderWithCount,
    previewDocs: List<DocumentEntity>,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backColor = if (isDark) Color(0xFF142B20) else Color(0xFF16A34A)
    val frontGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1E4332), Color(0xFF132B20))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF3ECF8E), Color(0xFF24B47E))
        )
    }
    val frontBorder = if (isDark) Color(0xFF2C5E45) else Color(0xFF34D399)

    val hasFiles = previewDocs.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        if (!hasFiles) {
            // ==========================================
            // EMPTY FOLDER: Clean Closed Folder Design
            // Seamless full-height folder, no cap on top
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, frontBorder),
                shadowElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(frontGradient)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top row: Folder icon & file counter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Text(
                                text = "0 files",
                                fontFamily = Manrope,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        // Bottom info
                        Column {
                            Text(
                                text = folder.name,
                                fontFamily = Manrope,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "0 Documents",
                                fontFamily = Manrope,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // POPULATED FOLDER: Open Pocket with Previews
            // Clean Back Folder Body (NO cap on top)
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backColor)
            )

            // Real Document Previews Peeking Out from the Pocket
            if (previewDocs.size == 1) {
                // Single document preview centered
                FolderPreviewSheet(
                    document = previewDocs[0],
                    isDark = isDark,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .fillMaxWidth(0.72f)
                        .height(82.dp)
                )
            } else {
                // Two document previews overlapping (left and right)
                // Left sheet
                FolderPreviewSheet(
                    document = previewDocs[0],
                    isDark = isDark,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 8.dp)
                        .fillMaxWidth(0.60f)
                        .height(84.dp)
                )

                // Right sheet
                FolderPreviewSheet(
                    document = previewDocs[1],
                    isDark = isDark,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 10.dp, top = 16.dp)
                        .fillMaxWidth(0.58f)
                        .height(76.dp)
                )
            }

            // Front Pocket / Flap (covers the bottom half of the peeking sheets)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                    topStart = 14.dp,
                    topEnd = 14.dp
                ),
                color = Color.Transparent,
                border = BorderStroke(1.dp, frontBorder),
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(frontGradient)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top row on the pocket: folder icon & file counter badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (folder.documentCount == 1) "1 file" else "${folder.documentCount} files",
                                fontFamily = Manrope,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        // Bottom info
                        Column {
                            Text(
                                text = folder.name,
                                fontFamily = Manrope,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (folder.documentCount == 1) "1 Document" else "${folder.documentCount} Documents",
                                fontFamily = Manrope,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Quick Action Interactive Bottom Sheets
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarDocumentsBottomSheet(
    viewModel: VaultViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var promptQuery by remember { mutableStateOf("") }
    var matchedDocs by remember { mutableStateOf<List<Pair<DocumentEntity, String>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(promptQuery) {
        isSearching = true
        matchedDocs = viewModel.findSimilarDocuments(promptQuery)
        isSearching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF16251E) else Color(0xFFEAF7F0)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (isDark) SupabaseGreen else SupabaseGreenDarker,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "List Similar Documents",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enter a prompt to find related files across your vault",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = Manrope,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = promptQuery,
                onValueChange = { promptQuery = it },
                placeholder = {
                    Text(
                        "e.g. Invoices, tax receipts, scanned forms...",
                        fontFamily = Manrope,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Suggestion chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Invoices", "Receipts", "Scans", "Photos").forEach { tag ->
                    AssistChip(
                        onClick = { promptQuery = tag },
                        label = { Text(tag, fontSize = 11.sp, fontFamily = Manrope) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isDark) Color(0xFF202020) else Color(0xFFF3F5F4)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Matched Documents (${matchedDocs.size})",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                    )
                }
            } else if (matchedDocs.isEmpty()) {
                Text(
                    text = "No similar documents found for this prompt.",
                    fontFamily = Manrope,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                matchedDocs.forEach { (doc, reason) ->
                    val isPdf = doc.mimeType.contains("pdf", ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenDocument(doc) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Image,
                                contentDescription = null,
                                tint = if (isPdf) Color(0xFFDC2626) else Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = doc.title,
                                    fontFamily = Manrope,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = reason,
                                    fontFamily = Manrope,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp
                                    ),
                                    color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                                )
                            }
                        }
                        Text(
                            text = "Open",
                            fontFamily = Manrope,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) SupabaseGreen else SupabaseGreenDarker
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDocumentsBottomSheet(
    viewModel: VaultViewModel,
    folders: List<FolderWithCount>,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSorting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF132525) else Color(0xFFE6F7F7)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sort Documents",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically creates necessary folders and places documents inside",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = Manrope,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Target folders to automatically create & populate:",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            listOf(
                "📁 Receipts & Invoices" to "Invoices, bills, tax papers, payment receipts",
                "📁 Scans & Documents" to "Scanned documents, PDF reports, forms",
                "📁 Photos & Images" to "Photos, pictures, screenshots",
                "📁 General" to "All other uncategorized documents"
            ).forEach { (category, desc) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = Manrope,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (!isSorting) {
                        isSorting = true
                        viewModel.autoSortDocuments {
                            isSorting = false
                            onDismiss()
                        }
                    }
                },
                enabled = !isSorting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488),
                    contentColor = if (isDark) Color(0xFF0C1919) else Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSorting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sorting Documents...",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "Auto-Sort All Documents",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindDetailBottomSheet(
    viewModel: VaultViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<DocumentEntity, String>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            isSearching = true
            results = viewModel.searchDetails(query)
            isSearching = false
        } else {
            results = emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF142433) else Color(0xFFEBF5FA)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ManageSearch,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Find in Documents",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Find specific numbers, clauses, dates, or details",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = Manrope,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        "e.g. Account number, Total amount, Invoice date...",
                        fontFamily = Manrope,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (query.isNotBlank()) {
                Text(
                    text = "Matching files (${results.size})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        )
                    }
                } else if (results.isEmpty()) {
                    Text(
                        text = "No documents found containing \"$query\"",
                        fontFamily = Manrope,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    results.forEach { (doc, snippet) ->
                        val isPdf = doc.mimeType.contains("pdf", ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenDocument(doc) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isPdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = if (isPdf) Color(0xFFDC2626) else Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = doc.title,
                                        fontFamily = Manrope,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = snippet,
                                        fontFamily = Manrope,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "View",
                                fontFamily = Manrope,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Bottom Navigation
// ==========================================

@Composable
private fun VaultBottomNavigationBar(
    isDark: Boolean,
    onTabClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isDark) VaultCardBorderDark else VaultCardBorderLight
        ),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "Vault",
                icon = Icons.Rounded.Folder,
                isSelected = true,
                isDark = isDark,
                onClick = { onTabClick("Vault") }
            )

            BottomNavItem(
                label = "Chat",
                icon = Icons.Rounded.ChatBubbleOutline,
                isSelected = false,
                isDark = isDark,
                onClick = { onTabClick("Chat") }
            )

            BottomNavItem(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                isSelected = false,
                isDark = isDark,
                onClick = { onTabClick("Settings") }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val activeColor = if (isDark) SupabaseGreen else SupabaseGreenDarker
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (isSelected) activeColor else inactiveColor
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = if (isSelected) activeColor else inactiveColor
        )
    }
}

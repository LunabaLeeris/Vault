package com.example.vault.ui.vault

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vault.data.local.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    viewModel: FolderDetailViewModel,
    onNavigateBack: () -> Unit,
    onOpenDocument: (doc: DocumentEntity) -> Unit,
    onViewText: (docId: Long) -> Unit
) {
    val folder by viewModel.folder.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val extractingDocumentIds by viewModel.extractingDocumentIds.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "Folder") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No documents in this folder",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use the Scan or Import buttons to add your first document.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(documents, key = { it.document.id }) { item ->
                    val doc = item.document
                    DocumentCard(
                        document = doc,
                        hasExtractedText = item.hasExtractedText,
                        isExtracting = extractingDocumentIds.contains(doc.id),
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

@Composable
fun DocumentCard(
    document: DocumentEntity,
    hasExtractedText: Boolean,
    isExtracting: Boolean,
    onClick: () -> Unit,
    onViewText: () -> Unit,
    onExtractText: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isPdf = document.mimeType.contains("pdf", ignoreCase = true)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(document.createdAt) { dateFormat.format(Date(document.createdAt)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isPdf) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (isPdf) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = if (isPdf) "PDF" else "IMAGE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (isExtracting) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Extracting...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (hasExtractedText) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Text Extracted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "No Text",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    if (!hasExtractedText) {
                        DropdownMenuItem(
                            text = { Text("Extract Text") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null
                                )
                            },
                            enabled = !isExtracting,
                            onClick = {
                                menuExpanded = false
                                onExtractText()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("View / Edit Text") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onViewText()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
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

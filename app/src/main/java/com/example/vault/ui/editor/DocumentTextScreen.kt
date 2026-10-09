package com.example.vault.ui.editor

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
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.vault.data.local.DocumentPageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentTextScreen(
    viewModel: DocumentTextViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.document?.title?.let { "Text: $it" } ?: "Document Text",
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
                    if (uiState.pages.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Re-run OCR") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.extractText()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Extracted Text") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isExtracting -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.extractionProgress,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.pages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Text Extracted",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Extract text from this document using on-device ML Kit OCR.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = viewModel::extractText) {
                            Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract Text Now")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.pages, key = { it.id }) { page ->
                            PageTextCard(
                                page = page,
                                onSavePage = { updatedText ->
                                    viewModel.updatePageText(page.id, updatedText)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Extracted Text") },
            text = {
                Text("This will permanently remove the extracted text from the database and search index. The original file will remain completely intact.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExtractedText()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PageTextCard(
    page: DocumentPageEntity,
    onSavePage: (String) -> Unit
) {
    var textValue by remember(page.id, page.content) { mutableStateOf(page.content) }
    val isModified = textValue != page.content

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page ${page.pageNumber}",
                    style = MaterialTheme.typography.titleMedium
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (page.editedText != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (page.editedText != null) "User Edited" else "Original OCR",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (page.editedText != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text("Extracted Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 15
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isModified) {
                    OutlinedButton(onClick = { textValue = page.content }) {
                        Text("Reset")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = { onSavePage(textValue) },
                    enabled = isModified
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Page")
                }
            }
        }
    }
}

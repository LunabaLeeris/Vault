package com.example.vault.ui.viewer

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: Long, onNavigateBack: () -> Unit, onViewText: () -> Unit,
    initialPageNumber: Int = 1, expectedRevision: Long = 0, sourceEdited: Boolean = false,
) {
    val app = LocalContext.current.applicationContext as Application
    val factory = remember(app, documentId, initialPageNumber) { PdfViewerViewModel.Factory(app, documentId, initialPageNumber) }
    val model: PdfViewerViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(state.document?.title ?: "PDF Viewer", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onViewText) { Icon(Icons.Default.Description, "View Text") } })
        },
        bottomBar = {
            if (state.pageCount > 0) Row(Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { model.showPage(state.pageIndex - 1) }, enabled = state.pageIndex > 0 && !state.isLoading) { Text("Previous") }
                Text("Page ${state.pageIndex + 1} of ${state.pageCount}", Modifier.testTag("pdf-page-status"))
                Button(onClick = { model.showPage(state.pageIndex + 1) },
                    enabled = state.pageIndex < state.pageCount - 1 && !state.isLoading) { Text("Next") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ReferenceNotice(state.document?.textRevision, expectedRevision, sourceEdited)
            state.notice?.let { Text(it, Modifier.padding(8.dp)) }
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                when {
                    state.error != null -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = onNavigateBack) { Text("Go Back") }
                    }
                    state.isLoading -> CircularProgressIndicator()
                    state.bitmap != null -> Image(state.bitmap!!.asImageBitmap(), "PDF page ${state.pageIndex + 1}",
                        Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

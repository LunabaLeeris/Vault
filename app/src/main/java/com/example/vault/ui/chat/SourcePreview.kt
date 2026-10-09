package com.example.vault.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun SourcePreview(preview: SourcePreviewState, onDismiss: () -> Unit) {
    val source = preview.source
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("source-preview"),
        title = { Text("${source.document.title} · page ${source.requestedPageNumber}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (source.revisionChanged) Text("Extracted text changed after this answer.")
                if (source.page?.isEdited == true) Text("Edited text. This preview does not show an exact image highlight.")
                SelectionContainer { Text(source.page?.text?.takeIf { it.isNotBlank() }
                    ?: "Extracted text is no longer available. The original document can still exist.") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

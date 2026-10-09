package com.example.vault.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReferenceNotice(currentRevision: Long?, expectedRevision: Long, edited: Boolean) {
    val changed = currentRevision != null && expectedRevision > 0 && currentRevision != expectedRevision
    if (!changed && !edited) return
    Column(Modifier.padding(8.dp)) {
        if (changed) Text("Document text changed after this answer.", style = MaterialTheme.typography.bodySmall)
        if (edited) Text("The answer used edited text. This view shows the original document.", style = MaterialTheme.typography.bodySmall)
    }
}

package com.example.vault.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.vault.rag.model.Evidence

@Composable
fun SourceCard(evidence: Evidence, enabled: Boolean, onClick: () -> Unit) {
    val chunk = evidence.chunk
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("source-${evidence.sourceId}")) {
        Column(Modifier.padding(12.dp)) {
            Text("[${evidence.sourceId}] ${chunk.title}", style = MaterialTheme.typography.titleSmall)
            Text("Page ${chunk.pageNumber} · revision ${chunk.revision}" + if (chunk.isEdited) " · edited text" else "",
                style = MaterialTheme.typography.labelSmall)
            Text(chunk.text, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            Text("Open source", style = MaterialTheme.typography.labelLarge)
        }
    }
}

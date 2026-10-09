package com.example.vault.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.vault.rag.model.AnswerMode
import com.example.vault.rag.model.Evidence
import com.example.vault.rag.model.SearchScope

@Composable
fun ChatScreen(
    state: ChatUiState, corpusLabel: String, maxQuestionChars: Int,
    onDraftChange: (String) -> Unit, onSend: () -> Unit, onCancel: () -> Unit, onRefresh: () -> Unit,
    onScopeChange: (SearchScope) -> Unit, onModelModeChange: (Boolean) -> Unit, onRetry: () -> Unit,
    onDismissError: () -> Unit, onOpenSource: (Evidence) -> Unit,
) {
    val listState = rememberLazyListState()
    var scopeMenu by remember { mutableStateOf(false) }
    val scopeLabel = when (val scope = state.scope) {
        SearchScope.All -> "All documents"
        is SearchScope.Document -> state.documents.find { it.id == scope.id }?.title ?: "Unavailable document"
        is SearchScope.Folder -> state.documents.find { it.folderId == scope.id }?.folderName ?: "Unavailable folder"
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.answer) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(16.dp)) {
            Text("Vault chat", style = MaterialTheme.typography.headlineSmall)
            Text("$corpusLabel · ${state.documents.size} files · offline", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = { scopeMenu = true }, enabled = !state.isBusy) { Text("Search: $scopeLabel") }
                    DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false }) {
                        DropdownMenuItem(text = { Text("All documents") }, onClick = { onScopeChange(SearchScope.All); scopeMenu = false })
                        state.documents.distinctBy { it.folderId }.forEach { document ->
                            DropdownMenuItem(text = { Text("Folder: ${document.folderName}") }, onClick = {
                                onScopeChange(SearchScope.Folder(document.folderId)); scopeMenu = false
                            })
                        }
                        state.documents.forEach { document ->
                            DropdownMenuItem(text = { Text(document.title) }, onClick = {
                                onScopeChange(SearchScope.Document(document.id)); scopeMenu = false
                            })
                        }
                    }
                }
                TextButton(onClick = onRefresh, enabled = !state.isBusy) { Text("Refresh") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.useLocalModel, onCheckedChange = onModelModeChange,
                    enabled = state.hasLocalModel && !state.isBusy, modifier = Modifier.testTag("model-mode"))
                Text(if (state.hasLocalModel) "Use local model" else "Excerpt mode · no model installed",
                    modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
                Row {
                    if (state.canRetry) TextButton(onClick = onRetry, enabled = !state.isBusy) { Text("Retry") }
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
            if (state.messages.isEmpty()) Text("Ask about the documents. Try: When is rent due?", Modifier.padding(vertical = 8.dp))
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You: ${message.question}", style = MaterialTheme.typography.titleSmall)
                        message.answer?.let { answer ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(when (answer.mode) {
                                        AnswerMode.EXCERPTS -> "Document excerpts"
                                        AnswerMode.LOCAL_MODEL -> "Local model answer"
                                        AnswerMode.NO_EVIDENCE -> "No evidence found"
                                    }, style = MaterialTheme.typography.labelLarge)
                                    Text(answer.text)
                                    answer.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            answer.references.forEach { evidence ->
                                SourceCard(evidence, enabled = !state.isBusy, onClick = { onOpenSource(evidence) })
                            }
                        }
                    }
                }
            }
            if (state.isBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("chat-loading"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.status.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else if (state.status != null) {
                Text(state.status, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = state.draft, onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f).testTag("question-input"), label = { Text("Ask a question") },
                    enabled = !state.isBusy, maxLines = 3, supportingText = { Text("${state.draft.length}/$maxQuestionChars") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!state.isBusy) onSend() }))
                Button(onClick = onSend, enabled = !state.isBusy && state.draft.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp).width(80.dp).testTag("send-question")) { Text("Send") }
            }
        }
    }
}

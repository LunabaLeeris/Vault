package com.example.vault.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vault.rag.RagDependencies
import com.example.vault.rag.model.SourceNavigationRequest

/** Embed this route in the navigation graph and supply the Milestone 3 viewer callback. */
@Composable
fun ChatRoute(
    dependencies: RagDependencies,
    onOpenDocument: ((SourceNavigationRequest) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
) {
    val factory = remember(dependencies) { ChatViewModel.Factory(dependencies) }
    val model: ChatViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    ChatScreen(
        state = state, corpusLabel = model.corpusLabel, maxQuestionChars = model.maxQuestionChars,
        onDraftChange = model::setDraft, onSend = model::send, onCancel = model::cancel,
        onRefresh = model::refresh, onScopeChange = model::setScope,
        onModelModeChange = model::setModelMode, onRetry = model::retry,
        onDismissError = model::dismissError, onOpenSource = { model.openSource(it, onOpenDocument) },
        onNavigateBack = onNavigateBack,
    )
    state.preview?.let { SourcePreview(it, onDismiss = model::closePreview) }
}

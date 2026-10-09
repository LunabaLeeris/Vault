package com.example.vault.rag

import com.example.vault.data.repository.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RagSyncCoordinator(repository: VaultRepository, pipeline: RagPipeline, scope: CoroutineScope) {
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val job = scope.launch {
        repository.observeDocumentChanges().collect {
            try { pipeline.refresh(); mutableError.value = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableError.value = "Retrieval refresh failed. The next question will retry." }
        }
    }
    fun close() { job.cancel() }
}

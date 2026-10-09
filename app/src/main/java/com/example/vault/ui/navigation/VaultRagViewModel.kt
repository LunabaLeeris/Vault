package com.example.vault.ui.navigation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vault.data.repository.VaultRepository
import com.example.vault.rag.RagDependencies
import com.example.vault.rag.RagSyncCoordinator

/** One production pipeline survives activity recreation alongside the navigation graph. */
class VaultRagViewModel(application: Application) : AndroidViewModel(application) {
    val repository = VaultRepository.getInstance(application)
    val dependencies = RagDependencies(application)
    private val coordinator = RagSyncCoordinator(repository, dependencies.pipeline, viewModelScope)
    override fun onCleared() { coordinator.close() }
}

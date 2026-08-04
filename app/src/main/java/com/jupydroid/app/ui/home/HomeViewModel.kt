package com.jupydroid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(val notebooks: List<NotebookSummary>)

class HomeViewModel(private val notebookRepository: NotebookRepository) : ViewModel() {
  val uiState: StateFlow<HomeUiState> =
    notebookRepository.observeNotebooks()
      .map { HomeUiState(it) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState(emptyList()))

  fun createNotebook(title: String, kernelId: String) {
    viewModelScope.launch { notebookRepository.createNotebook(title, kernelId) }
  }

  fun renameNotebook(id: String, title: String) {
    viewModelScope.launch { notebookRepository.renameNotebook(id, title) }
  }

  fun deleteNotebook(id: String) {
    viewModelScope.launch { notebookRepository.deleteNotebook(id) }
  }
}

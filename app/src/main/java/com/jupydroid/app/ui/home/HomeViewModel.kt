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

  /**
   * A refusal leaves the list as it was rather than crashing.
   *
   * Since X1b a rename can fail - the name is the file, so it can be invalid or already taken -
   * and an exception thrown inside `viewModelScope.launch` is uncaught and fatal. The list is
   * rebuilt from disk by the repository, so there is nothing to roll back here; not dying is the
   * whole job.
   */
  fun renameNotebook(id: String, title: String) {
    viewModelScope.launch { runCatching { notebookRepository.renameNotebook(id, title) } }
  }

  fun deleteNotebook(id: String) {
    viewModelScope.launch { notebookRepository.deleteNotebook(id) }
  }
}

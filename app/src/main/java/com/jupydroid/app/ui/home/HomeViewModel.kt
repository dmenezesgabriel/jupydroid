package com.jupydroid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(val notebooks: List<NotebookSummary>)

class HomeViewModel(notebookRepository: NotebookRepository) : ViewModel() {
  val uiState: StateFlow<HomeUiState> =
    notebookRepository.observeNotebooks()
      .map { HomeUiState(it) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState(emptyList()))
}

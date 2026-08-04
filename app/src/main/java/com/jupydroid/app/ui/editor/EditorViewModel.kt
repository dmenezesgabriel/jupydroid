package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorUiState(
  val title: String = "",
  val cells: List<Cell> = emptyList(),
  val loaded: Boolean = false,
  val error: String? = null,
)

class EditorViewModel(
  notebookId: String,
  private val notebookRepository: NotebookRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      _uiState.value =
        try {
          val notebook = notebookRepository.readNotebook(notebookId)
          EditorUiState(title = notebook.title, cells = notebook.cells, loaded = true)
        } catch (e: IllegalArgumentException) {
          EditorUiState(error = e.message)
        }
    }
  }
}

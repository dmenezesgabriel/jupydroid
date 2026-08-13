package com.jupydroid.app.ui.fileeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.pathAfterRename
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.syntax.syntaxForFile
import com.jupydroid.app.ui.editor.NotebookSaveDebounce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the editor is showing of one file.
 *
 * @property name the last segment of the path, which is what the bar shows -
 *   `PathExt.basename(sender.localPath)`, as the notebook editor's title does.
 * @property syntax chosen from the file's own name, so a `.py` colours as Python and a `.csv`
 *   renders plainly rather than being refused.
 */
data class FileEditorUiState(
  val path: String = "",
  val name: String = "",
  val text: String = "",
  val syntax: SyntaxHighlighter = syntaxForFile(""),
  val loaded: Boolean = false,
  val error: String? = null,
)

/**
 * One file, one text field - the reference's file editor, which is what opens anything the
 * notebook factory does not claim (`packages/fileeditor`).
 *
 * ```
 * FileEditorViewModel("reports/data.csv", workspaceRepository, applicationScope)
 * ```
 *
 * @param applicationScope for the write that must finish after the screen is gone: closing the
 *   file flushes its last edit from here, because `viewModelScope` is already cancelled by then -
 *   the same arrangement `EditorViewModel` uses.
 */
class FileEditorViewModel(
  /**
   * Where this file lives - **mutable**, because a rename made anywhere moves it.
   *
   * `Context._path`, which `_updatePath` reassigns in place while the open widget carries on.
   */
  private var path: String,
  private val workspaceRepository: WorkspaceRepository,
  private val applicationScope: CoroutineScope,
) : ViewModel() {
  private val _uiState = MutableStateFlow(FileEditorUiState(path = path, name = notebookLeafOf(path)))
  val uiState: StateFlow<FileEditorUiState> = _uiState

  private val save = NotebookSaveDebounce(viewModelScope) { persist() }

  init {
    viewModelScope.launch {
      // A shared workspace can lose a file under the user - a cell can `os.remove` it - so a read
      // that fails is an ordinary event to report, not a corrupt state to crash on.
      runCatching { workspaceRepository.readText(path) }
        .onSuccess { text ->
          _uiState.update { it.copy(text = text, syntax = syntaxForFile(it.name), loaded = true, error = null) }
        }
        .onFailure { failure -> _uiState.update { it.copy(error = failure.message, loaded = false) } }
    }
    // `Context._onFileChanged`: the open document follows its file rather than waiting to be told
    // by whichever screen moved it. Without this the next write re-creates the old path.
    viewModelScope.launch {
      workspaceRepository.renames.collect { moved ->
        pathAfterRename(path, moved.from, moved.to)?.let { adopt(it) }
      }
    }
  }

  /** Takes on where the file lives now; the document did not change, only its path. */
  private fun adopt(moved: String) {
    path = moved
    _uiState.update { it.copy(path = moved, name = notebookLeafOf(moved)) }
  }

  /** Takes an edit from the field. Written back on a debounce, as a cell's source is. */
  fun edit(text: String) {
    _uiState.update { it.copy(text = text) }
    save.schedule()
  }

  public override fun onCleared() {
    val hadUnsavedWork = save.cancelPending()
    if (hadUnsavedWork) applicationScope.launch { persist() }
    super.onCleared()
  }

  private suspend fun persist() {
    if (!_uiState.value.loaded) return
    workspaceRepository.writeText(path, _uiState.value.text)
  }
}

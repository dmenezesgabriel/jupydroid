package com.jupydroid.app.ui.fileeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.WorkspaceChange
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
  /** The file was deleted, so the screen showing it should close - see `EditorUiState.gone`. */
  val gone: Boolean = false,
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

  /** Set when the file this editor is on is deleted; see [stopWriting]. */
  private var fileIsGone = false

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
      workspaceRepository.changes.collect { change ->
        when (change) {
          is WorkspaceChange.Renamed -> pathAfterRename(path, change.from, change.to)?.let { adopt(it) }
          is WorkspaceChange.Deleted -> if (change.path == path) stopWriting()
        }
      }
    }
  }

  /** Takes on where the file lives now; the document did not change, only its path. */
  private fun adopt(moved: String) {
    path = moved
    _uiState.update { it.copy(path = moved, name = notebookLeafOf(moved)) }
  }

  /**
   * The file is gone, so nothing more goes to it - not from the debounce, not on the way out.
   *
   * `SaveHandler.dispose()` clears its timer and writes nothing, and it is wired to
   * `context.disposed` (`docmanager/src/savehandler.ts:29,62-69`). Ours has to say so out loud,
   * because [onCleared] *does* write: a farewell flush after a delete puts the file back.
   */
  private fun stopWriting() {
    fileIsGone = true
    save.cancelPending()
    _uiState.update { it.copy(gone = true) }
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
    if (fileIsGone || !_uiState.value.loaded) return
    workspaceRepository.writeText(path, _uiState.value.text)
  }
}

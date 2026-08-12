package com.jupydroid.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.UNTITLED_NOTEBOOK
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Browsing the workspace one directory at a time.
 *
 * A single pane that replaces its contents as you descend, with a breadcrumb above it - which is
 * what JupyterLab's file browser is (`BreadCrumbs` + `DirListing`), and what a phone wants anyway:
 * a tree's indentation would spend the width the names need.
 *
 * ```
 * ExplorerViewModel(workspaceRepository).open(entry)
 * ```
 */
class ExplorerViewModel(
  private val workspaceRepository: WorkspaceRepository,
  /**
   * For the one type the workspace cannot make on its own: a notebook is nbformat, not an empty
   * file with an `.ipynb` name. `new_untitled` gives `type: "notebook"` its own branch and the
   * server writes content into it (`services/contents/manager.py:605-607`).
   */
  private val notebookRepository: NotebookRepository,
  private val rootLabel: String = ROOT_LABEL,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ExplorerUiState())
  val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

  init {
    goTo("")
  }

  /**
   * Descends into a directory, or hands a file to [onOpenFile].
   *
   * The explorer does not decide what opens a file - a notebook and a `.py` are different
   * documents with different editors, as they are in the reference, where the browser asks the
   * document manager to open a path and the factory is chosen from it.
   */
  fun open(entry: WorkspaceEntry, onOpenFile: (WorkspaceEntry) -> Unit = {}) {
    if (entry.kind == WorkspaceEntryKind.DIRECTORY) goTo(entry.path) else onOpenFile(entry)
  }

  /** Up one level, which the reference calls `filebrowser:go-up` and binds to Backspace. */
  fun goUp() {
    goTo(_uiState.value.path.substringBeforeLast('/', missingDelimiterValue = ""))
  }

  /**
   * Lists [path] and shows it, or reports why it could not.
   *
   * A directory can vanish under the explorer - the workspace is shared with the kernel, and a
   * cell can remove the folder being looked at - so a failure leaves the view where it was and
   * says so, rather than emptying the screen or throwing.
   */
  fun goTo(path: String) {
    viewModelScope.launch {
      runCatching { workspaceRepository.list(path) }
        .onSuccess { entries ->
          _uiState.value = ExplorerUiState(
            path = path,
            entries = entries,
            crumbs = crumbsFor(path, rootLabel),
            error = null,
          )
        }
        .onFailure { failure ->
          _uiState.value = _uiState.value.copy(error = failure.message ?: "could not open '$path'")
        }
    }
  }

  private companion object {
    /** What the root crumb is called, since its path is the empty string. */
    const val ROOT_LABEL = "Workspace"
  }

  /**
   * Creates a file of [extension] in the directory being browsed.
   *
   * The *directory being browsed*, because that is what `_createNew` passes - `path:
   * this.model.path` (`packages/filebrowser/src/browser.ts:558-566`). The name is the server's,
   * never ours: `untitled` plus the extension.
   */
  fun createFile(extension: String) = create { workspaceRepository.createFile(_uiState.value.path, extension) }

  /** `Untitled Folder`, counting with a space - the server's `insert` for a directory. */
  fun createFolder() = create { workspaceRepository.createFolder(_uiState.value.path) }

  /** `Untitled.ipynb`, written as real nbformat so that opening it is not an error. */
  fun createNotebook() = create {
    val directory = _uiState.value.path
    notebookRepository.createNotebook(
      if (directory.isEmpty()) UNTITLED_NOTEBOOK else "$directory/$UNTITLED_NOTEBOOK",
      DEFAULT_KERNEL_ID,
    ).id
  }

  /**
   * Runs a creation and shows the result, or reports why it did not happen.
   *
   * Listing again rather than appending: the workspace is shared, so the directory may have
   * changed for other reasons since it was last read, and a re-read is the only honest answer.
   */
  private fun create(creating: suspend () -> String) {
    viewModelScope.launch {
      runCatching { creating() }
        .onSuccess { goTo(_uiState.value.path) }
        .onFailure { failure -> _uiState.update { it.copy(error = failure.message) } }
    }
  }
}

package com.jupydroid.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.UNTITLED_NOTEBOOK
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.shownWhileBrowsing
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepository
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
  /**
   * What was opened, which no listing of the tree can answer. Its own port because a recent is a
   * record of an act where a listing is a record of a tree - and because it remembers plain files
   * and folders too.
   */
  private val recentDocuments: RecentDocumentsRepository,
  private val rootLabel: String = ROOT_LABEL,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ExplorerUiState())
  val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

  /**
   * What a pending overwrite would move, since the answer arrives long after the question.
   *
   * The state carries the *name* because that is what the dialog says; the path it would move is
   * bookkeeping, and putting it on screen state would be putting it somewhere it is never read.
   */
  private var renamingPath: String? = null

  /**
   * The whole list, kept apart from what is on screen: [shownWhileBrowsing] decides how much of it
   * a given directory shows, and it must not be re-derived from a truncated copy.
   */
  private var recentlyOpened: List<RecentDocument> = emptyList()

  init {
    goTo("")
    viewModelScope.launch {
      recentDocuments.observeRecents().collect { opened ->
        recentlyOpened = opened
        _uiState.update { it.copy(recents = opened.shownWhileBrowsing(it.path)) }
      }
    }
  }

  /**
   * A recent folder moves the browser; only a document is handed to an editor.
   *
   * The reference's `openRecent` makes the same split - a `contentType: 'directory'` goes to the
   * file browser's `openPath` and everything else to `docmanager:open`
   * (`packages/mainmenu-extension/src/recents.ts`). Which editor a document gets is not decided
   * here, for the same reason [open] does not decide it.
   */
  fun openRecent(document: RecentDocument, onOpenFile: (String) -> Unit) {
    if (document.kind == WorkspaceEntryKind.DIRECTORY) goTo(document.path) else onOpenFile(document.path)
  }

  /** `docmanager:clear-recents`, which the reference offers from the same submenu. */
  fun clearRecents() {
    viewModelScope.launch { recentDocuments.clear() }
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

  /**
   * Lists [path] and shows it, or reports why it could not.
   *
   * A directory can vanish under the explorer - the workspace is shared with the kernel, and a
   * cell can remove the folder being looked at - so a failure leaves the view where it was and
   * says so, rather than emptying the screen or throwing.
   */
  /**
   * @param justCreated what a creation just made, so the screen can scroll to it - carried *into*
   *   the listing rather than set after it. This launches its own coroutine, so setting it
   *   afterwards raced the listing and lost: the fresh state built here overwrote it, audit A3's
   *   row stayed below the fold, and every unit test stayed green. The flow is what caught it.
   */
  fun goTo(path: String, justCreated: String? = null) {
    viewModelScope.launch {
      runCatching { workspaceRepository.list(path) }
        .onSuccess { entries ->
          _uiState.value = ExplorerUiState(
            path = path,
            entries = entries,
            crumbs = crumbsFor(path, rootLabel),
            error = null,
            recents = recentlyOpened.shownWhileBrowsing(path),
            justCreated = justCreated,
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
   * Creates one [NewItem] in the directory being browsed.
   *
   * **One typed call, not one method per menu item** - which is the reference's own shape:
   * `_createNew` takes `{path, type}` and the server's `new_untitled` branches on the type
   * (`packages/filebrowser/src/browser.ts:529-566`, `services/contents/manager.py:599-614`).
   * Three methods here were three spellings of one act, and detekt counting them was right.
   *
   * The *directory being browsed*, because that is what `_createNew` passes - `path:
   * this.model.path`. Every name is the server's, never ours: `untitled.py`, `Untitled Folder`
   * with its counting space, `Untitled.ipynb` written as real nbformat so that opening it is not
   * an error. A notebook goes through the notebook repository rather than
   * `createFile("ipynb")` for that last reason - an empty file with an `.ipynb` name is not one.
   */
  fun create(item: NewItem) = relistAfter {
    val directory = _uiState.value.path
    when (item) {
      NewItem.PYTHON_FILE -> workspaceRepository.createFile(directory, "py")
      NewItem.FOLDER -> workspaceRepository.createFolder(directory)
      NewItem.NOTEBOOK -> notebookRepository.createNotebook(
        if (directory.isEmpty()) UNTITLED_NOTEBOOK else "$directory/$UNTITLED_NOTEBOOK",
        DEFAULT_KERNEL_ID,
      ).id
    }
  }

  /**
   * Lists the current directory again.
   *
   * The workspace is **shared** - the kernel's `HOME` since X1a - so what is on screen goes stale
   * without anything here doing wrong: an editor writes the notebook the browser is showing, an
   * import lands a file, a cell writes beside its own notebook. The reference keeps its browser
   * fresh with a poll (`FileBrowserModel`, `DEFAULT_REFRESH_INTERVAL = 10000`,
   * `standby: 'when-hidden'`); ours refreshes when the screen comes back, which is the moment that
   * matters on a phone showing one thing at a time. See "Known residual risks" for the case that
   * leaves open.
   *
   * A6 is why this is not optional: an edit must move the row's time, and the row is here now.
   */
  fun refresh() {
    // Recents is re-checked here as well as re-listed: an entry whose file has since been renamed
    // or deleted points at nothing, and the reference drops exactly those - `_isValid` treats a
    // 404 as invalid and `RecentsMenu.onBeforeAttach` re-validates every time the list is about to
    // be shown (`mainmenu-extension/src/recents.ts`). Ours is always on screen, so "about to be
    // shown" is this.
    viewModelScope.launch { recentDocuments.revalidate() }
    goTo(_uiState.value.path)
  }

  /**
   * Renames [entry] to [name], or asks before replacing something already called that.
   *
   * The reference renames from the row itself and joins the typed name onto the directory being
   * browsed (`PathExt.join(this._model.path, newName)`, `listing.ts:2621`); the repository does
   * that joining, so a name is only ever a name here.
   */
  fun rename(entry: WorkspaceEntry, name: String) = move(entry.path, name) {
    workspaceRepository.rename(entry.path, name)
  }

  /**
   * Answers "already exists, overwrite?" - yes replaces what is there, no leaves both alone.
   *
   * One method taking the answer rather than two named for the outcomes, which is the shape of
   * `shouldOverwrite(path): Promise<boolean>` with `renameFile` acting on the boolean, and the
   * shape `EditorViewModel` already answers this same question in.
   */
  fun answerOverwrite(accepted: Boolean) {
    val taken = _uiState.value.nameTaken ?: return
    val renaming = renamingPath
    // Cleared either way: the question has been answered, and leaving it up would ask it again.
    _uiState.update { it.copy(nameTaken = null) }
    if (accepted && renaming != null) {
      move(renaming, taken) { workspaceRepository.overwrite(renaming, taken) }
    }
  }

  /**
   * Deletes [entry], permanently, and shows what is left.
   *
   * Asked about first - the confirmation belongs to the screen, as `DirListing.delete` puts it in
   * front of `_delete` (`listing.ts:451-500`). A folder with anything in it comes back as an
   * error, because the server sharing this workspace refuses that too.
   */
  fun delete(entry: WorkspaceEntry) = relistAfter {
    workspaceRepository.delete(entry.path)
    // Nothing to bring into view: what it named is gone.
    ""
  }

  /**
   * Runs a change to the directory and shows the result, or reports why it did not happen.
   *
   * Listing again rather than patching the rows: the workspace is shared, so the directory may
   * have changed for other reasons since it was last read, and a re-read is the only honest
   * answer.
   */
  private fun relistAfter(changing: suspend () -> String) {
    viewModelScope.launch {
      runCatching { changing() }
        // Named so the listing can bring it into view - the reference does the same by other
        // means: `_createNew` hands the result to `rename()`, which selects and scrolls to it.
        // Cleared by the next plain `goTo`, which builds a fresh state, so nothing has to
        // remember to put it down again.
        .onSuccess { created -> goTo(_uiState.value.path, justCreated = created) }
        .onFailure { failure -> _uiState.update { it.copy(error = failure.message) } }
    }
  }


  /**
   * Moves [from] to [name] and re-lists, or says why it did not happen.
   *
   * The split between the two refusals is the reference's: only a taken name becomes
   * `"%1" already exists, overwrite?`, everything else rethrows (`renameFile`,
   * `docmanager/src/dialogs.ts`). The name comes from the caller rather than out of the
   * exception, the way `EditorViewModel.applyRename` keeps the title it asked for.
   */
  private fun move(from: String, name: String, moving: suspend () -> String) {
    renamingPath = from
    viewModelScope.launch {
      runCatching { moving() }
        .onSuccess { goTo(_uiState.value.path) }
        .onFailure { failure ->
          _uiState.update {
            if (failure is FileNameTakenException) {
              it.copy(nameTaken = name)
            } else {
              it.copy(error = failure.message)
            }
          }
        }
    }
  }
}

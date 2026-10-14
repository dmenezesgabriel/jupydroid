package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StampedNotebook
import com.jupydroid.app.domain.port.FileChangedOnDiskException
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The three answers to "this notebook's file changed on disk" - [EditorUiState.saveConflict].
 *
 * `Context._raiseConflict`'s dialog (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`):
 * Overwrite saves anyway, Revert loads the disk's version, and Cancel rejects that one save and
 * leaves the document owing it. No reference merges the two, and none is attempted here.
 *
 * Split from [EditorViewModel] the way its other collaborators are, sharing only the state flow.
 * The notebook and its stamp stay the ViewModel's, so the two acts that need them are handed in.
 *
 * ```
 * if (state.saveConflict) FileChangedDialog(state.title, conflict::overwrite, conflict::revert, conflict::cancel)
 * ```
 */
class NotebookSaveConflict(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val scope: CoroutineScope,
  private val repository: NotebookRepository,
  /** Asked for rather than captured: a rename moves the file while the document carries on. */
  private val notebookId: () -> String,
  /** The ViewModel's own write lock, so a revert never lands in the middle of a save. */
  private val writes: Mutex,
  /** The save, made whatever is on disk. */
  private val saveOver: suspend () -> Unit,
  /**
   * Cancels the debounced write an edit armed. A revert drops that edit, and the write must not
   * follow it: it would put the bytes just read back under a new modification time, which every
   * other holder of the file reads as a change (ADR-0096).
   */
  private val dropOwedWrite: () -> Unit,
  /** Hands the ViewModel what was read, so the next save expects the file as it is now. */
  private val adopt: (StampedNotebook) -> Unit,
  /** The stamp the ViewModel holds - what it last read or wrote - or null before the notebook has loaded. */
  private val held: () -> FileStamp?,
) {
  /** The editor's version wins: what is on disk is replaced, which is what was asked. */
  fun overwrite() {
    cancel()
    scope.launch { saveOver() }
  }

  /** The disk's version wins: the cells are read back and the edits that were owed are dropped. */
  fun revert() {
    dropOwedWrite()
    scope.launch { writes.withLock { reread() } }
  }

  /** Neither yet: the edit stays owed and the file stays as it is, and the next save asks again. */
  fun cancel() {
    uiState.update { it.copy(saveConflict = false) }
  }

  /**
   * The file was written while this notebook had it open: its cells are re-read, unless it has
   * unsaved work or [seen] is the stamp it holds, which is its own save heard back.
   *
   * vscode@3f3380c's rule over a notebook's working copy (`storedFileWorkingCopyManager.ts`,
   * `onDidFilesChange`). Under the write lock, so a save still in flight adopts its stamp first.
   */
  suspend fun followDisk(seen: FileStamp) {
    writes.withLock {
      val ours = held() ?: return
      if (uiState.value.dirty || seen == ours) return
      reread()
    }
  }

  /**
   * A file gone or unreadable by now raises what an open raises for it, and is reported where a
   * failed save is: the work is still on screen, so it must not be replaced by a message.
   */
  private suspend fun reread() {
    try {
      val onDisk = repository.readStampedNotebook(notebookId())
      adopt(onDisk)
      uiState.update { it.revertedTo(onDisk.notebook) }
    } catch (e: IllegalArgumentException) {
      uiState.update { it.copy(saveConflict = false, saveFailure = e.message) }
    }
  }
}

/**
 * A refused save is a question and any other failure is a report - see [EditorUiState.saveConflict].
 *
 * The refusal marks the notebook dirty itself: a run's save is made with no edit having marked it
 * first, and a notebook whose outputs were refused the disk owes a write whatever led to it. Cancel
 * then leaves a document the close gate still asks about.
 */
internal fun EditorUiState.reportingSave(failure: Throwable): EditorUiState =
  if (failure is FileChangedOnDiskException) {
    copy(
      saveConflict = true,
      dirty = true,
    )
  } else {
    copy(saveFailure = failure.message)
  }

/**
 * This sitting, on [onDisk]'s cells: `Context.revert()` reinitialises the model from the file and
 * leaves the widget, its kernel and its selection where they were.
 *
 * Outputs and execution counts come with the cells because they are the file's too, and a cell
 * that kept its id is told its text changed, which its field cannot see on its own.
 */
internal fun EditorUiState.revertedTo(onDisk: Notebook): EditorUiState {
  val read = onDisk.toEditorUiState(defaultCellId = newCellId())
  val stillSelected = selectedCellId?.takeIf { id -> read.cells.any { it.id == id } }
  return copy(
    cells = read.cells,
    outputs = read.outputs,
    executionCounts = read.executionCounts,
    selectedCellId = stillSelected ?: read.selectedCellId,
    anchorCellId = null,
    dirty = false,
    saveConflict = false,
    saveFailure = null,
  ).adoptingRewrites(this).withSearchRecomputed()
}

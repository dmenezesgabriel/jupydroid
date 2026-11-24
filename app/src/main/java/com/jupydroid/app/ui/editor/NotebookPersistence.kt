package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StampedNotebook
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The notebook's writes: the model a save is made from, the stamp it expects to find, and the
 * one-write-at-a-time rule that keeps an older snapshot from landing second.
 *
 * Split from [EditorViewModel] the way its other collaborators are - [NotebookKernel],
 * [NotebookCellEdits], [NotebookSaveConflict] - sharing only the state flow. What stays on the
 * ViewModel is what a *screen* does with a write (debounce it, flush it on the way out); what
 * lives here is what a write *is*, which a retained run's checkpoint still needs after the
 * screen is gone.
 *
 * ```
 * persistence.persist()          // a run's checkpoint, a debounced edit, the close flush
 * persistence.adopt(read)        // the load, and the conflict's Revert
 * ```
 */
internal class NotebookPersistence(
  private val repository: NotebookRepository,
  private val uiState: MutableStateFlow<EditorUiState>,
) {
  /**
   * The notebook as last read or written - what a save's base is, null until it has loaded.
   * Mutated only through the named acts below, so a rename, a bound kernel and a re-read each
   * have one place they change it.
   */
  private var notebook: Notebook? = null

  /**
   * What the file looked like when it was last read or written here - what the next save
   * expects to find, so one that would replace somebody else's write is refused instead.
   */
  private var stamp: FileStamp? = null

  /** Set when this notebook's file is deleted, so [persist] stops putting it back. */
  private var gone = false

  /**
   * One write at a time, in the order they were asked for: a debounced write and a run's
   * checkpoint `persist` can both be in flight, and an older snapshot landing second must not
   * put its document back on disk. Shared with [NotebookSaveConflict], whose revert must not
   * land in the middle of a save either.
   */
  val writes = Mutex()

  /** What was read becomes the base a save expects - the load and the conflict's Revert alike. */
  fun adopt(read: StampedNotebook) {
    notebook = read.notebook
    stamp = read.stamp
  }

  /** `Context._updatePath`'s model half: the document moved, so does what a save writes. */
  fun movedTo(path: String) {
    notebook = notebook?.copy(id = path, title = path)
  }

  /** `kernelChanged` -> `metadata.kernelspec`: the bound kernel is part of what gets written. */
  fun kernelBound(kernelName: String, spec: KernelSpec?) {
    notebook = notebook?.withKernelSpec(kernelName, spec)
  }

  /** The file is gone, so nothing more goes to it - the delete a detached follow can still hear. */
  fun markGone() {
    gone = true
  }

  /** The stamp the file was last known by - what a conflict check holds; null before the load. */
  fun heldStamp(): FileStamp? = stamp

  /** The notebook a save would be made from - what the kernel seed and a copy's metadata read. */
  fun held(): Notebook? = notebook

  /**
   * Writes the notebook back - and **adopts the result only if the write happened**. Answers
   * whether the file on disk is this notebook now, which [NotebookExport] needs: nbconvert reads
   * the file, and the reference's `await context.save()` rejects its export command when the save
   * fails rather than converting what was last written
   * (`packages/notebook-extension/src/index.ts:741-745`).
   *
   * Two things used to be true before the save rather than after it, and both were wrong when it
   * failed: `dirty` was cleared, so a notebook that owed a write claimed not to, and the exception
   * went straight into `viewModelScope`, where it is fatal. A cell that removes the notebook's
   * folder is enough to cause it, and nothing announces that - `WorkspaceFiles.changes`
   * carries what this app moved, not what the kernel did.
   *
   * `EditorUiState.dirty` says it is *"cleared by the save, not by the edit"*, which is only true
   * from here. See [EditorUiState.saveFailure] for why the report is not `error`.
   *
   * The write carries [stamp], so a file somebody else wrote since is left alone and the refusal
   * becomes [EditorUiState.saveConflict]. [overwriting] is that question answered "Overwrite": the
   * one save made whatever is on disk.
   */
  suspend fun persist(overwriting: Boolean = false): Boolean {
    if (gone) return false
    return writes.withLock {
      val base = notebook ?: return@withLock false
      val written = base.copy(cells = uiState.value.withResultsOnCells())
      runCatching { repository.saveNotebook(written, expected = stamp.takeUnless { overwriting }) }
        .onSuccess { saved ->
          stamp = saved
          adoptWritten(base, written)
        }
        .onFailure { failure ->
          if (failure is CancellationException) throw failure
          uiState.update { it.reportingSave(failure) }
        }
        .isSuccess
    }
  }

  /**
   * A write is a snapshot of the model, not an authority over it: what arrived while it was
   * suspended - an edit, a delete, a run's outputs - stays, and the screen's cells are rebuilt
   * from the state as it stands now rather than put back from the snapshot. `dirty` clears only
   * when the write still covers that state; otherwise the later edit is still owed a write of
   * its own, which `schedule` already armed.
   *
   * `Context._save` is the same shape - it clears `dirty` when the write returns and updates the
   * contents model's metadata, but it never writes the snapshot back over the live document
   * (`packages/docregistry/src/context.ts:627-640`).
   */
  private fun adoptWritten(base: Notebook, written: Notebook) {
    val notebookMoved = notebook != base
    notebook = (notebook ?: base).copy(cells = written.cells)
    uiState.update { state ->
      val merged = state.withResultsOnCells()
      val covered = !notebookMoved && merged == written.cells
      state.copy(cells = merged, dirty = state.dirty && !covered, saveFailure = null)
    }
  }
}

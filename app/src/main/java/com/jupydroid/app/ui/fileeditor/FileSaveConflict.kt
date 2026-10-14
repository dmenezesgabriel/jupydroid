package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.StampedText
import com.jupydroid.app.domain.port.FileChangedOnDiskException
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The three answers to "this file changed on disk" - [FileEditorUiState.saveConflict].
 *
 * `Context._raiseConflict`'s dialog (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`):
 * Overwrite saves anyway, Revert loads the disk's version, and Cancel rejects that one save and
 * leaves the document owing it. `NotebookSaveConflict` for a document that is one string, split
 * from [FileEditorViewModel] the same way and sharing only the state flow.
 *
 * ```
 * if (state.saveConflict) FileChangedDialog(state.name, conflict::overwrite, conflict::revert, conflict::cancel)
 * ```
 */
class FileSaveConflict(
  private val uiState: MutableStateFlow<FileEditorUiState>,
  private val scope: CoroutineScope,
  private val repository: WorkspaceRepository,
  /** Asked for rather than captured: a rename moves the file while the document carries on. */
  private val filePath: () -> String,
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
  /** Hands the ViewModel the stamp that was read, so the next save expects the file as it is now. */
  private val adopt: (FileStamp) -> Unit,
  /** The stamp the ViewModel holds - what it last read or wrote - or null before the file has loaded. */
  private val held: () -> FileStamp?,
) {
  /** The editor's text wins: what is on disk is replaced, which is what was asked. */
  fun overwrite() {
    cancel()
    scope.launch { saveOver() }
  }

  /** The disk's text wins: it is read back and the edit that was owed is dropped. */
  fun revert() {
    dropOwedWrite()
    scope.launch { writes.withLock { reread() } }
  }

  /** Neither yet: the edit stays owed and the file stays as it is, and the next save asks again. */
  fun cancel() {
    uiState.update { it.copy(saveConflict = false) }
  }

  /**
   * The file was written while this editor had it open: re-read it, unless the editor has unsaved
   * work or [seen] is the stamp it holds, which is its own save heard back.
   *
   * vscode@3f3380c "never reload[s] dirty models" (`textFileEditorModelManager.ts`), and its
   * resolve skips a file whose etag it already holds (`textFileEditorModel.ts`, `resolveFromFile`).
   * Under the write lock, so a save still in flight adopts its stamp before this compares.
   */
  suspend fun followDisk(seen: FileStamp) {
    writes.withLock {
      val ours = held() ?: return
      if (uiState.value.dirty || seen == ours) return
      reread()
    }
  }

  /**
   * A file gone or unreadable by now is reported where a failed save is, with the question taken
   * down: the text is still on screen, and there is no disk version left to choose.
   */
  private suspend fun reread() {
    runCatching { repository.readStampedText(filePath()) }
      .onSuccess { onDisk ->
        adopt(onDisk.stamp)
        uiState.update { it.revertedTo(onDisk) }
      }
      .onFailure { failure -> uiState.update { it.copy(saveConflict = false, error = failure.message) } }
  }
}

/**
 * A refused save is a question and any other failure is a report.
 *
 * The refusal marks the file dirty itself, so a cancelled question leaves a document the close
 * gate still asks about.
 */
internal fun FileEditorUiState.reportingSave(failure: Throwable): FileEditorUiState =
  if (failure is FileChangedOnDiskException) copy(saveConflict = true, dirty = true) else copy(error = failure.message)

/** The revision is bumped because the field cannot see a changed [FileEditorUiState.text] on its own. */
private fun FileEditorUiState.revertedTo(onDisk: StampedText): FileEditorUiState =
  copy(text = onDisk.text, revision = revision + 1, dirty = false, saveConflict = false, error = null)

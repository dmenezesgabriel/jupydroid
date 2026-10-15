package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The file editor's own save: the stamp the next write expects, the lock that keeps one write at a
 * time, and the write itself - kept together because the stamp is only ever true under the lock.
 *
 * Split from [FileEditorViewModel] as [FileSaveConflict] was, sharing only the state flow; the
 * conflict's answers reach the stamp and the lock through here.
 *
 * ```
 * val writes = FileEditorWrites(uiState, workspaceRepository, { livePath }, { fileIsGone })
 * writes.persist()
 * ```
 */
class FileEditorWrites(
  private val uiState: MutableStateFlow<FileEditorUiState>,
  private val repository: WorkspaceRepository,
  /** Asked for rather than captured: a rename moves the file while the document carries on. */
  private val filePath: () -> String,
  /** Whether the file was deleted under the editor - then nothing more goes to it. */
  private val fileIsGone: () -> Boolean,
) {
  /** The file as it was last read or written here: what the next save expects to find. */
  var stamp: FileStamp? = null

  /** One writer at a time, so a revert never lands in the middle of a save - `EditorViewModel`'s own. */
  val lock = Mutex()

  /**
   * Writes the file back, and **reports a write it could not make instead of raising into
   * `viewModelScope`**, where an uncaught exception takes the process down.
   *
   * A shared workspace can lose the directory under an open file - a cell can `shutil.rmtree` it -
   * and `WorkspaceRepository.changes` announces only what *this app* moved, so nothing warns us. The
   * reference reports it the same way and leaves the document owing a write:
   * `Context.save()`'s failure path is `showErrorMessage`, not a rethrow.
   *
   * Cleared by a save that works, so a failure that has passed does not sit on screen for ever.
   *
   * The write carries [stamp], so a file somebody else wrote since is left alone and the refusal
   * becomes [FileEditorUiState.saveConflict] - a question, where every other failure is a report.
   * [overwriting] is that question answered "Overwrite": the one save made whatever is on disk.
   */
  suspend fun persist(overwriting: Boolean = false) {
    if (fileIsGone() || !uiState.value.loaded) return
    lock.withLock {
      runCatching { repository.writeText(filePath(), uiState.value.text, stamp.takeUnless { overwriting }) }
        .fold(
          onSuccess = { written ->
            stamp = written
            uiState.update { it.copy(error = null, dirty = false) }
          },
          onFailure = { failure ->
            if (failure is CancellationException) throw failure
            uiState.update { it.reportingSave(failure) }
          },
        )
    }
  }
}

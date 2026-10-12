package com.jupydroid.app.ui.fileeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.pathAfterRename
import com.jupydroid.app.domain.model.withEditsApplied
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.syntax.syntaxForFile
import com.jupydroid.app.ui.editor.DocumentAutosave
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
  /**
   * Bumped whenever [text] was rewritten by something that is **not** the field - today, a
   * workspace replace reaching this document through [OpenDocuments].
   *
   * `EditorUiState.sourceRevisions` for a document with one resource instead of a cell each, and it
   * exists for a measured reason: `rememberTextFieldState(initialText)` is
   * `rememberSaveable(saver) { … }` with **no keys** (foundation `TextFieldState.kt:657-661`), so a
   * changed [text] on its own leaves the field showing what it had. Typing never bumps it - that
   * came from the field, and adopting it back would fight the caret.
   */
  val revision: Int = 0,
  /** Edited since the last write. Cleared by the save, not by the edit - `EditorUiState.dirty`. */
  val dirty: Boolean = false,
  /** The file was deleted, so the screen showing it should close - see `EditorUiState.gone`. */
  val gone: Boolean = false,
  /**
   * The save was refused because the file changed on disk, and the user has not yet said which
   * version wins - `EditorUiState.saveConflict` for a document that is one string. A state rather
   * than an event, so a second refusal while the question is up sets what is already set.
   */
  val saveConflict: Boolean = false,
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
  private var filePath: String,
  private val workspaceRepository: WorkspaceRepository,
  private val applicationScope: CoroutineScope,
  /**
   * Where this document says it is open, so a workspace replace edits the model rather than the
   * file (`bulkTextEdits.ts:227`).
   *
   * Defaulted so the many tests that drive an editor alone are not made to care, which is the same
   * arrangement every port on `AppContainer` has; a registry nobody reads costs nothing.
   */
  private val openDocuments: OpenDocuments = OpenDocuments(),
  /**
   * The `autosave`/`autosaveInterval` pair [save] reads - defaulted the way [openDocuments] is,
   * so a test driving the editor alone reads the shipped defaults.
   */
  private val editorSettings: EditorSettingsRepository = FakeEditorSettingsRepository(),
) : ViewModel(),
  OpenDocument {
  private val _uiState = MutableStateFlow(FileEditorUiState(path = filePath, name = notebookLeafOf(filePath)))
  val uiState: StateFlow<FileEditorUiState> = _uiState

  /** Where this document lives now - read live by [OpenDocuments], so a rename moves it. */
  override val path: String get() = this.filePath

  /** The path the editor opened under - [filePath]'s first value, kept so a rename cannot hide it. */
  override val openedAs: String = filePath

  /** Live, because the tab strip's dot has to follow the save rather than a snapshot of it. */
  override val dirty: StateFlow<Boolean> =
    _uiState.map { it.dirty }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

  private val settings = editorSettings.observeSettings()
    .stateIn(viewModelScope, SharingStarted.Eagerly, EditorSettings())

  private val save = DocumentAutosave(viewModelScope, settings, { _uiState.value.dirty && _uiState.value.loaded }) {
    persist()
  }

  /** Set when the file this editor is on is deleted; see [stopWriting]. */
  private var fileIsGone = false

  /** The file as it was last read or written here: what the next save expects to find. */
  private var stamp: FileStamp? = null

  /** One writer at a time, so a revert never lands in the middle of a save - `EditorViewModel`'s own. */
  private val writeMutex = Mutex()

  init {
    // **Open before it has loaded, on purpose.** A replace arriving mid-read must not be handed the
    // empty string that is on screen, and must not be allowed to write the file either - this
    // editor's next save would put the old text straight back. Present with a null source is the
    // conflict the engine already knows how to refuse.
    openDocuments.opened(this)
    viewModelScope.launch {
      // A shared workspace can lose a file under the user - a cell can `os.remove` it - so a read
      // that fails is an ordinary event to report, not a corrupt state to crash on.
      runCatching { workspaceRepository.readStampedText(filePath) }
        .onSuccess { read ->
          stamp = read.stamp
          _uiState.update { it.copy(text = read.text, syntax = syntaxForFile(it.name), loaded = true, error = null) }
        }
        .onFailure { failure -> _uiState.update { it.copy(error = failure.message, loaded = false) } }
    }
    // `Context._onFileChanged`: the open document follows its file rather than waiting to be told
    // by whichever screen moved it. Without this the next write re-creates the old path.
    viewModelScope.launch {
      workspaceRepository.changes.collect { change ->
        when (change) {
          is WorkspaceChange.Renamed -> pathAfterRename(filePath, change.from, change.to)?.let { adopt(it) }
          is WorkspaceChange.Deleted -> if (change.path == filePath) stopWriting()
        }
      }
    }
  }

  /** Takes on where the file lives now; the document did not change, only its path. */
  private fun adopt(moved: String) {
    filePath = moved
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
    save.discard()
    _uiState.update { it.copy(gone = true) }
  }

  /** Takes an edit from the field. Written back on a debounce, as a cell's source is. */
  fun edit(text: String) {
    _uiState.update { it.copy(text = text, dirty = true) }
    save.schedule()
  }

  /**
   * The text of the resource [cell] names - the file itself, and nothing else.
   *
   * A plain file has one resource where a notebook has one per cell, so an index names nothing
   * here. Null until the file has loaded; see the note in `init`.
   */
  override fun sourceOf(cell: Int?): String? = _uiState.value.takeIf { cell == null && it.loaded }?.text

  /**
   * Rewrites this document from a workspace replace - `BulkTextEdits` editing the resolved model,
   * then saving it (`replaceService.ts:136`).
   *
   * Saved on the same debounce every other write here uses, and the revision is bumped because the
   * field cannot see a changed [FileEditorUiState.text] on its own.
   */
  override fun applyEdits(cell: Int?, edits: List<TextEdit>) {
    if (cell != null || edits.isEmpty()) return
    _uiState.update { it.copy(text = it.text.withEditsApplied(edits), revision = it.revision + 1, dirty = true) }
    save.schedule()
  }

  /**
   * `docmanager:save`, run on the way out: the owed write is made first and the screen is let go
   * only if it left nothing dirty - a failed save keeps the document open, as the reference's
   * `saveBeforeClose` keeps its widget open on the same failure.
   */
  override fun saveNow(onSaved: () -> Unit) {
    viewModelScope.launch {
      save.flush()
      if (!_uiState.value.dirty) onSaved()
    }
  }

  /**
   * The close dialog's Discard: owed work is dropped - `SaveHandler.dispose()` clears its timer
   * and writes nothing, so the close that follows must not flush it back.
   */
  override fun discardChanges() {
    save.discard()
  }

  /** What to do about a refused save. Driven straight from the screen; see [FileSaveConflict]. */
  val conflict = FileSaveConflict(
    uiState = _uiState,
    scope = viewModelScope,
    repository = workspaceRepository,
    filePath = { filePath },
    writes = writeMutex,
    saveOver = { persist(overwriting = true) },
    adopt = { stamp = it },
  )

  public override fun onCleared() {
    openDocuments.closed(this)
    val hadUnsavedWork = save.cancelPending()
    if (!save.discarded && (hadUnsavedWork || _uiState.value.dirty)) applicationScope.launch { persist() }
    super.onCleared()
  }

  /**
   * Writes the file back, and **reports a write it could not make instead of raising into
   * `viewModelScope`**, where an uncaught exception takes the process down.
   *
   * The same argument this file already makes for the read, one direction over: a shared workspace
   * can lose the directory under an open file - a cell can `shutil.rmtree` it - and
   * `WorkspaceRepository.changes` announces only what *this app* moved, so nothing warns us. The
   * reference reports it the same way and leaves the document owing a write:
   * `Context.save()`'s failure path is `showErrorMessage`, not a rethrow.
   *
   * Cleared by a save that works, so a failure that has passed does not sit on screen for ever.
   *
   * The write carries [stamp], so a file somebody else wrote since is left alone and the refusal
   * becomes [FileEditorUiState.saveConflict] - a question, where every other failure is a report.
   * [overwriting] is that question answered "Overwrite": the one save made whatever is on disk.
   */
  private suspend fun persist(overwriting: Boolean = false) {
    if (fileIsGone || !_uiState.value.loaded) return
    writeMutex.withLock {
      runCatching { workspaceRepository.writeText(filePath, _uiState.value.text, stamp.takeUnless { overwriting }) }
        .fold(
          onSuccess = ::adoptWritten,
          onFailure = { failure ->
            if (failure is CancellationException) throw failure
            _uiState.update { it.reportingSave(failure) }
          },
        )
    }
  }

  private fun adoptWritten(written: FileStamp) {
    stamp = written
    _uiState.update { it.copy(error = null, dirty = false) }
  }
}

package com.jupydroid.app.ui.fileeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceWatcher
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.withEditsApplied
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceWatcher
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.syntax.syntaxForFile
import com.jupydroid.app.ui.editor.DocumentAutosave
import com.jupydroid.app.ui.editor.DocumentFileFollow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
  /** The file this editor opened - where it lives from then on is [livePath]. */
  filePath: String,
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
  /** Which of [filePath]'s opens this editor is - its tab's key says; see [DocumentOpening]. */
  opening: Int = 0,
  /** Hears the file written by anything else, so a clean editor shows it; defaulted as [openDocuments] is. */
  private val workspaceWatcher: WorkspaceWatcher = FakeWorkspaceWatcher(),
  /** Announces a rename or delete made beside this editor, so it follows; defaulted as [workspaceWatcher] is. */
  private val workspaceFiles: WorkspaceFiles = FakeWorkspaceFiles(),
) : ViewModel(),
  OpenDocument {
  /**
   * Where this file lives - **mutable**, because a rename made anywhere moves it.
   *
   * `Context._path`, which `_updatePath` reassigns in place while the open widget carries on.
   * Snapshot state, because the tab strip reads it in composition through [path] - see
   * `EditorViewModel.liveNotebookId` for the tab it left stale as a plain `var`.
   */
  private var livePath: String by mutableStateOf(filePath)

  private val _uiState = MutableStateFlow(FileEditorUiState(path = filePath, name = notebookLeafOf(filePath)))
  val uiState: StateFlow<FileEditorUiState> = _uiState

  /** Where this document lives now - read live by [OpenDocuments], so a rename moves it. */
  override val path: String get() = livePath

  /** Where the editor's tab opened it - [livePath]'s first value, kept so a rename cannot hide it. */
  override val openedAs: DocumentOpening = DocumentOpening(filePath, opening)

  /** Live, because the tab strip's dot has to follow the save rather than a snapshot of it. */
  override val dirty: StateFlow<Boolean> =
    _uiState.map { it.dirty }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

  private val settings = editorSettings.observeSettings()
    .stateIn(viewModelScope, SharingStarted.Eagerly, EditorSettings())

  /** Set when the file this editor is on is deleted; see [stopWriting]. */
  private var fileIsGone = false

  /** The stamp, the lock and the write; see [FileEditorWrites]. */
  private val writes = FileEditorWrites(_uiState, workspaceRepository, { livePath }, { fileIsGone })

  private val save = DocumentAutosave(viewModelScope, settings, { _uiState.value.dirty && _uiState.value.loaded }) {
    writes.persist()
  }

  init {
    // **Open before it has loaded, on purpose.** A replace arriving mid-read must not be handed the
    // empty string that is on screen, and must not be allowed to write the file either - this
    // editor's next save would put the old text straight back. Present with a null source is the
    // conflict the engine already knows how to refuse.
    openDocuments.opened(this)
    viewModelScope.launch {
      // A shared workspace can lose a file under the user - a cell can `os.remove` it - so a read
      // that fails is an ordinary event to report, not a corrupt state to crash on.
      runCatching { workspaceRepository.readStampedText(livePath) }
        .onSuccess { read ->
          writes.stamp = read.stamp
          _uiState.update { it.copy(text = read.text, syntax = syntaxForFile(it.name), loaded = true, error = null) }
        }
        .onFailure { failure -> _uiState.update { it.copy(error = failure.message, loaded = false) } }
    }
    // `Context._onFileChanged`: the open document follows its file rather than waiting to be told
    // by whichever screen moved it. Without this the next write re-creates the old path.
    DocumentFileFollow(workspaceFiles.changes, workspaceWatcher, { livePath }, ::adopt, ::stopWriting) {
      conflict.followDisk(it)
    }.followIn(viewModelScope)
  }

  /** Takes on where the file lives now; the document did not change, only its path. */
  private fun adopt(moved: String) {
    livePath = moved
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

  /**
   * Takes an edit from the field. Written back on a debounce, as a cell's source is.
   *
   * A report of the text already held is no edit: the field reports once more as it leaves the
   * screen (`reportCellEdits`), and after a save that would mark the saved file dirty again.
   */
  fun edit(text: String) {
    if (text == _uiState.value.text) return
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
    viewModelScope.launch { if (saveAndConfirm()) onSaved() }
  }

  override suspend fun saveAndConfirm(): Boolean {
    save.flush()
    return !_uiState.value.dirty
  }

  /** The text on screen, written unconditionally; the stamp is this file's, so the copy's is not kept. */
  override suspend fun writeCopyTo(path: String) {
    workspaceRepository.writeText(path, _uiState.value.text)
  }

  /**
   * The close dialog's Discard: owed work is dropped - `SaveHandler.dispose()` clears its timer
   * and writes nothing, so the close that follows must not flush it back.
   */
  override fun discardChanges() {
    save.discard()
  }

  /** `docmanager:reload`, through the same re-read File Changed's Revert makes. */
  override fun reloadFromDisk() = conflict.revert()

  /** What to do about a refused save. Driven straight from the screen; see [FileSaveConflict]. */
  val conflict = FileSaveConflict(
    uiState = _uiState,
    scope = viewModelScope,
    repository = workspaceRepository,
    filePath = { livePath },
    writes = writes.lock,
    saveOver = { writes.persist(overwriting = true) },
    dropOwedWrite = { save.cancelPending() },
    adopt = { writes.stamp = it },
    held = { writes.stamp },
  )

  public override fun onCleared() {
    openDocuments.closed(this)
    val hadUnsavedWork = save.cancelPending()
    if (!save.discarded && (hadUnsavedWork || _uiState.value.dirty)) applicationScope.launch { writes.persist() }
    super.onCleared()
  }
}

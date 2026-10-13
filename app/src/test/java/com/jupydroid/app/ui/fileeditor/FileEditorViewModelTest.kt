package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.SnapshotReadWatch
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.syntax.JsonSyntax
import com.jupydroid.app.syntax.PythonSyntax
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A file that is not a notebook, in one editor.
 *
 * JupyterLab opens anything its notebook factory does not claim in the *file editor* - one
 * CodeMirror over the whole document, with the language chosen from the file type
 * (`packages/fileeditor`). Ours is the cell editor's own field over the whole file, so line
 * numbers, highlighting, bracket matching and the symbol row are the same code and not a second
 * implementation of them.
 */
class FileEditorViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun itLoadsTheFileItWasGiven() = runTest {
    val viewModel = editorFor("notes.txt" to "hello\nthere")
    advanceUntilIdle()

    assertEquals("hello\nthere", viewModel.uiState.value.text)
    assertTrue(viewModel.uiState.value.loaded)
  }

  /** The name, not the path - `PathExt.basename`, the same thing the notebook editor shows. */
  @Test
  fun itIsTitledByTheFilesName() = runTest {
    val viewModel = editorFor("reports/data.csv" to "a,b")
    advanceUntilIdle()

    assertEquals("data.csv", viewModel.uiState.value.name)
  }

  @Test
  fun theHighlighterFollowsTheFileType() = runTest {
    val python = editorFor("train.py" to "x = 1")
    val json = editorFor("config.json" to "{}")
    advanceUntilIdle()

    assertTrue(python.uiState.value.syntax is PythonSyntax)
    assertTrue(json.uiState.value.syntax is JsonSyntax)
  }

  /**
   * A file a text field cannot represent is not shown in one: the read refuses it, so there is
   * nothing loaded to edit and the reason is what the document carries instead.
   */
  @Test
  fun aFileThatIsNotTextIsNotLoadedAndSaysWhy() = runTest {
    val viewModel = FileEditorViewModel("plot.png", workspaceHoldingAnImage(), this)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse("an image must not load as text", state.loaded)
    assertEquals("", state.text)
    assertTrue("expected the reason to name the file, got '${state.error}'", "plot.png" in state.error.orEmpty())
  }

  /** Not by a stray edit and not on the way out: the image holds the bytes it held. */
  @Test
  fun nothingIsWrittenOverAFileThatIsNotText() = runTest {
    val workspace = workspaceHoldingAnImage()
    val viewModel = FileEditorViewModel("plot.png", workspace, this)
    advanceUntilIdle()

    viewModel.edit("typed over an image")
    advanceUntilIdle()
    viewModel.onCleared()
    advanceUntilIdle()

    assertArrayEquals(PNG_HEADER, workspace.readBytes("plot.png"))
  }

  /**
   * An edit reaches the disk, debounced.
   *
   * Same rule the notebook editor follows and for the same reason: a write per keystroke rewrites
   * the file on every character. `DocumentAutosave` is shared rather than reimplemented.
   */
  @Test
  fun anEditIsWrittenBack() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before"))
    val viewModel = FileEditorViewModel("notes.txt", workspace, this)
    advanceUntilIdle()

    viewModel.edit("after")
    advanceUntilIdle()

    assertEquals("after", workspace.readText("notes.txt"))
  }

  @Test
  fun aFileThatCannotBeReadIsReportedRatherThanCrashing() = runTest {
    val viewModel = editorFor("other.txt" to "x", open = "missing.txt")
    advanceUntilIdle()

    assertTrue("expected the failure to be reported", viewModel.uiState.value.error != null)
    assertEquals(false, viewModel.uiState.value.loaded)
  }

  /**
   * A rename made somewhere else moves the open file, and the next write follows it.
   *
   * This is `Context._onFileChanged` -> `_updatePath`: the reference's open document subscribes to
   * the contents layer and retargets itself, because a rename can come from the file browser or
   * from another client entirely. Without it the editor keeps writing to a path that no longer
   * exists, which **re-creates** the file under its old name - one document, two files, and the
   * edits in the one nobody is looking at.
   */
  @Test
  fun aRenameMadeElsewhereMovesTheOpenFile() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("before")
    val viewModel = FileEditorViewModel("notes.txt", workspace, this)
    advanceUntilIdle()

    workspace.rename("notes.txt", "kept.txt")
    advanceUntilIdle()
    viewModel.edit("after")
    advanceUntilIdle()

    assertEquals("kept.txt", viewModel.uiState.value.path)
    assertEquals("kept.txt", viewModel.uiState.value.name)
    assertEquals("after", File(temp.root, "kept.txt").readText())
    assertEquals(listOf("kept.txt"), temp.root.list()?.toList())
  }

  /** A folder rename moves everything under it, which is the case the prefix rule exists for. */
  @Test
  fun aRenamedFolderMovesTheFileInsideIt() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "reports").mkdirs()
    File(temp.root, "reports/data.csv").writeText("a,b")
    val viewModel = FileEditorViewModel("reports/data.csv", workspace, this)
    advanceUntilIdle()

    workspace.rename("reports", "archive")
    advanceUntilIdle()

    assertEquals("archive/data.csv", viewModel.uiState.value.path)
  }

  /**
   * A file deleted under the editor is not written back - not by the debounce, not on the way out.
   *
   * Our teardown flushes the last edit; the reference's does not - `SaveHandler.dispose()` clears
   * its timer and writes nothing, and it is wired to `context.disposed`
   * (`docmanager/src/savehandler.ts:29,62-69`). So a delete has to reach the document *before*
   * anything closes it, or the farewell write puts the file straight back.
   */
  @Test
  fun aFileDeletedUnderTheEditorIsNotWrittenBack() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("before")
    val viewModel = FileEditorViewModel("notes.txt", workspace, this)
    advanceUntilIdle()
    viewModel.edit("an edit that is still owed")

    workspace.delete("notes.txt")
    advanceUntilIdle()
    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals(emptyList<String>(), temp.root.list()?.toList())
  }

  /**
   * And it stays gone while the editor is still on screen.
   *
   * Dropping the *pending* write is not enough on its own: the delete closes the pane, but not
   * before the next keystroke can schedule another one, and that write would put the file back
   * with no pending anything to cancel. The reference's `SaveHandler` is disposed, not paused -
   * after that it never writes again, whatever the widget does.
   */
  @Test
  fun typingAfterTheFileIsDeletedDoesNotBringItBack() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("before")
    val viewModel = FileEditorViewModel("notes.txt", workspace, this)
    advanceUntilIdle()

    workspace.delete("notes.txt")
    advanceUntilIdle()
    viewModel.edit("typed into a file that is gone")
    advanceUntilIdle()

    assertEquals(emptyList<String>(), temp.root.list()?.toList())
  }

  /**
   * An overwrite is a delete of the file under the editor, and it must be heard as one.
   *
   * `DocumentManager.overwrite` runs `deleteFile(newPath)` first (`manager.ts:514-528`), so the
   * reference's widget on the target learns its file is gone exactly the way a delete lands.
   * Unannounced, the editor's owed write lands on the name the *other* file just won - one
   * document, and the file that survived is gone under it.
   */
  @Test
  fun aFileOverwrittenUnderTheEditorIsNotWrittenBack() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("what the editor loaded")
    File(temp.root, "other.txt").writeText("the one that survives")
    val viewModel = FileEditorViewModel("notes.txt", workspace, this)
    advanceUntilIdle()
    viewModel.edit("typed into the file being replaced")

    workspace.overwrite("other.txt", "notes.txt")
    advanceUntilIdle()
    viewModel.onCleared()
    advanceUntilIdle()

    assertTrue("the file it was on is gone, so the screen should close", viewModel.uiState.value.gone)
    assertEquals("the one that survives", File(temp.root, "notes.txt").readText())
  }

  /** A screen showing a document is a document that is open, and its text is what it holds. */
  @Test
  fun theOpenFileIsReachedByItsPathAndReadsBackItsText() = runTest {
    val open = OpenDocuments()
    val viewModel = editorFor("notes.txt" to "cat", registeredIn = open)
    advanceUntilIdle()

    assertSame(viewModel, open.at("notes.txt"))
    assertEquals("cat", open.at("notes.txt")?.sourceOf(cell = null))
  }

  /**
   * **A document that could not be read is open and unreadable, not open and empty.**
   *
   * It is registered from `init` rather than on a successful load, so a replace can never slip in
   * between the two and write a file this editor is about to overwrite from stale memory. The cost
   * is this state, and it must refuse rather than offer the empty string that is on screen: the
   * engine reads a null source as the conflict it is, and a replace against `""` would delete the
   * file's contents.
   */
  @Test
  fun aFileThatCouldNotBeReadIsOpenButRefusesToBeRead() = runTest {
    val open = OpenDocuments()
    val viewModel = editorFor("other.txt" to "x", open = "missing.txt", registeredIn = open)
    advanceUntilIdle()

    assertSame(viewModel, open.at("missing.txt"))
    assertEquals(false, viewModel.uiState.value.loaded)
    assertNull(open.at("missing.txt")?.sourceOf(cell = null))
  }

  /** A plain file has one resource and it is the file - a cell index names nothing here. */
  @Test
  fun aPlainFileHasNoCellToAddress() = runTest {
    val open = OpenDocuments()
    editorFor("notes.txt" to "cat", registeredIn = open)
    advanceUntilIdle()

    assertNull(open.at("notes.txt")?.sourceOf(cell = 0))
  }

  /**
   * The edit lands in the **model**, which is the whole point: `BulkTextEdits` edits the resolved
   * model and saves it (`replaceService.ts:136`), where a write to the file would be overwritten by
   * this editor's own next save.
   */
  @Test
  fun anEditThroughTheRegistryRewritesTheTextAndIsWrittenOut() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("cat")
    val open = OpenDocuments()
    val viewModel = FileEditorViewModel("notes.txt", workspace, this, open)
    advanceUntilIdle()

    viewModel.applyEdits(cell = null, edits = listOf(TextEdit(0..2, "dog")))
    advanceUntilIdle()

    assertEquals("dog", viewModel.uiState.value.text)
    assertEquals("dog", File(temp.root, "notes.txt").readText())
  }

  /**
   * **And the field is told**, which is the measurement this sub-step exists for:
   * `rememberTextFieldState(initialText)` is `rememberSaveable(saver) { … }` with **no keys**
   * (foundation `TextFieldState.kt:657-661`), so a changed `text` alone leaves the field showing
   * what it had. The notebook has carried `sourceRevisions` for this since Split; the file editor
   * had nothing.
   */
  @Test
  fun anEditThroughTheRegistryTellsTheFieldItsTextChanged() = runTest {
    val open = OpenDocuments()
    val viewModel = editorFor("notes.txt" to "cat", registeredIn = open)
    advanceUntilIdle()
    val before = viewModel.uiState.value.revision

    viewModel.applyEdits(cell = null, edits = listOf(TextEdit(0..2, "dog")))

    assertEquals(before + 1, viewModel.uiState.value.revision)
  }

  /** Typing does not, because typing came *from* the field - adopting it back would fight the caret. */
  @Test
  fun typingDoesNotTellTheFieldAnything() = runTest {
    val viewModel = editorFor("notes.txt" to "cat")
    advanceUntilIdle()

    viewModel.edit("catt")

    assertEquals(0, viewModel.uiState.value.revision)
  }

  @Test
  fun aClosedEditorIsNotOpenAnyMore() = runTest {
    val open = OpenDocuments()
    val viewModel = editorFor("notes.txt" to "cat", registeredIn = open)
    advanceUntilIdle()

    viewModel.onCleared()

    assertNull(open.at("notes.txt"))
  }

  /** The registry reads [FileEditorViewModel.path] live, so a rename moves the document with it. */
  @Test
  fun aRenamedFileIsReachedByWhereItLivesNow() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("cat")
    val open = OpenDocuments()
    val viewModel = FileEditorViewModel("notes.txt", workspace, this, open)
    advanceUntilIdle()

    workspace.rename("notes.txt", "kept.txt")
    advanceUntilIdle()

    assertNull(open.at("notes.txt"))
    assertSame(viewModel, open.at("kept.txt"))
  }

  /**
   * The tab strip reads [FileEditorViewModel.path] in composition, so a rename has to invalidate
   * that read - the tab beneath a terminal has nothing else to redraw it (`pathChanged` ->
   * `title.label`, `packages/docregistry/src/default.ts`).
   */
  @Test
  fun aRenameInvalidatesAComposedReadOfThePath() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("cat")
    val viewModel = FileEditorViewModel("notes.txt", workspace, this, OpenDocuments())
    advanceUntilIdle()
    val watch = SnapshotReadWatch { viewModel.path }

    workspace.rename("notes.txt", "kept.txt")
    advanceUntilIdle()

    assertTrue("a composed read of the path is never told it moved", watch.sawChange())
  }

  private fun kotlinx.coroutines.test.TestScope.editorFor(
    file: Pair<String, String>,
    open: String = file.first,
    registeredIn: OpenDocuments = OpenDocuments(),
  ) = FileEditorViewModel(open, FakeWorkspaceRepository(textByPath = mapOf(file)), this, registeredIn)

  private suspend fun workspaceHoldingAnImage() = FakeWorkspaceRepository().apply { writeBytes("plot.png", PNG_HEADER) }

  private companion object {
    val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
  }
}

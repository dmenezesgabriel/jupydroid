@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The letter intents' half of [notebookKey] - what each resolved chord asks of the document,
 * the settings, the save machinery and the field. The table that resolves them is
 * [NotebookChordTableTest]; this is only about where a bound intent lands.
 */
class NotebookKeyDispatchTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val input = FocusedCellInput()

  /**
   * `B` is `notebook:insert-cell-below`, and every letter is the cell menu's own verb - the
   * [CellAction] arrives already named, so this is [CellEdits.applyTo]'s dispatch with the head
   * as the cell (`actions.tsx`'s `selectFirst` is why letters act on the head).
   */
  @Test
  fun b_insertsBelowTheHeadAndLandsOnIt() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    viewModel.notebookKey(NotebookChordIntent.ApplyCellAction(CellAction.Insert(CellKind.CODE)), input)

    val cells = viewModel.uiState.value.cells
    assertEquals(listOf("c1", cells[1].id, "c3"), cells.map { it.id })
    assertEquals(cells[1].id, viewModel.uiState.value.selectedCellId)
  }

  /** `M` is `change-cell-to-markdown`: the same kind change the menu's Type row does. */
  @Test
  fun m_changesTheHeadCellsKind() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))

    viewModel.notebookKey(
      NotebookChordIntent.ApplyCellAction(CellAction.ChangeKind(CellKind.MARKDOWN)),
      input,
    )

    assertEquals(CellKind.MARKDOWN, viewModel.uiState.value.cells.first().kind)
  }

  /** `D`,`D` then `Z`: `notebook:undo-cell-action` puts the deleted cell back. */
  @Test
  fun z_undoesTheLastCellAction() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    viewModel.notebookKey(NotebookChordIntent.ApplyCellAction(CellAction.Delete), input)
    assertEquals(listOf("c3"), viewModel.uiState.value.cells.map { it.id })

    viewModel.notebookKey(NotebookChordIntent.UndoCellAction, input)

    assertEquals(listOf("c1", "c3"), viewModel.uiState.value.cells.map { it.id })
  }

  /** `Shift Z` is `redo-cell-action`: what `Z` took back comes back. */
  @Test
  fun shiftZ_redoesTheCellAction() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))
    viewModel.notebookKey(NotebookChordIntent.ApplyCellAction(CellAction.Delete), input)
    viewModel.notebookKey(NotebookChordIntent.UndoCellAction, input)

    viewModel.notebookKey(NotebookChordIntent.RedoCellAction, input)

    assertEquals(listOf("c3"), viewModel.uiState.value.cells.map { it.id })
  }

  /** `Ctrl A` is `notebook:select-all`: the whole notebook marked, head on the first cell. */
  @Test
  fun ctrlA_selectsEveryCell() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, markdownCell, secondCodeCell))

    viewModel.notebookKey(NotebookChordIntent.SelectAllCells, input)

    assertEquals(setOf("c1", "c2", "c3"), viewModel.uiState.value.selectedCellIds.toSet())
  }

  /**
   * `Ctrl Shift`+`ArrowLeft`/`ArrowRight` - `collapse-all-headings` and its undo. Every heading
   * folds, not only the head's, which is why it is a document verb rather than [CellAction.Fold].
   */
  @Test
  fun ctrlShiftArrows_foldAndUnfoldEveryHeading() = runTest {
    val headings = listOf(
      Cell("h1", CellKind.MARKDOWN, "# One"),
      Cell("h2", CellKind.MARKDOWN, "# Two"),
      codeCell,
    )
    val viewModel = loadedEditor(cells = headings)

    viewModel.notebookKey(NotebookChordIntent.SetAllHeadingsCollapsed(true), input)

    assertTrue(viewModel.uiState.value.cells.filter { it.kind == CellKind.MARKDOWN }.all { it.headingCollapsed })

    viewModel.notebookKey(NotebookChordIntent.SetAllHeadingsCollapsed(false), input)

    assertTrue(viewModel.uiState.value.cells.none { it.headingCollapsed })
  }

  /**
   * `Ctrl Shift -` is `split-cell-at-cursor`, and it cuts at the **field's** caret - the one thing
   * the document cannot know, which is why the intent carries the input to the door rather than
   * reading the model's stale copy ([CellEditorText]'s whole reason).
   */
  @Test
  fun ctrlShiftMinus_splitsAtTheFieldsCaret() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))
    val field = TextFieldState("print(1)\nprint(2)", TextRange(8)).also(input::attach)

    viewModel.notebookKey(NotebookChordIntent.ApplyCellAction(CellAction.Split), input)

    val sources = viewModel.uiState.value.cells.map { it.source }
    assertEquals(listOf("print(1)", "print(2)"), sources)
    input.detach(field)
  }

  /**
   * `Shift L` is `viewmenu:line-numbering` - a settings write, not a document move: it lands in
   * the same repository the notebook menu's own "Line numbers" row writes.
   */
  @Test
  fun shiftL_togglesLineNumbersInTheSettings() = runTest {
    val settings = FakeEditorSettingsRepository(EditorSettings(lineNumbersShown = true))
    val viewModel = loadedEditor(editorSettings = settings)

    viewModel.notebookKey(NotebookChordIntent.ToggleAllCellLineNumbers, input)
    advanceUntilIdle()

    assertFalse(settings.observeSettings().first().lineNumbersShown)
  }

  /**
   * `Ctrl S` is `docmanager:save` - the close dialog's "Save", which is the autosave machinery's
   * [DocumentAutosave.flush] rather than a second write path.
   */
  @Test
  fun ctrlS_flushesAWriteNow() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    val writtenBefore = repository.saveCount

    viewModel.notebookKey(NotebookChordIntent.SaveDocument, input)
    advanceUntilIdle()

    assertTrue(repository.saveCount > writtenBefore)
  }

  /**
   * `docmanager:save-as` is the shell's dialog, dispatched from the screen's `onNotebookChord`
   * lambda - so at the ViewModel it is a no-op, for `EnterCommandMode`'s dispatch-site reason.
   */
  @Test
  fun ctrlShiftS_isNotTheViewModelsToAnswer() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    val before = viewModel.uiState.value
    val writtenBefore = repository.saveCount

    viewModel.notebookKey(NotebookChordIntent.SaveDocumentAs, input)
    advanceUntilIdle()

    assertEquals(before, viewModel.uiState.value)
    assertEquals(writtenBefore, repository.saveCount)
  }

  /**
   * `Alt`+arrows are `access-{previous,next}-history-entry`, and the `accessKernelHistory`
   * setting gates the **command** upstream-style - the binding is always resolved and the
   * command stands down, which is the same gate the symbol-row keys answer to.
   */
  @Test
  fun altUp_withTheSettingOff_recallsNothing() = runTest {
    val viewModel = loadedEditor()
    val field = TextFieldState("x + 1", TextRange(5)).also(input::attach)

    viewModel.notebookKey(NotebookChordIntent.RecallPreviousHistoryEntry, input)
    advanceUntilIdle()

    assertEquals("x + 1", field.text.toString())
    input.detach(field)
  }

  /** With it on, `Alt`+`ArrowUp` is `recallPrevious` - one step older per key, into the field. */
  @Test
  fun altUp_withTheSettingOn_recallsTheLastRun() = runTest {
    val settings = FakeEditorSettingsRepository(EditorSettings(accessKernelHistory = true))
    val viewModel = loadedEditor(editorSettings = settings)
    viewModel.kernel.run(RunRange.MARKED)
    advanceUntilIdle()
    val field = TextFieldState("x + 1", TextRange(5)).also(input::attach)

    viewModel.notebookKey(NotebookChordIntent.RecallPreviousHistoryEntry, input)
    advanceUntilIdle()

    assertEquals("print(1)", field.text.toString())
    input.detach(field)
  }

  /**
   * `Esc`/`Ctrl M` keep being the dispatch site's: [NotebookChordIntent.EnterCommandMode] is a
   * focus move, answered where the FocusRequester lives - the ViewModel arm must stay a no-op.
   */
  @Test
  fun enterCommandMode_isADispatchSiteAct() = runTest {
    val viewModel = loadedEditor()

    viewModel.notebookKey(NotebookChordIntent.EnterCommandMode, input)

    assertNull(viewModel.uiState.value.cellTakingCaret)
  }
}

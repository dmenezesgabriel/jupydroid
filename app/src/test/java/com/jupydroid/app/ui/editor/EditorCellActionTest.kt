package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguageLocation
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `EditorViewModel.cellAction` - the one door the menu's asks and edits share
 * ([EditorCellActions]): `GoToDefinition` and `FormatCell` dispatch to the language server
 * asynchronously, everything else reaches [NotebookCellEdits.applyTo] unchanged.
 * `EditorViewModelTest` carries the kernel/run/edit cases; the ask-dispatch ones live here.
 */
class EditorCellActionTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * P5e-5: a "Go to definition" row answers with the target selected and the caret armed -
   * the reply names `compute_total`'s `def` in c1, so the landing lands on c1 even though the
   * ask was made from c3's caret.
   */
  @Test
  fun cellAction_goToDefinition_landsOnTheDefiningCell() = runTest {
    val viewModel = loadedEditor(cells = definitionCells(), languageServer = definitionAnswering())
    // A run is what opens the session the ask reuses - typing alone never boots a runtime.
    viewModel.kernel.runCell(viewModel.uiState.value.cells.first { it.id == "c1" })
    advanceUntilIdle()

    viewModel.cellAction(
      "c3",
      CellAction.GoToDefinition,
      CellEditorText("total = compute_total([1, 2])", TextRange(8)),
    )
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("c1", state.selectedCellId)
    assertEquals(DefinitionTarget("c1", 4, 17), state.caretLanding)
    assertEquals(0, state.noDefinitionFound)
  }

  /** An empty reply - or all-foreign locations - is a miss the screen reports once. */
  @Test
  fun cellAction_goToDefinition_withNothingFound_countsAMiss() = runTest {
    val languageServer = FakeLanguageServerPort().apply { definitionAnswer = emptyList() }
    val viewModel = loadedEditor(languageServer = languageServer)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.cellAction("c1", CellAction.GoToDefinition, CellEditorText("print(1)", TextRange(1)))
    advanceUntilIdle()
    viewModel.cellAction("c1", CellAction.GoToDefinition, CellEditorText("print(1)", TextRange(1)))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(2, state.noDefinitionFound)
    assertNull(state.caretLanding)
  }

  /** Every other action still arrives at [NotebookCellEdits] through the same door. */
  @Test
  fun cellAction_anythingElse_isTheEditItAlwaysWas() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    viewModel.cellAction("c1", CellAction.Duplicate, CellEditorText.NONE)

    assertEquals(3, viewModel.uiState.value.cells.size)
    assertEquals("print(1)", viewModel.uiState.value.cells[1].source)
  }

  /**
   * P5e-6: "Format cell" answers with the cell's source rewritten - autopep8's whole-document
   * `TextEdit` sliced back to the asked cell - as **one undoable operation**, the
   * `sourceRevisions` bump being what the cell's field reads to adopt it. Undo puts back the
   * text the ask was made over, not a debounce-stale copy.
   */
  @Test
  fun cellAction_formatCell_rewritesTheCellUndoably() = runTest {
    val viewModel = loadedEditor(
      cells = listOf(codeCell.copy(source = "x=1"), secondCodeCell),
      languageServer = FakeLanguageServerPort().apply { formattingAnswer = listOf(formatEdit("x = 1")) },
    )
    viewModel.kernel.runCell(viewModel.uiState.value.cells.first { it.id == "c1" })
    advanceUntilIdle()

    viewModel.cellAction("c1", CellAction.FormatCell, CellEditorText("x=1", TextRange(3)))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("x = 1", state.cells.first { it.id == "c1" }.source)
    assertEquals("print(2)", state.cells.first { it.id == "c3" }.source)
    assertEquals(1, state.undoable.size)
    assertEquals(1, state.sourceRevisions.getValue("c1"))

    viewModel.edits.undo()
    assertEquals("x=1", viewModel.uiState.value.cells.first { it.id == "c1" }.source)
  }

  /** The wire's `options` carry the user's `indentUnit` - two spaces here - verbatim. */
  @Test
  fun cellAction_formatCell_sendsTheIndentUnitAsFormattingOptions() = runTest {
    val languageServer = FakeLanguageServerPort()
    val viewModel = loadedEditor(
      languageServer = languageServer,
      editorSettings = FakeEditorSettingsRepository(EditorSettings(indentUnit = IndentUnit.SPACES_2)),
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.cellAction("c1", CellAction.FormatCell, CellEditorText("print(1)", TextRange(0)))
    advanceUntilIdle()

    val (_, _, options) = languageServer.formattingAsks.single()
    assertEquals(LanguageFormatOptions(tabSize = 2, insertSpaces = true), options)
  }

  /**
   * The session gate's menu-facing half - no run means nothing is asked, and the model still
   * catches up to the field's own text, which the ask syncs the way `operated` does before a
   * split: the debounced report would have done the same a blink later.
   */
  @Test
  fun cellAction_formatCell_withoutASession_neverAsks() = runTest {
    val languageServer = FakeLanguageServerPort()
    val viewModel = loadedEditor(languageServer = languageServer)

    viewModel.cellAction("c1", CellAction.FormatCell, CellEditorText("print(1) typed", TextRange(0)))
    advanceUntilIdle()

    assertTrue(languageServer.formattingAsks.isEmpty())
    assertEquals("print(1) typed", viewModel.uiState.value.cells.single().source)
    assertTrue(viewModel.uiState.value.undoable.isEmpty())
  }

  /** An answer identical to the cell's own text is the formatter's no-op: nothing to apply. */
  @Test
  fun cellAction_formatCell_withTheSameText_recordsNothing() = runTest {
    val viewModel = loadedEditor(
      cells = listOf(codeCell.copy(source = "x = 1"), secondCodeCell),
      languageServer = FakeLanguageServerPort().apply { formattingAnswer = listOf(formatEdit("x = 1")) },
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.cellAction("c1", CellAction.FormatCell, CellEditorText("x = 1", TextRange(0)))
    advanceUntilIdle()

    assertEquals("x = 1", viewModel.uiState.value.cells.first { it.id == "c1" }.source)
    assertTrue(viewModel.uiState.value.undoable.isEmpty())
    assertTrue(viewModel.uiState.value.sourceRevisions.isEmpty())
  }

  /**
   * The gate for a slow answer: a debounced report landing mid-ask leaves the cell holding a
   * different source than the reply was computed over, so the answer is dropped rather than
   * clobbering the newer text.
   */
  @Test
  fun cellAction_formatCell_afterTheCellMoved_changesNothing() = runTest {
    val languageServer = FakeLanguageServerPort().apply {
      formattingAnswer = listOf(formatEdit("x = 1"))
    }
    val viewModel = loadedEditor(
      cells = listOf(codeCell.copy(source = "x=1"), secondCodeCell),
      languageServer = languageServer,
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    languageServer.onFormattingAsk = { viewModel.editCell("c1", "x=1 typed") }

    viewModel.cellAction("c1", CellAction.FormatCell, CellEditorText("x=1", TextRange(3)))
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("x=1 typed", state.cells.first { it.id == "c1" }.source)
    assertTrue(state.undoable.isEmpty())
  }

  /**
   * autopep8's own reply shape: one `TextEdit` spanning the whole synced document, its end
   * `len(document.lines)` on the trailing empty line - `(4, 0)` for this fixture's
   * `"x=1\n\n\nprint(2)\n"` - so the slice gives back exactly the first cell's new source.
   */
  private fun formatEdit(formattedFirstCell: String) = LanguageEdit(
    LanguageRange(LanguagePosition(0, 0), LanguagePosition(4, 0)),
    "$formattedFirstCell\n\n\nprint(2)\n",
  )

  /** The def cell c1 and the use cell c3 - the jump's endpoints, named once for the test. */
  private fun definitionCells() = listOf(
    codeCell.copy(id = "c1", source = "def compute_total(items):\n    return sum(items)"),
    secondCodeCell.copy(id = "c3", source = "total = compute_total([1, 2])"),
  )

  /** The probe's own reply: `compute_total`'s `def`-line name span in the notebook's virtual doc. */
  private fun definitionAnswering() = FakeLanguageServerPort().apply {
    definitionAnswer = listOf(
      LanguageLocation(
        "file:///jupyterdroid/notebooks/nb-1.py",
        LanguageRange(LanguagePosition(0, 4), LanguagePosition(0, 17)),
      ),
    )
  }
}

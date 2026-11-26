package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document half of `Shift`+`Enter` and `Alt`+`Enter` - what the two chords do to cells,
 * head and caret once the run itself is underway.
 *
 * Both are `Private.runSelected` followed by one move
 * (`packages/notebook/src/actions.tsx:814-856`, `:864-903`): the run's own bookkeeping is
 * [withRunStarted]'s, which is why every case here reads as "the head a marked run leaves,
 * then one more thing".
 */
class RunChordTransformsTest {
  /**
   * The ordinary case is one move: `runSelected` leaves `activeCellIndex` on the last cell it ran,
   * and `runAndAdvance` adds one (`actions.tsx:843`). The document is untouched - no new cell, no
   * caret ask, nothing to undo.
   */
  @Test
  fun advance_midNotebook_leavesTheHeadOnTheNextCell() {
    val state = stateOf("a", "b", "c").withCellSelected("b").withMode(CellMode.EDIT)

    val advanced = state.withRunAdvanced(newId = "new")

    assertEquals("c", advanced.selectedCellId)
    assertEquals(state.cells, advanced.cells)
    assertNull(advanced.cellTakingCaret)
    assertFalse(advanced.dirty)
  }

  /**
   * **And the mode is `command`, not `edit`** - `runSelected`'s first line is
   * `notebook.mode = 'command'` (`actions.tsx:3020`) and nothing in the mid-notebook branch
   * raises it again. Edit returns only where the reference sets it: through the caret ask on a
   * cell it just made.
   */
  @Test
  fun advance_midNotebook_leavesCommandMode() {
    val state = stateOf("a", "b", "c").withCellSelected("b").withMode(CellMode.EDIT)

    assertEquals(CellMode.COMMAND, state.withRunAdvanced(newId = "new").mode)
  }

  /**
   * From a marked run the head lands one past its **last** cell, because `runSelected` tracks
   * `lastIndex` through the filter before deselecting (`actions.tsx:3020-3034`) and the advance
   * counts from there.
   */
  @Test
  fun advance_fromAMarkedRun_landsAfterItsLastCell() {
    val marked = stateOf("a", "b", "c", "d").withSelectionStartedAt("b").withSelectionExtendedTo("c")

    assertEquals("d", marked.withRunAdvanced(newId = "new").selectedCellId)
  }

  /**
   * At the last cell the advance has nowhere to go, so the reference appends a `defaultCell` -
   * a code cell - and lands on it in edit mode (`actions.tsx:830-849`). Ours stores that as the
   * head plus a caret ask: the cell's field takes the caret when it composes, and focus carries
   * the mode to edit.
   */
  @Test
  fun advance_onTheLastCell_appendsACodeCellAndAsksItToTakeTheCaret() {
    val advanced = stateOf("a", "b").withCellSelected("b").withRunAdvanced(newId = "new")

    val appended = advanced.cells.last()
    assertEquals("new", appended.id)
    assertEquals(CellKind.CODE, appended.kind)
    assertEquals("", appended.source)
    assertEquals("new", advanced.selectedCellId)
    assertEquals("new", advanced.cellTakingCaret)
    assertTrue(advanced.dirty)
  }

  /**
   * The appended cell lands in **command** mode with a caret ask, exactly as the reference
   * sequences it: `'command'` first, `'edit'` only once the widget exists and is in view
   * (`actions.tsx:3020`, `:839-848`).
   */
  @Test
  fun advance_onTheLastCell_asksForEditThroughTheCaret() {
    val advanced = stateOf("a").withCellSelected("a").withRunAdvanced(newId = "new")

    assertEquals(CellMode.COMMAND, advanced.mode)
    assertEquals("new", advanced.cellTakingCaret)
  }

  /**
   * A head that lands inside a collapsed section is revealed - `expandParent` follows every
   * active-cell change (`packages/notebook-extension/src/index.ts:2871-2873`), the same rule
   * [withCellRevealed] already carries for the other ways a cell becomes the head.
   */
  @Test
  fun advance_ontoACellHiddenInsideACollapsedSection_revealsIt() {
    val state = EditorUiState(
      cells = listOf(
        Cell("a", CellKind.CODE, "source of a"),
        Cell("h", CellKind.MARKDOWN, "## Section", headingCollapsed = true),
        Cell("x", CellKind.CODE, "source of x"),
      ),
      loaded = true,
    ).withCellSelected("h")

    val advanced = state.withRunAdvanced(newId = "new")

    assertEquals("x", advanced.selectedCellId)
    assertFalse(advanced.cells.single { it.id == "h" }.headingCollapsed)
  }

  /** `runAndAdvance` returns early on `!notebook.activeCell` (`actions.tsx:820-822`). */
  @Test
  fun advance_withNoCellSelected_changesNothing() {
    val state = stateOf("a", "b")

    assertSame(state, state.withRunAdvanced(newId = "new"))
  }

  /**
   * `runAndInsert` is `runSelected` then `insertCell(activeCellIndex + 1)` - **below the head
   * the run left**, which is `activeCellIndex` after deselecting (`actions.tsx:894-903`). The new
   * cell is a code cell, it becomes the head and it takes the caret, because the reference ends
   * on `notebook.mode = 'edit'` (`:918`) and our edit mode arrives through focus.
   */
  @Test
  fun insert_putsACodeCellBelowTheHeadAndAsksItToTakeTheCaret() {
    val inserted = stateOf("a", "b", "c").withCellSelected("b").withRunInserted(newId = "new")

    assertEquals(listOf("a", "b", "new", "c"), inserted.cells.map { it.id })
    assertEquals(CellKind.CODE, inserted.cells[2].kind)
    assertEquals("new", inserted.selectedCellId)
    assertEquals("new", inserted.cellTakingCaret)
    assertEquals(CellMode.COMMAND, inserted.mode)
    assertTrue(inserted.dirty)
  }

  /** Same `lastIndex` rule as the advance: below a marked run is below its last cell. */
  @Test
  fun insert_fromAMarkedRun_landsBelowItsLastCell() {
    val marked = stateOf("a", "b", "c", "d").withSelectionStartedAt("b").withSelectionExtendedTo("c")

    val inserted = marked.withRunInserted(newId = "new")

    assertEquals(listOf("a", "b", "c", "new", "d"), inserted.cells.map { it.id })
    assertEquals("new", inserted.selectedCellId)
  }

  /**
   * **Inserting under a collapsed heading puts the cell inside the folded section**, so it is
   * revealed rather than left where nobody can see it - `expandParent` again
   * (`index.ts:2871-2873`).
   */
  @Test
  fun insert_belowACollapsedHeading_revealsTheSection() {
    val state = EditorUiState(
      cells = listOf(
        Cell("h", CellKind.MARKDOWN, "## Section", headingCollapsed = true),
        Cell("x", CellKind.CODE, "source of x"),
      ),
      loaded = true,
    ).withCellSelected("h")

    val inserted = state.withRunInserted(newId = "new")

    assertEquals("new", inserted.cells[1].id)
    assertEquals("new", inserted.selectedCellId)
    assertFalse(inserted.cells.single { it.id == "h" }.headingCollapsed)
  }

  /** `runAndInsert` returns early on `!notebook.activeCell` (`actions.tsx:875-877`). */
  @Test
  fun insert_withNoCellSelected_changesNothing() {
    val state = stateOf("a", "b")

    assertSame(state, state.withRunInserted(newId = "new"))
  }

  private fun stateOf(vararg ids: String) = EditorUiState(
    cells = ids.map { Cell(it, CellKind.CODE, "source of $it") },
    loaded = true,
  )
}

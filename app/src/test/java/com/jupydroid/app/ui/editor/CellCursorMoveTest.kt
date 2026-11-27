package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `notebook:move-cursor-{up,down}` - `ArrowUp`/`K`, `ArrowDown`/`J` in command mode - against
 * `selectAbove` and `selectBelow` (`packages/notebook/src/actions.tsx:1099-1177`).
 */
class CellCursorMoveTest {
  private fun code(id: String, hidden: Boolean = false) = Cell(id, CellKind.CODE, "print(1)", sourceHidden = hidden)

  private fun md(id: String, source: String, collapsed: Boolean = false) =
    Cell(id, CellKind.MARKDOWN, source, headingCollapsed = collapsed)

  private fun notebook(vararg cells: Cell) = EditorUiState(cells = cells.toList(), loaded = true)

  @Test
  fun moving_stepsTheHeadOneCell() {
    val state = notebook(code("a"), code("b"), code("c")).withCellSelected("a")

    assertEquals("b", state.withCursorMoved(CursorDirection.DOWN).selectedCellId)
    assertEquals("a", state.withCursorMoved(CursorDirection.DOWN).withCursorMoved(CursorDirection.UP).selectedCellId)
  }

  /**
   * The scan skips `isHidden` widgets (`actions.tsx:1128-1132`): a collapsed heading's whole
   * section is walked over, though the heading itself is a stop.
   */
  @Test
  fun moving_skipsAHiddenSection() {
    val state = notebook(
      md("h", "# Chapter", collapsed = true),
      code("x"),
      code("y"),
      md("h2", "# Next"),
      code("c"),
    ).withCellSelected("h")

    val moved = state.withCursorMoved(CursorDirection.DOWN)

    // x and y are inside the collapsed section; h2 is the first stop that is not.
    assertEquals("h2", moved.selectedCellId)
    assertEquals("c", moved.withCursorMoved(CursorDirection.DOWN).selectedCellId)
  }

  /** `inputHidden` widgets are skipped the same way (`actions.tsx:1128-1132`). */
  @Test
  fun moving_skipsACellWhoseSourceIsHidden() {
    val state = notebook(
      code("a"),
      code("x", hidden = true),
      code("b"),
    ).withCellSelected("a")

    assertEquals("b", state.withCursorMoved(CursorDirection.DOWN).selectedCellId)
  }

  /**
   * `selectAbove` returns early on index 0 and `selectBelow` on the last *non-hidden* cell
   * (`actions.tsx:1110-1115`, `:1150-1155`) - **before** `deselectAll` is reached, so a marked run
   * survives a move that goes nowhere.
   */
  @Test
  fun moving_pastEitherEndIsANoOp_andKeepsTheRun() {
    val atTop = notebook(code("a"), code("b"), code("c")).withSelectionStartedAt("a")
    val atBottom = notebook(code("a"), code("b"), code("c"))
      .withSelectionStartedAt("a").withSelectionExtendedTo("c")

    assertEquals(atTop, atTop.withCursorMoved(CursorDirection.UP))
    assertEquals(atBottom, atBottom.withCursorMoved(CursorDirection.DOWN))
  }

  /** A real move is `activeCellIndex = …` then `deselectAll()` (`actions.tsx:1136-1140`). */
  @Test
  fun moving_dropsTheMarkedRun() {
    val state = notebook(code("a"), code("b"), code("c"))
      .withSelectionStartedAt("a").withSelectionExtendedTo("b")

    val moved = state.withCursorMoved(CursorDirection.DOWN)

    assertEquals("c", moved.selectedCellId)
    assertFalse(moved.selecting)
    assertNull(moved.anchorCellId)
  }

  @Test
  fun moving_withNoActiveCellIsANoOp() {
    val state = notebook(code("a"), code("b"))

    assertEquals(state, state.withCursorMoved(CursorDirection.DOWN))
  }

  /**
   * Every key-driven move is counted, which is the flag the screen's scroll-and-refocus effect
   * listens for - a counter and not a boolean so two moves in a row are two changes.
   */
  @Test
  fun moving_countsTheKeyMove() {
    val state = notebook(code("a"), code("b"), code("c")).withCellSelected("a")

    val moved = state.withCursorMoved(CursorDirection.DOWN)

    assertEquals(state.headMovesByKey + 1, moved.headMovesByKey)
    // A move that went nowhere counts nothing - there is no head to scroll to.
    assertEquals(
      0,
      notebook(code("a")).withCellSelected("a").withCursorMoved(CursorDirection.UP).headMovesByKey,
    )
  }

  /**
   * The mode is where it was (`selectAbove`/`selectBelow` never touch `notebook.mode`), which is
   * what lets the same keys stay the field's own arrows in edit mode - they never reach here.
   */
  @Test
  fun moving_leavesTheModeAlone() {
    val state = notebook(code("a"), code("b")).withCellSelected("a")

    assertEquals(CellMode.COMMAND, state.withCursorMoved(CursorDirection.DOWN).mode)
  }
}

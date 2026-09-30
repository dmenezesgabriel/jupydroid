package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which markdown cells are showing their source, against the reference that defines it.
 *
 * `MarkdownCell._rendered` is the reference's flag and rendered is its resting state
 * (`packages/cells/src/widget.ts`), which is why this stores the cells being **edited** rather than
 * the ones being rendered: an empty set is then the right answer for every notebook ever opened.
 */
class MarkdownEditingTest {
  @Test
  fun aMarkdownCellIsRenderedUntilSomebodyOpensIt() {
    val state = stateOf("md")

    assertTrue(state.isMarkdownRendered("md"))
    assertFalse(state.withMarkdownEditorOpened("md").isMarkdownRendered("md"))
  }

  /**
   * **Running a markdown cell renders it**, which is what running one means in Jupyter:
   * `case 'markdown': (cell as MarkdownCell).rendered = true` (`packages/notebook/src/cellexecutor.ts`).
   */
  @Test
  fun renderingClosesTheEditor() {
    val state = stateOf("md").withMarkdownEditorOpened("md")

    assertTrue(state.withMarkdownRendered(listOf("md")).isMarkdownRendered("md"))
  }

  /**
   * **And unfolds it**, which is the line straight after: `cell.inputHidden = false`. A rendered
   * cell hidden behind a collapse placeholder would be a render nobody can see.
   */
  @Test
  fun renderingUnfoldsTheSourceToo() {
    val state = EditorUiState(
      cells = listOf(Cell("md", CellKind.MARKDOWN, "# heading", sourceHidden = true)),
      loaded = true,
    )

    assertFalse(state.withMarkdownRendered(listOf("md")).cells.single().sourceHidden)
  }

  /** Only the cells named, so rendering one cell of a run leaves the others being written. */
  @Test
  fun renderingTouchesOnlyTheCellsNamed() {
    val state = stateOf("one", "two").withMarkdownEditorOpened("one").withMarkdownEditorOpened("two")

    val rendered = state.withMarkdownRendered(listOf("one"))

    assertTrue(rendered.isMarkdownRendered("one"))
    assertFalse(rendered.isMarkdownRendered("two"))
  }

  /**
   * A cell nobody had opened is already rendered, so rendering it changes nothing at all - and the
   * state must come back **identical**, or a run over a notebook of untouched markdown would mark
   * it dirty and rewrite the file for having been looked at.
   */
  @Test
  fun renderingAnAlreadyRenderedCellChangesNothing() {
    val state = stateOf("md")

    assertEquals(state, state.withMarkdownRendered(listOf("md")))
  }

  /**
   * **A tap asks for the caret; nothing else does.**
   *
   * The field used to request focus simply for having been composed, which was right while a tap
   * was the only way to open one. A second way in - a command that unrenders - looked like this:
   * two editors appearing in one frame, both asking, and the soft keyboard up over a notebook
   * nobody was typing in. The
   * reference leaves the notebook in command mode after `changeCellType` (`actions.tsx:3314`,
   * `notebook.deselectAll()` and no mode change).
   */
  @Test
  fun openingACellByTapIsWhatAsksForTheCaret() {
    assertEquals("md", stateOf("md").withMarkdownEditorOpened("md").markdownTakingCaret)
  }

  /** Rendering it again takes the request back, so re-opening it later asks afresh. */
  @Test
  fun renderingTheCellWithdrawsTheRequest() {
    val open = stateOf("md").withMarkdownEditorOpened("md")

    assertNull(open.withMarkdownRendered(listOf("md")).markdownTakingCaret)
  }

  private fun stateOf(vararg ids: String) = EditorUiState(
    cells = ids.map { Cell(it, CellKind.MARKDOWN, "# $it") },
    loaded = true,
  )
}

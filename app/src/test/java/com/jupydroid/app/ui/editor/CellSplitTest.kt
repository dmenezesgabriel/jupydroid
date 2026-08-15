package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cutting one cell into several - `notebook:split-cell-at-cursor`.
 *
 * Every rule is read out of `splitCell` (`packages/notebook/src/actions.tsx:187-320`) rather than
 * guessed, and the two that decide the shape of the feature are both counter-intuitive: the
 * **original** cell becomes the **last** fragment, and the outputs stay with it.
 *
 * These drive the transform directly, so each state is built holding what its editor would hold.
 * That the two are kept in step is `CellEdits.applying`'s job and `CellUndoTest`'s to prove.
 */
class CellSplitTest {
  private val notebook = EditorUiState(
    cells = listOf(
      Cell("c1", CellKind.CODE, "first"),
      Cell("c2", CellKind.CODE, "second"),
      Cell("m1", CellKind.MARKDOWN, "# notes"),
    ),
    loaded = true,
  )

  /** A cell's unmodelled nbformat JSON: the fragments of a split must arrive carrying it. */
  private val preservedWithTag = cellJson { putJsonArray("tags") { add("keep") } }

  /** `iopub.execute_input` with no `shell.execute_reply` is the reference's test for "running". */
  private val preservedMidRun = cellJson {
    putJsonArray("tags") { add("keep") }
    putJsonObject("execution") { put("iopub.execute_input", "2026-08-15T00:00:00Z") }
  }

  private val preservedFinished = cellJson {
    putJsonArray("tags") { add("keep") }
    putJsonObject("execution") {
      put("iopub.execute_input", "2026-08-15T00:00:00Z")
      put("shell.execute_reply", "2026-08-15T00:00:01Z")
    }
  }

  /** A cell holding what its editor holds, which is what [CellEdits.applying] guarantees. */
  private fun EditorUiState.editing(source: String) = withSource("c1", source)

  /**
   * `splitCell` (`actions.tsx:187-320`), and it contradicts the obvious answer twice.
   *
   * The text before the caret becomes a **new** cell and the original keeps what is after it -
   * *"The last piece will remain in the original cell to preserve kernel connection"* - so the
   * new cells are inserted at the original's index and the original ends up last.
   */
  @Test
  fun splittingAtTheCaretLeavesTheTextBeforeItInANewCellAbove() {
    val split = notebook.editing("first\nsecond").withCellSplit("c1", TextRange(6), listOf("new"))

    assertEquals(listOf("new", "c1", "c2", "m1"), split.cells.map { it.id })
    assertEquals("first", split.cells[0].source)
    assertEquals("second", split.cells[1].source)
  }

  /**
   * The corollary of the original cell being the last fragment, and the reason the reference
   * gives for it: every new cell is created with `outputs: undefined`, so the results and the
   * `[7]` stay on the piece that still has the kernel's attention.
   */
  @Test
  fun theOutputsAndTheCountStayOnTheLastFragment() {
    val ran = notebook.editing("first\nsecond").copy(
      outputs = mapOf("c1" to listOf(CellOutput.Stream(StreamName.STDOUT, "ran"))),
      executionCounts = mapOf("c1" to 7),
    )

    val split = ran.withCellSplit("c1", TextRange(6), listOf("new"))

    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "ran")), split.outputs["c1"])
    assertEquals(7, split.executionCounts["c1"])
    assertNull(split.outputs["new"])
    assertNull(split.executionCounts["new"])
  }

  /**
   * *"append start and end to handle selections"* - a selection contributes two offsets, so the
   * selected text becomes a cell of its own between the two halves around it.
   */
  @Test
  fun aSelectionSplitsIntoThreePieces() {
    val split = notebook.editing(THREE_LINES).withCellSplit("c1", TextRange(6, 12), listOf("a", "b"))

    assertEquals(listOf("a", "b", "c1", "c2", "m1"), split.cells.map { it.id })
    assertEquals(listOf("first", "second", "third"), split.cells.take(3).map { it.source })
  }

  /** `else if (end < start)` - the offsets are pushed low first, so dragging backwards is the same. */
  @Test
  fun aBackwardsSelectionSplitsTheSameWay() {
    val editing = notebook.editing(THREE_LINES)

    val forwards = editing.withCellSplit("c1", TextRange(6, 12), listOf("a", "b"))
    val backwards = editing.withCellSplit("c1", TextRange(12, 6), listOf("a", "b"))

    assertEquals(forwards, backwards)
  }

  /**
   * `.replace(/^\n+/, '').replace(/\n+$/, '')` - **newlines only**. Stripping whitespace would
   * take the indentation of a fragment that starts inside a block with it, and the fragment would
   * no longer be the code it was cut from.
   */
  @Test
  fun aFragmentLosesItsOwnNewlinesAndKeepsItsIndentation() {
    val split = notebook.editing("if x:\n\n    body\n").withCellSplit("c1", TextRange(6), listOf("new"))

    assertEquals("if x:", split.cells[0].source)
    assertEquals("    body", split.cells[1].source)
  }

  /** *"If there is no content, two empty cells will be created."* */
  @Test
  fun splittingAnEmptyCellGivesTwoEmptyCells() {
    val split = notebook.editing("").withCellSplit("c1", TextRange(0), listOf("new"))

    assertEquals(listOf("new", "c1", "c2", "m1"), split.cells.map { it.id })
    assertEquals(listOf("", ""), split.cells.take(2).map { it.source })
  }

  /** The caret past the last character: the whole source moves up and the original is emptied. */
  @Test
  fun splittingAtTheEndLeavesTheOriginalEmpty() {
    val split = notebook.editing("first").withCellSplit("c1", TextRange(5), listOf("new"))

    assertEquals("first", split.cells[0].source)
    assertEquals("", split.cells[1].source)
  }

  /** *"Both cells will have the same type as the original cell"*, and its metadata is cloned. */
  @Test
  fun theFragmentsKeepTheKindAndTheUnmodelledJson() {
    val split = stateOfOneCell(CellKind.MARKDOWN, preservedWithTag).withCellSplit("c1", TextRange(2), listOf("new"))

    assertEquals(CellKind.MARKDOWN, split.cells[0].kind)
    assertEquals(preservedWithTag, split.cells[0].preserved)
  }

  /**
   * *"If execution metadata is present but missing execute_reply (i.e. it is in running state),
   * remove execution metadata entirely for new cells."* The fragments were never run, so a
   * half-written record of the original's run does not describe them - while the rest of the
   * metadata, `tags` included, still does.
   */
  @Test
  fun aRunningCellsExecutionMetadataDoesNotFollowTheFragments() {
    val split = stateOfOneCell(CellKind.CODE, preservedMidRun).withCellSplit("c1", TextRange(2), listOf("new"))

    assertEquals(preservedWithTag, split.cells[0].preserved)
    // The original's own record is untouched: it is the cell that is actually running.
    assertEquals(preservedMidRun, split.cells[1].preserved)
  }

  /** A finished run's `execution` metadata is copied, because it is a complete record. */
  @Test
  fun aFinishedRunsExecutionMetadataDoesFollowTheFragments() {
    val split = stateOfOneCell(CellKind.CODE, preservedFinished).withCellSplit("c1", TextRange(2), listOf("new"))

    assertEquals(preservedFinished, split.cells[0].preserved)
  }

  /**
   * *"If text was selected, the cell containing the selection will be activated"* - which is the
   * fragment the selection became. A bare caret leaves it alone: the original cell is where it
   * was, and it is the one the split was invoked from.
   */
  @Test
  fun aSelectionLeavesItsOwnFragmentSelected() {
    val editing = notebook.editing(THREE_LINES).copy(selectedCellId = "c1")

    assertEquals("b", editing.withCellSplit("c1", TextRange(6, 12), listOf("a", "b")).selectedCellId)
    assertEquals("c1", editing.withCellSplit("c1", TextRange(6), listOf("a", "b")).selectedCellId)
  }

  @Test
  fun splittingAStaleIdChangesNothing() {
    assertEquals(notebook, notebook.withCellSplit("gone", TextRange(1), listOf("new")))
  }

  private fun stateOfOneCell(kind: CellKind, preserved: JsonObject) =
    EditorUiState(cells = listOf(Cell("c1", kind, "a\nb", preserved = preserved)), loaded = true)

  private fun cellJson(metadata: JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject { putJsonObject("metadata", metadata) }

  private companion object {
    const val THREE_LINES = "first\nsecond\nthird"
  }
}

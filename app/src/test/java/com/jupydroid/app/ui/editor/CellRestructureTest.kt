package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restructuring a notebook: insert above, change a cell's type, merge two, and cut/copy/paste.
 *
 * The actions JupyterLab has that this app did not. Every rule below is read out of
 * `packages/notebook/src/actions.tsx` rather than guessed, and several are not the obvious
 * answer - which is the same thing the cell menu found in Step 4b.
 */
class CellRestructureTest {
  private val notebook = EditorUiState(
    cells = listOf(
      Cell("c1", CellKind.CODE, "first"),
      Cell("c2", CellKind.CODE, "second"),
      Cell("m1", CellKind.MARKDOWN, "# notes"),
    ),
    loaded = true,
  )

  // --- insert above ---------------------------------------------------------------------

  @Test
  fun insertAbovePutsTheNewCellBeforeTheTarget() {
    val inserted = notebook.withCellInsertedAbove("c2", CellKind.CODE, newId = "new")

    assertEquals(listOf("c1", "new", "c2", "m1"), inserted.cells.map { it.id })
  }

  @Test
  fun insertAboveTheFirstCellStillWorks() {
    val inserted = notebook.withCellInsertedAbove("c1", CellKind.MARKDOWN, newId = "new")

    assertEquals(listOf("new", "c1", "c2", "m1"), inserted.cells.map { it.id })
    assertEquals(CellKind.MARKDOWN, inserted.cells.first().kind)
  }

  @Test
  fun insertAboveAStaleIdChangesNothing() {
    assertEquals(notebook, notebook.withCellInsertedAbove("gone", CellKind.CODE, newId = "new"))
  }

  // --- change cell type -----------------------------------------------------------------

  /**
   * `changeCellType` deletes and reinserts the cell **with `id: raw.id`**
   * (`actions.tsx:3271-3286`), so the identity survives - unlike a merge, which does not pass
   * one and gets a fresh id.
   */
  @Test
  fun changingTypeKeepsTheCellsIdAndSource() {
    val changed = notebook.withCellsKindChanged(listOf("c1"), CellKind.MARKDOWN)

    assertEquals(listOf("c1", "c2", "m1"), changed.cells.map { it.id })
    assertEquals(CellKind.MARKDOWN, changed.cells.first().kind)
    assertEquals("first", changed.cells.first().source)
  }

  /**
   * *"After change of type outputs are deleted"* (`actions.tsx:3273-3275`). A markdown cell
   * cannot hold outputs at all, and a code cell's results no longer describe its source.
   */
  @Test
  fun changingTypeDiscardsOutputsAndTheExecutionCount() {
    val ran = notebook.copy(
      outputs = mapOf("c1" to listOf(CellOutput.Stream(StreamName.STDOUT, "old"))),
      executionCounts = mapOf("c1" to 4),
    )

    val changed = ran.withCellsKindChanged(listOf("c1"), CellKind.MARKDOWN)

    assertNull(changed.outputs["c1"])
    assertNull(changed.executionCounts["c1"])
  }

  /** `if (child.model.type !== value)` - asking for the type it already has does nothing. */
  @Test
  fun changingTypeToTheOneItAlreadyHasChangesNothing() {
    assertEquals(notebook, notebook.withCellsKindChanged(listOf("c1"), CellKind.CODE))
  }

  /**
   * `notebook:change-cell-to-raw` (`packages/notebook-extension/src/index.ts:214`). The same
   * transaction as the markdown one, and it has to be: `changeCellType` is one function over
   * `nbformat.CellType` and reinserts with `id: raw.id` whichever type was asked for.
   *
   * Dropping the results is not a nicety here - the shipped `nbformat/v4/nbformat.v4.schema.json`
   * makes `raw_cell` `additionalProperties: false`, so a raw cell carrying `outputs` or
   * `execution_count` is a notebook `nbformat.validate` rejects.
   */
  @Test
  fun changingToRawKeepsTheIdAndDropsWhatARawCellMayNotHold() {
    val ran = notebook.copy(
      outputs = mapOf("c1" to listOf(CellOutput.Stream(StreamName.STDOUT, "old"))),
      executionCounts = mapOf("c1" to 4),
    )

    val changed = ran.withCellsKindChanged(listOf("c1"), CellKind.RAW)

    assertEquals(listOf("c1", "c2", "m1"), changed.cells.map { it.id })
    assertEquals(CellKind.RAW, changed.cells.first().kind)
    assertEquals("first", changed.cells.first().source)
    assertNull(changed.outputs["c1"])
    assertNull(changed.executionCounts["c1"])
  }

  /** And back again, which is the only way a raw cell ever becomes runnable. */
  @Test
  fun aRawCellCanBecomeCodeAgain() {
    val raw = notebook.withCellsKindChanged(listOf("c1"), CellKind.RAW)

    assertEquals(CellKind.CODE, raw.withCellsKindChanged(listOf("c1"), CellKind.CODE).cells.first().kind)
  }

  /**
   * **A cell that becomes markdown is left showing its source**, which is `changeCellType`'s last
   * act: *"Fetch the new widget and unrender it"* - `(child as MarkdownCell).rendered = false`
   * (`packages/notebook/src/actions.tsx:3308-3312`).
   *
   * Ours rendered it at once, because rendered is the resting state here and nothing put the cell
   * on [EditorUiState.markdownBeingEdited] - so converting a code cell hid the very text that had
   * just been converted behind rendered prose, with a tap needed to get it back.
   */
  @Test
  fun becomingMarkdownLeavesTheCellShowingItsSource() {
    val changed = notebook.withCellsKindChanged(listOf("c1"), CellKind.MARKDOWN)

    assertFalse(changed.isMarkdownRendered("c1"))
  }

  /**
   * **Every cell the command visits, not only the ones whose type changed.** That `if` is outside
   * the `child.model.type !== value` branch, so a mixed run changed to markdown unrenders the
   * cells that were already markdown too - which is reachable here, since the head decides the
   * menu and a run may hold both kinds.
   */
  @Test
  fun becomingMarkdownUnrendersEvenTheCellsThatAlreadyWere() {
    val changed = notebook.withCellsKindChanged(listOf("c1", "c2", "m1"), CellKind.MARKDOWN)

    assertFalse(changed.isMarkdownRendered("m1"))
  }

  /**
   * **And it does not ask for the caret**, which is the difference between a tap and a command.
   * Measured rather than reasoned: with the field requesting focus for having been composed, a run
   * changed to markdown put the soft keyboard up over a notebook nobody was typing in, and two
   * editors raced for the caret (`evidence/p2e-3-change-to-markdown/`). The reference ends
   * `changeCellType` in command mode.
   */
  @Test
  fun becomingMarkdownDoesNotAskForTheCaret() {
    val changed = notebook.withCellsKindChanged(listOf("c1", "c2"), CellKind.MARKDOWN)

    assertNull(changed.markdownTakingCaret)
  }

  /** And a cell leaving markdown must not linger on the list, or coming back would find it open. */
  @Test
  fun leavingMarkdownTakesTheCellOffTheEditedList() {
    val open = notebook.withMarkdownEditorOpened("m1")

    val code = open.withCellsKindChanged(listOf("m1"), CellKind.CODE)

    assertTrue(code.withCellsKindChanged(listOf("m1"), CellKind.MARKDOWN).markdownBeingEdited.contains("m1"))
    assertFalse(code.markdownBeingEdited.contains("m1"))
  }

  /**
   * **A request made by a tap does not survive the cell changing type**, which is the hole the
   * first version of this rule left: tap a markdown cell to open it, change it to code, change it
   * back, and the request nobody withdrew would take the caret - a command doing what only a tap
   * may do, three taps after the tap.
   */
  @Test
  fun changingTypeWithdrawsACaretRequestMadeForThatCell() {
    val tapped = notebook.withMarkdownEditorOpened("m1")

    val backAgain = tapped
      .withCellsKindChanged(listOf("m1"), CellKind.CODE)
      .withCellsKindChanged(listOf("m1"), CellKind.MARKDOWN)

    assertFalse("it is open for editing", backAgain.isMarkdownRendered("m1"))
    assertNull("but nobody asked to type in it", backAgain.markdownTakingCaret)
  }

  /**
   * The early return still has to hold where nothing at all would happen, because
   * [EditorUiState.withMarkdownRendered]'s reason applies here too: a state that comes back merely
   * equal-but-new marks the notebook dirty and rewrites the file.
   */
  @Test
  fun askingForTheTypeACellAlreadyHasStillChangesNothingEvenForMarkdown() {
    val allMarkdown = notebook.withCellsKindChanged(listOf("c1", "c2", "m1"), CellKind.MARKDOWN)

    assertEquals(allMarkdown, allMarkdown.withCellsKindChanged(listOf("c1", "c2", "m1"), CellKind.MARKDOWN))
  }

  // --- merge ----------------------------------------------------------------------------

  /** `toMerge.join(addExtraLine ? '\n\n' : '\n')`, and `addExtraLine` defaults to true. */
  @Test
  fun mergingAboveJoinsTheSourcesWithABlankLineBetween() {
    val merged = notebook.withCellsMerged(listOf("c2"), MergeInto.ABOVE, newId = "merged")

    assertEquals("first\n\nsecond", merged.cells.first().source)
    assertEquals(listOf("merged", "m1"), merged.cells.map { it.id })
  }

  @Test
  fun mergingBelowJoinsInReadingOrderToo() {
    val merged = notebook.withCellsMerged(listOf("c1"), MergeInto.BELOW, newId = "merged")

    assertEquals("first\n\nsecond", merged.cells.first().source)
  }

  /**
   * The **active** cell decides the type: `const { cell_type, metadata } = primaryModel.toJSON()`
   * (`actions.tsx:431`). Merging a markdown cell into a code cell gives code, and the other way
   * round gives markdown - it depends on which one you invoked it from.
   */
  @Test
  fun theCellYouInvokedItFromDecidesTheType() {
    val intoMarkdown = notebook.withCellsMerged(listOf("m1"), MergeInto.ABOVE, newId = "merged")

    assertEquals(CellKind.MARKDOWN, intoMarkdown.cells.last().kind)
    assertEquals("second\n\n# notes", intoMarkdown.cells.last().source)
  }

  /** *"We can trust this cell because the outputs will be removed."* (`actions.tsx:432-434`) */
  @Test
  fun mergingDiscardsBothCellsOutputs() {
    val ran = notebook.copy(
      outputs = mapOf("c1" to listOf(CellOutput.Stream(StreamName.STDOUT, "old"))),
      executionCounts = mapOf("c1" to 4, "c2" to 5),
    )

    val merged = ran.withCellsMerged(listOf("c2"), MergeInto.ABOVE, newId = "merged")

    assertEquals(emptyMap<String, Any>(), merged.outputs)
    assertEquals(emptyMap<String, Any>(), merged.executionCounts)
  }

  /** *"Bail if it is the first cell."* / *"Bail if it is the last cell."* */
  @Test
  fun thereIsNothingToMergeIntoPastEitherEnd() {
    assertEquals(notebook, notebook.withCellsMerged(listOf("c1"), MergeInto.ABOVE, newId = "merged"))
    assertEquals(notebook, notebook.withCellsMerged(listOf("m1"), MergeInto.BELOW, newId = "merged"))
  }

  // --- cut / copy / paste ---------------------------------------------------------------

  @Test
  fun copyingPutsTheCellOnTheClipboardAndLeavesItAlone() {
    val copied = notebook.withCellsCopied(listOf("c2"))

    assertEquals(listOf("c2"), copied.clipboard.map { it.id })
    assertEquals(listOf("c1", "c2", "m1"), copied.cells.map { it.id })
  }

  /** `copyOrCut(cut = true)` calls `deleteCells` after filling the clipboard. */
  @Test
  fun cuttingCopiesAndThenRemoves() {
    val cut = notebook.withCellsCut(listOf("c2"), replacementId = "fresh")

    assertEquals(listOf("c2"), cut.clipboard.map { it.id })
    assertEquals(listOf("c1", "m1"), cut.cells.map { it.id })
  }

  @Test
  fun pastingPutsTheClipboardBelowTheTarget() {
    val pasted = notebook.withCellsCopied(listOf("c1")).withClipboardPasted("m1", newIds = listOf("copy"))

    assertEquals(listOf("c1", "c2", "m1", "copy"), pasted.cells.map { it.id })
    assertEquals("first", pasted.cells.last().source)
  }

  /**
   * **A pasted cell gets a fresh id, except a code cell that was *cut*** - which keeps its own
   * (`actions.tsx:1666-1677`). The exception is not cosmetic: a cut-and-pasted code cell is the
   * same cell moving, and its identity is what lets a running execution reattach to it.
   */
  @Test
  fun aCutCodeCellKeepsItsIdentityWhenPastedBack() {
    val pasted = notebook.withCellsCut(listOf("c2"), replacementId = "fresh")
      .withClipboardPasted("c1", newIds = listOf("would-be-new"))

    assertEquals(listOf("c1", "c2", "m1"), pasted.cells.map { it.id })
  }

  /** A copied cell is a second cell, so it cannot keep the id the original still holds. */
  @Test
  fun aCopiedCellIsGivenANewIdentity() {
    val pasted = notebook.withCellsCopied(listOf("c2")).withClipboardPasted("c1", newIds = listOf("copy"))

    assertEquals(listOf("c1", "copy", "c2", "m1"), pasted.cells.map { it.id })
  }

  /** *"This is a no-op if there is no cell data on the clipboard."* */
  @Test
  fun pastingAnEmptyClipboardChangesNothing() {
    assertEquals(notebook, notebook.withClipboardPasted("c1", newIds = listOf("copy")))
  }

  @Test
  fun cuttingTheLastCellStillLeavesSomethingToTypeInto() {
    val single = EditorUiState(cells = listOf(Cell("only", CellKind.CODE, "x")), loaded = true)

    val cut = single.withCellsCut(listOf("only"), replacementId = "fresh")

    assertEquals(listOf("fresh"), cut.cells.map { it.id })
    assertEquals(listOf("only"), cut.clipboard.map { it.id })
  }
}

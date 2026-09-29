package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A heading owns a **section** of the document, and what a collapsed one hides is derived from
 * that rather than stored.
 *
 * Every rule is read out of `packages/notebook/src/actions.tsx` and
 * `packages/notebook/src/widget.ts`; the tests name the line.
 */
class CellHeadingsTest {
  private fun md(id: String, source: String) = Cell(id, CellKind.MARKDOWN, source)

  private fun code(id: String) = Cell(id, CellKind.CODE, "print(1)")

  /**
   * ```
   * h1  # Chapter
   * a   code
   * h2  ## Part
   * b   code
   * h1b # Next chapter
   * c   code
   * ```
   */
  private val notebook = EditorUiState(
    cells = listOf(
      md("h1", "# Chapter"),
      code("a"),
      md("h2", "## Part"),
      code("b"),
      md("h1b", "# Next chapter"),
      code("c"),
    ),
    loaded = true,
  )

  // --- the level a cell is --------------------------------------------------------------

  /**
   * `getHeadingInfo` answers for markdown alone; a code or raw cell gets `headingLevel: 7`
   * (`actions.tsx:2481-2493`), a number no section boundary can ever be. Null says the same here
   * without inviting anyone to compare it.
   */
  @Test
  fun onlyAMarkdownCellHasAHeadingLevel() {
    assertEquals(1, md("h", "# Chapter").headingLevel)
    assertNull(Cell("c", CellKind.CODE, "# not markdown").headingLevel)
    assertNull(Cell("r", CellKind.RAW, "# not markdown").headingLevel)
    assertNull(md("p", "just prose").headingLevel)
  }

  // --- the section a heading owns -------------------------------------------------------

  /**
   * `setHeadingCollapse` walks forward from the heading and stops at the first heading of **equal
   * or higher** level (`actions.tsx:2374-2405`), so an H1's section swallows the H2 under it.
   */
  @Test
  fun aSectionRunsToTheNextHeadingOfEqualOrHigherLevel() {
    assertEquals(listOf("a", "h2", "b"), notebook.headingSectionOf("h1"))
    assertEquals(listOf("b"), notebook.headingSectionOf("h2"))
  }

  /** The last section runs to the end of the notebook - the loop's `cellNum === length` branch. */
  @Test
  fun theLastSectionRunsToTheEndOfTheNotebook() {
    assertEquals(listOf("c"), notebook.headingSectionOf("h1b"))
  }

  /** A cell that is not a heading owns nothing: `setHeadingCollapse` returns before its loop. */
  @Test
  fun aCellThatIsNotAHeadingOwnsNoSection() {
    assertEquals(emptyList<String>(), notebook.headingSectionOf("a"))
    assertEquals(emptyList<String>(), notebook.headingSectionOf("gone"))
  }

  /** A heading with nothing under it before the next one owns an empty section, not a null. */
  @Test
  fun aHeadingImmediatelyFollowedByAnotherOwnsNothing() {
    val backToBack = EditorUiState(cells = listOf(md("h1", "# One"), md("h2", "# Two")), loaded = true)

    assertEquals(emptyList<String>(), backToBack.headingSectionOf("h1"))
  }

  // --- what a collapsed heading hides ----------------------------------------------------

  /** Nothing is hidden while nothing is collapsed, whatever the headings say. */
  @Test
  fun aNotebookWithNoCollapsedHeadingHidesNothing() {
    assertEquals(emptySet<String>(), notebook.hiddenCellIds)
  }

  @Test
  fun aCollapsedHeadingHidesItsSectionAndNotItself() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true)

    assertEquals(setOf("a", "h2", "b"), collapsed.hiddenCellIds)
    assertTrue("the heading itself stays on screen", "h1" !in collapsed.hiddenCellIds)
  }

  /**
   * **Expanding respects a locally collapsed sub-heading.** `setHeadingCollapse`'s `localCollapsed`
   * branch leaves a collapsed H2 visible with its own children still hidden when the H1 above it is
   * expanded (`actions.tsx:2400-2420`). Deriving the hidden set as the union of every collapsed
   * heading's section gives that for free.
   */
  @Test
  fun expandingAHeadingLeavesACollapsedSubHeadingCollapsed() {
    val both = notebook
      .withHeadingCollapsed("h1", collapsed = true)
      .withHeadingCollapsed("h2", collapsed = true)

    assertEquals(setOf("a", "h2", "b"), both.hiddenCellIds)
    assertEquals(setOf("b"), both.withHeadingCollapsed("h1", collapsed = false).hiddenCellIds)
  }

  /**
   * Hidden-ness is recomputed from the order, never carried: `_refreshCollapsedHeadingVisibility`
   * is *"Recompute hidden cells from the current collapsed heading structure"*
   * (`widget.ts:916-940`), and `moveCells` calls it after reordering (`:462`). Here that is a
   * derivation rather than a re-run, so a move cannot leave a stale flag behind.
   */
  @Test
  fun movingACellOutOfACollapsedSectionShowsItAgain() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true)

    val moved = collapsed.withCellsMoved(listOf("b"), CellMove.DOWN).withCellsMoved(listOf("b"), CellMove.DOWN)

    assertEquals(setOf("a", "h2"), moved.hiddenCellIds)
  }

  /** Collapsing is persisted, so it is an edit the notebook has to be written back for. */
  @Test
  fun collapsingAHeadingDirtiesTheNotebook() {
    assertTrue(notebook.withHeadingCollapsed("h1", collapsed = true).dirty)
  }

  /** A stale id is a no-op, which is every transform's promise in this package. */
  @Test
  fun collapsingACellThatIsGoneChangesNothing() {
    assertEquals(notebook, notebook.withHeadingCollapsed("gone", collapsed = true))
  }

  /**
   * What a scroll-to-cell has to index into, since a `LazyColumn` only composes what
   * [hiddenCellIds] leaves out - the same filter [EditorContent] itself applies, pulled out here
   * so P2h-2's click-to-jump reads one definition rather than a second copy of it.
   */
  @Test
  fun cellsOnScreenExcludesEveryHiddenCell() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true)

    assertEquals(listOf("h1", "h1b", "c"), collapsed.cellsOnScreen.map { it.id })
  }

  // --- insert a heading -----------------------------------------------------------

  /**
   * `insertSameLevelHeadingAbove` inserts at the active cell's own index (`actions.tsx:1183-1201`),
   * so the new cell takes its place and pushes it down.
   */
  @Test
  fun insertAboveLandsOnTheHeadsOwnIndex() {
    val inserted = notebook.copy(selectedCellId = "h2").withHeadingInserted(SectionReach.ABOVE, "new")

    assertEquals(listOf("h1", "a", "new", "h2", "b", "h1b", "c"), inserted.cells.map { it.id })
  }

  /**
   * **Below means the end of the section, not the next cell.** `insertSameLevelHeadingBelow` inserts
   * at `findLowerEqualLevelHeadingBelow` (`actions.tsx:1208-1231`) - the start of the next sibling
   * section - which is the rule a first guess gets wrong.
   */
  @Test
  fun insertBelowLandsAtTheEndOfTheSectionAndNotAfterTheCell() {
    val inserted = notebook.copy(selectedCellId = "h1").withHeadingInserted(SectionReach.BELOW, "new")

    assertEquals(listOf("h1", "a", "h2", "b", "new", "h1b", "c"), inserted.cells.map { it.id })
  }

  /** With no sibling below it, the section runs to the end and so does the insert. */
  @Test
  fun insertBelowInTheLastSectionLandsAtTheEndOfTheNotebook() {
    val inserted = notebook.copy(selectedCellId = "h1b").withHeadingInserted(SectionReach.BELOW, "new")

    assertEquals(listOf("h1", "a", "h2", "b", "h1b", "c", "new"), inserted.cells.map { it.id })
  }

  /**
   * `determineHeadingLevel` answers the cell's **own** level if it is a heading, otherwise its
   * parent heading's (`actions.tsx:3576-3597`) - so a code cell inside an H2 section gets an H2.
   */
  @Test
  fun theNewHeadingTakesTheLevelOfTheSectionItIsIn() {
    assertEquals("## ", insertedSourceAt("b", SectionReach.ABOVE))
    assertEquals("# ", insertedSourceAt("h1", SectionReach.ABOVE))
    assertEquals("## ", insertedSourceAt("h2", SectionReach.ABOVE))
  }

  /**
   * `headingLevel == -1` - no heading at or above the cell - falls back to 1
   * (`actions.tsx:1194`, `:1216`), which is also every cell of a notebook with no headings at all.
   */
  @Test
  fun aCellWithNoHeadingAboveItGetsAnH1() {
    val prose = EditorUiState(cells = listOf(code("a"), code("b")), loaded = true, selectedCellId = "b")

    assertEquals("# ", prose.withHeadingInserted(SectionReach.ABOVE, "new").cells.single { it.id == "new" }.source)
  }

  /** The new cell is markdown and empty but for its marker, and takes the caret to type into. */
  @Test
  fun theNewHeadingIsAMarkdownCellOpenForTyping() {
    val inserted = notebook.copy(selectedCellId = "h1").withHeadingInserted(SectionReach.ABOVE, "new")

    val cell = inserted.cells.single { it.id == "new" }
    assertEquals(CellKind.MARKDOWN, cell.kind)
    assertTrue("new" in inserted.markdownBeingEdited)
    assertEquals("new", inserted.markdownTakingCaret)
    assertEquals("new", inserted.selectedCellId)
  }

  /** Nothing to insert relative to, so nothing happens - every transform's promise here. */
  @Test
  fun insertingWithNoHeadSelectedChangesNothing() {
    val headless = notebook.copy(selectedCellId = null)

    assertEquals(headless, headless.withHeadingInserted(SectionReach.ABOVE, "new"))
  }

  private fun insertedSourceAt(cellId: String, reach: SectionReach): String =
    notebook.copy(selectedCellId = cellId).withHeadingInserted(reach, "new")
      .cells.single { it.id == "new" }.source

  // --- change a cell to a heading -------------------------------------------------

  /**
   * `Private.setMarkdownHeader` replaces `^#+\s*` - or, failing that, `^\s*` - with
   * `'#'.repeat(level) + ' '` (`actions.tsx:3429-3438`). One regex with two alternatives, so it
   * strips a header the cell already had *or* the leading whitespace it did not.
   */
  @Test
  fun aHeadingIsWrittenOntoTheFrontOfTheSource() {
    assertEquals("## Results", markdownHeader("Results", 2))
    assertEquals("# Results", markdownHeader("#### Results", 1))
    assertEquals("### Results", markdownHeader("   Results", 3))
    assertEquals("###### x", markdownHeader("# x", 6))
  }

  /** An empty cell becomes the marker and its space, which is what an inserted heading is too. */
  @Test
  fun anEmptySourceBecomesTheMarkerAlone() {
    assertEquals("## ", markdownHeader("", 2))
  }

  /**
   * The regex is anchored, so only the *first* line is rewritten - the rest of a cell survives
   * being given a heading.
   */
  @Test
  fun onlyTheFirstLineIsRewritten() {
    assertEquals("# One\n\n## Two", markdownHeader("### One\n\n## Two", 1))
  }

  /**
   * `setMarkdownHeader` is `changeCellType(notebook, 'markdown', { headingLevel })`
   * (`actions.tsx:2197-2214`), so it is the type change and everything that goes with it - the id
   * survives, the outputs do not, and the cell is left showing its source.
   */
  @Test
  fun changingToAHeadingIsAChangeOfTypeAndRewritesTheSource() {
    val changed = notebook.withCellsHeadingLevel(listOf("a"), level = 2)

    val cell = changed.cells.single { it.id == "a" }
    assertEquals(CellKind.MARKDOWN, cell.kind)
    assertEquals("## print(1)", cell.source)
    assertTrue("a" in changed.markdownBeingEdited)
  }

  /** The whole marked run, as `changeCellType` iterates `isSelectedOrActive`. */
  @Test
  fun aRunOfCellsAllBecomeHeadingsOfTheSameLevel() {
    val changed = notebook.withCellsHeadingLevel(listOf("h2", "b"), level = 3)

    assertEquals("### Part", changed.cells.single { it.id == "h2" }.source)
    assertEquals("### print(1)", changed.cells.single { it.id == "b" }.source)
  }

  /**
   * A cell that is **already markdown** takes the rewrite and no type change - the `else if` branch
   * (`actions.tsx:3300-3308`). Re-levelling a heading is the whole point of that branch.
   */
  @Test
  fun aHeadingCanBeGivenADifferentLevelWithoutChangingType() {
    val changed = notebook.withCellsHeadingLevel(listOf("h1"), level = 4)

    assertEquals("#### Chapter", changed.cells.single { it.id == "h1" }.source)
    assertEquals(CellKind.MARKDOWN, changed.cells.single { it.id == "h1" }.kind)
    assertEquals(4, changed.cells.single { it.id == "h1" }.headingLevel)
  }

  /**
   * `level = Math.min(Math.max(level, 1), 6)` (`actions.tsx:2208`), and markdown has six levels -
   * a seventh `#` renders as prose, so a clamp is the difference between a heading and a paragraph.
   */
  @Test
  fun theLevelIsClampedToTheSixMarkdownHas() {
    assertEquals("# x", markdownHeader("x", 0))
    assertEquals("###### x", markdownHeader("x", 9))
  }

  /**
   * A field on screen has to be *told* its text was rewritten, never left to notice - see [CellUndo].
   * Through the operation rather than the transform, because that is where the before/after diff
   * that bumps it lives, and this is the second command after Split to rewrite a surviving cell.
   */
  @Test
  fun theRewriteIsAnnouncedToTheFieldHoldingTheText() {
    val changed = notebook.copy(selectedCellId = "a").applying(CellAction.ChangeToHeading(2))

    assertEquals(1, changed.sourceRevisions["a"])
    assertEquals(setOf("a"), changed.undoable.single().rewrote)
  }

  /** Opening the picker is not an edit, so it records nothing and changes nothing. */
  @Test
  fun askingForTheLevelPickerIsNotAnOperation() {
    val asked = notebook.copy(selectedCellId = "a").applying(CellAction.ChooseHeadingLevel)

    assertEquals(notebook.copy(selectedCellId = "a"), asked)
  }

  private fun markdownHeader(source: String, level: Int): String =
    EditorUiState(cells = listOf(md("x", source)), loaded = true)
      .withCellsHeadingLevel(listOf("x"), level)
      .cells.single().source

  // --- collapse all / expand all --------------------------------------------------

  /**
   * `collapseAllHeadings` sets every heading and nothing else - it filters on
   * `getHeadingInfo(cell).isHeading` (`actions.tsx:2221-2232`), so the code cells between them are
   * hidden by *being in a section* rather than by being touched.
   */
  @Test
  fun collapseAllSetsEveryHeadingAndLeavesOnlyTheHeadingsOnScreen() {
    val collapsed = notebook.withAllHeadingsCollapsed()

    assertEquals(listOf("h1", "h2", "h1b"), collapsed.cells.filter { it.headingCollapsed }.map { it.id })
    assertEquals(setOf("a", "h2", "b", "c"), collapsed.hiddenCellIds)
  }

  /**
   * `notebook.activeCellIndex = 0` is the command's last line before its state is handled
   * (`actions.tsx:2229`), and the first cell is the one cell no collapse can reach - nothing is
   * above it to own it.
   */
  @Test
  fun collapseAllPutsTheHeadOnTheFirstCell() {
    val collapsed = notebook.copy(selectedCellId = "b", anchorCellId = "c").withAllHeadingsCollapsed()

    assertEquals("h1", collapsed.selectedCellId)
    assertNull(collapsed.anchorCellId)
  }

  @Test
  fun expandAllClearsEveryHeadingSoNothingIsHidden() {
    val expanded = notebook.withAllHeadingsCollapsed().withAllHeadingsExpanded()

    assertEquals(emptySet<String>(), expanded.hiddenCellIds)
    assertTrue(expanded.cells.none { it.headingCollapsed })
  }

  /**
   * `expandAllHeadings` does **not** touch `activeCellIndex` - only its opposite does
   * (`actions.tsx:2238-2246`), and it needs to: nothing it does can hide the head.
   */
  @Test
  fun expandAllLeavesTheHeadWhereItIs() {
    val marked = notebook.withAllHeadingsCollapsed().copy(selectedCellId = "h1b", anchorCellId = "h2")

    val expanded = marked.withAllHeadingsExpanded()

    assertEquals("h1b", expanded.selectedCellId)
    assertEquals("h2", expanded.anchorCellId)
  }

  /**
   * Returning **this** when nothing changed is load-bearing rather than an optimisation: a notebook
   * with no headings would otherwise be marked dirty and rewritten for having been looked at, which
   * is [withMarkdownRendered]'s reason as well as this one's.
   */
  @Test
  fun neitherCommandDirtiesANotebookItChangesNothingIn() {
    val prose = EditorUiState(cells = listOf(code("a"), md("p", "just prose")), loaded = true)

    assertEquals(prose, prose.withAllHeadingsCollapsed())
    assertEquals(prose, prose.withAllHeadingsExpanded())
    assertEquals(
      notebook.withAllHeadingsCollapsed(),
      notebook.withAllHeadingsCollapsed().withAllHeadingsCollapsed(),
    )
  }

  // --- a collapsed heading behaves as its section ---------------------------------

  /**
   * `Private.selectedCells` adds a collapsed heading's children to the operand
   * (`actions.tsx:3103-3130`), so what is copied, cut, duplicated or deleted is the whole section -
   * the cells nobody can see included, because that is what "this heading" means on screen.
   */
  @Test
  fun theOperandOfADestructiveCommandTakesTheHiddenSectionWithIt() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true).copy(selectedCellId = "h1")

    assertEquals(listOf("h1", "a", "h2", "b"), collapsed.operandOf(CellAction.Delete))
    assertEquals(listOf("h1", "a", "h2", "b"), collapsed.operandOf(CellAction.Copy))
    assertEquals(listOf("h1", "a", "h2", "b"), collapsed.operandOf(CellAction.Cut))
    assertEquals(listOf("h1", "a", "h2", "b"), collapsed.operandOf(CellAction.Duplicate))
  }

  /**
   * **And only those four.** `changeCellType`, `mergeCells`, `moveCells`, `clearOutputs` and every
   * collapse iterate `isSelectedOrActive` with no expansion at all - the two functions that expand
   * are `Private.selectedCells` and `deleteCells`, and nothing else calls either.
   */
  @Test
  fun everyOtherCommandMeansTheHeadingAloneEvenCollapsed() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true).copy(selectedCellId = "h1")

    assertEquals(listOf("h1"), collapsed.operandOf(CellAction.ChangeKind(CellKind.CODE)))
    assertEquals(listOf("h1"), collapsed.operandOf(CellAction.Move(CellMove.DOWN)))
    assertEquals(listOf("h1"), collapsed.operandOf(CellAction.ClearOutputs))
    assertEquals(listOf("h1"), collapsed.operandOf(CellAction.ToggleHeadingCollapse))
  }

  /** An expanded heading owns nothing extra: the section is visible and selectable on its own. */
  @Test
  fun anExpandedHeadingIsJustACell() {
    assertEquals(listOf("h1"), notebook.copy(selectedCellId = "h1").operandOf(CellAction.Delete))
  }

  /** A run already covering the section does not gain its cells twice, and stays in document order. */
  @Test
  fun aRunThatAlreadyHoldsTheSectionIsUnchanged() {
    val collapsed = notebook
      .withHeadingCollapsed("h2", collapsed = true)
      .copy(anchorCellId = "h1", selectedCellId = "h1b")

    assertEquals(listOf("h1", "a", "h2", "b", "h1b"), collapsed.operandOf(CellAction.Cut))
  }

  /** Deleting a collapsed heading takes the section with it - `deleteCells` (`actions.tsx:3340-3365`). */
  @Test
  fun deletingACollapsedHeadingDeletesItsSection() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true).copy(selectedCellId = "h1")

    val deleted = collapsed.applying(CellAction.Delete)

    assertEquals(listOf("h1b", "c"), deleted.cells.map { it.id })
  }

  /** And one undo brings the whole section back, because it was one operation. */
  @Test
  fun undoingThatDeleteBringsTheWholeSectionBack() {
    val collapsed = notebook.withHeadingCollapsed("h1", collapsed = true).copy(selectedCellId = "h1")

    val restored = collapsed.applying(CellAction.Delete).undoneCellOperation()

    assertEquals(notebook.cells.map { it.id }, restored.cells.map { it.id })
  }

  /**
   * **Paste lands after the children, not directly after the heading.** `addCells`'s `'below'`
   * branch adds `numberChildNodes` to the index (`actions.tsx:1590-1612`), so a paste under a
   * collapsed heading does not land inside a section nobody can see.
   */
  @Test
  fun pasteBelowACollapsedHeadingLandsAfterItsSection() {
    val collapsed = notebook
      .withHeadingCollapsed("h1", collapsed = true)
      .copy(selectedCellId = "h1", clipboard = listOf(Cell("clip", CellKind.CODE, "pasted")))

    val pasted = collapsed.applying(CellAction.Paste)

    // By source and not by id: a *copied* cell is given a fresh one, and only a cut code cell keeps
    // the id it had (`actions.tsx:1666-1677`). Where it landed is the claim here.
    assertEquals(4, pasted.cells.indexOfFirst { it.source == "pasted" })
    assertEquals(notebook.cells.map { it.id }, pasted.cells.filterNot { it.source == "pasted" }.map { it.id })
  }

  // --- the marked run, which must not end up on a cell nothing draws ----------------------

  /**
   * The reference never faces this: `toggleCurrentHeadingCollapse` acts on the *active* cell
   * (`actions.tsx:2440-2455`), so the only cell that matters is the heading, and it stays visible.
   * Here a heading inside a marked run can swallow the head - and a head nothing draws is one the
   * ranged runs would still compute their range from.
   */
  @Test
  fun collapsingAHeadingOverTheHeadPutsTheHeadOnTheHeading() {
    val marked = notebook.copy(anchorCellId = "h1", selectedCellId = "b")

    val collapsed = marked.withHeadingCollapsed("h1", collapsed = true)

    assertEquals("h1", collapsed.selectedCellId)
    assertNull(collapsed.anchorCellId)
  }

  /** A run whose ends are both on screen keeps them, and keeps being a run. */
  @Test
  fun collapsingAHeadingBelowTheRunLeavesItAlone() {
    val marked = notebook.copy(anchorCellId = "h1", selectedCellId = "h1b")

    val collapsed = marked.withHeadingCollapsed("h1b", collapsed = true)

    assertEquals("h1b", collapsed.selectedCellId)
    assertEquals("h1", collapsed.anchorCellId)
  }

  /**
   * `setHeadingCollapse` bails unless the cell *is* a markdown heading - *"otherwise collapsing and
   * uncollapsing already hidden stuff can cause some funny looking bugs"* (`actions.tsx:2364-2372`).
   */
  @Test
  fun aCellThatIsNotAHeadingCannotBeCollapsed() {
    assertEquals(notebook, notebook.withHeadingCollapsed("a", collapsed = true))
  }
}

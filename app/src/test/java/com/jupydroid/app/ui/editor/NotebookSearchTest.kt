package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.StreamName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `documentsearch:start` and `documentsearch:end`, and what `SearchDocumentModel` does between
 * them (`packages/documentsearch/src/searchmodel.ts`).
 */
class NotebookSearchTest {
  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)

  private val notebook = EditorUiState(
    cells = listOf(code("a", "cat"), code("b", "dog"), code("c", "cat cat")),
    selectedCellId = "a",
    loaded = true,
  )

  @Test
  fun aNotebookIsNotSearchingUntilItIsAsked() {
    assertNull(notebook.search)
  }

  /** The box opens empty: `getInitialQuery` is the browser's text selection, and touch has none. */
  @Test
  fun openingTheBarStartsWithNothingTypedAndNothingFound() {
    val opened = notebook.withSearchOpened()
    assertEquals(SearchQuery(""), opened.search?.query)
    assertEquals(emptyList<SearchHit>(), opened.search?.hits)
    assertEquals("-/-", opened.searchCounter())
  }

  /** Re-opening an open bar leaves what was typed in it, so the icon is not a reset. */
  @Test
  fun openingAnOpenBarKeepsTheQuery() {
    val typed = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))
    assertEquals(typed.search, typed.withSearchOpened().search)
  }

  @Test
  fun typingAQueryFindsEveryMatchInDocumentOrder() {
    val searching = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))
    assertEquals(listOf("a", "c", "c"), searching.search?.hits?.map { it.cellId })
    assertEquals("1/3", searching.searchCounter())
  }

  /** `startQuery` lands from `activeCellIndex`, so the count is one-based *from there*. */
  @Test
  fun theQueryLandsBesideTheSelectedCell() {
    val searching = notebook.copy(selectedCellId = "c")
      .withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))
    assertEquals("2/3", searching.searchCounter())
  }

  @Test
  fun clearingTheQueryEndsTheSearchWithoutClosingTheBar() {
    val cleared = notebook.withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))
      .withSearchQuery(SearchQuery(""))
    assertEquals(emptyList<SearchHit>(), cleared.search?.hits)
    assertEquals("-/-", cleared.searchCounter())
  }

  /** `_parsingError` is state on the model, shown beside the box - so it is state here too. */
  @Test
  fun anUnparseablePatternIsReportedAndFindsNothing() {
    val broken = notebook.withSearchOpened()
      .withSearchQuery(SearchQuery("(", useRegex = true))
    assertTrue("${broken.search?.parseError}", broken.search?.parseError?.contains("(") == true)
    assertEquals(emptyList<SearchHit>(), broken.search?.hits)
  }

  /**
   * The catch in `_updateSearch` sets `_parsingError` and **returns before `endQuery` is reached**
   * - the provider keeps the last query's highlights while the error is up
   * (`searchmodel.ts:339-369`). The workspace panel does the same: `!isInputValid` returns early,
   * leaving the last settled tree (`searchView.ts:1612-1614`). A `(` typed mid-pattern must not
   * drop every highlight it will put back one keystroke later.
   */
  @Test
  fun anUnparseablePatternKeepsTheLastQuerysHits() {
    val found = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))
    val broken = found.withSearchQuery(SearchQuery("cat(", useRegex = true))
    assertEquals(found.search?.hits, broken.search?.hits)
    assertEquals(found.search?.current, broken.search?.current)
    assertEquals("1/3", broken.searchCounter())
    assertTrue("${broken.search?.parseError}", broken.search?.parseError?.contains("cat(") == true)
  }

  @Test
  fun aQueryThatParsesClearsTheErrorBeforeIt() {
    val recovered = notebook.withSearchOpened()
      .withSearchQuery(SearchQuery("(", useRegex = true))
      .withSearchQuery(SearchQuery("(x)", useRegex = true))
    assertNull(recovered.search?.parseError)
  }

  /** `documentsearch:end` - `endQuery()` plus the view going away. */
  @Test
  fun closingTheBarEndsTheSearch() {
    val closed = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat")).withSearchClosed()
    assertNull(closed.search)
  }

  /**
   * *"`startQuery` is also used as 'restartQuery' after each text change"* - the reference's own
   * comment (`packages/notebook/src/searchprovider.ts:391-395`). A cell edited under an open
   * search re-finds, or the highlights would name characters that have moved.
   */
  @Test
  fun editingACellUnderAnOpenSearchRefindsIt() {
    val searching = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))
    val edited = searching.withSource("b", "cat cat cat").withSearchRecomputed()
    assertEquals("1/6", edited.searchCounter())
  }

  @Test
  fun recomputingWithNoSearchOpenChangesNothing() {
    assertEquals(notebook, notebook.withSearchRecomputed())
  }

  /** `highlightNext()` - one forward, and the counter follows it. */
  @Test
  fun theArrowsStepThroughTheMatches() {
    val searching = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))

    assertEquals("2/3", searching.withSearchStepped(reverse = false).searchCounter())
    assertEquals("3/3", searching.withSearchStepped(false).withSearchStepped(false).searchCounter())
  }

  /**
   * Only the arrows put a match in front of the reader: a query lands beside the head without
   * moving anything (`startQuery`'s `scroll: false`), so what it lands on is not stepped onto.
   */
  @Test
  fun onlyAStepMarksTheCurrentHitAsSteppedOnto() {
    val typed = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))
    val stepped = typed.withSearchStepped(reverse = false)

    assertFalse(typed.searchHighlightOf("a").currentStepped)
    assertTrue(stepped.searchHighlightOf("c").currentStepped)
    assertFalse(stepped.searchHighlightOf("a").currentStepped)
    assertFalse(stepped.withSearchRecomputed().searchHighlightOf("a").currentStepped)
  }

  /** `loop = true` is `highlightNext()`'s own default, so the last match is followed by the first. */
  @Test
  fun steppingWrapsInBothDirections() {
    val searching = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))
    val last = searching.withSearchStepped(false).withSearchStepped(false)

    assertEquals("1/3", last.withSearchStepped(reverse = false).searchCounter())
    assertEquals("3/3", searching.withSearchStepped(reverse = true).searchCounter())
  }

  /** Nothing found is nothing to step to, rather than an index nothing backs. */
  @Test
  fun steppingWithNothingFoundStaysNowhere() {
    val empty = notebook.withSearchOpened()

    assertEquals("-/-", empty.withSearchStepped(reverse = false).searchCounter())
  }

  @Test
  fun steppingWithNoSearchOpenChangesNothing() {
    assertEquals(notebook, notebook.withSearchStepped(reverse = false))
  }

  /**
   * `activateNewMatch` reveals the cell it lands on, and here the head moves with it - the
   * reference sets `activeCellIndex`, which is what fires `expandParent` in the first place.
   */
  @Test
  fun steppingOntoAHiddenMatchRevealsItAndSelectsIt() {
    val folded = EditorUiState(
      cells = listOf(
        code("a", "cat"),
        Cell("h", CellKind.MARKDOWN, "# Section", headingCollapsed = true),
        code("under", "cat"),
      ),
      selectedCellId = "a",
      loaded = true,
    )
    val searching = folded.withSearchOpened().withSearchQuery(SearchQuery("cat"))
    assertEquals("1/2", searching.searchCounter())

    val stepped = searching.withSearchStepped(reverse = false)

    assertEquals("2/2", stepped.searchCounter())
    assertEquals(listOf("a", "h", "under"), stepped.cellsOnScreen.map { it.id })
    assertEquals("under", stepped.selectedCellId)
  }

  /** A step onto a cell nothing was hiding leaves the document exactly as it was. */
  @Test
  fun steppingThroughAnOrdinaryNotebookNeverDirtiesIt() {
    val searching = notebook.withSearchOpened().withSearchQuery(SearchQuery("cat"))

    assertEquals(false, searching.withSearchStepped(false).dirty)
  }

  // ---- replace ----

  private fun searchingFor(text: String, replaceWith: String, vararg cells: Cell) =
    EditorUiState(cells = cells.toList(), selectedCellId = cells.first().id, loaded = true)
      .withSearchOpened()
      .withReplaceText(replaceWith)
      .withSearchQuery(SearchQuery(text))

  private fun EditorUiState.sourceOf(id: String) = cells.single { it.id == id }.source

  @Test
  fun replacingTheCurrentMatchRewritesOnlyThatMatch() {
    val replaced = searchingFor("cat", "dog", code("a", "cat and cat")).withCurrentMatchReplaced()

    assertEquals("dog and cat", replaced.sourceOf("a"))
  }

  /**
   * `for (matchIdx …) if (allMatches[matchIdx].position >= positionAfterReplacement)` - the cursor
   * goes **forward** from where the replacement ended, so replacing the first of two leaves the
   * second standing (`packages/codemirror/src/searchprovider.ts:360-380`).
   */
  @Test
  fun theCursorMovesForwardPastTheReplacement() {
    val replaced = searchingFor("cat", "dog", code("a", "cat and cat")).withCurrentMatchReplaced()

    assertEquals("1/1", replaced.searchCounter())
    assertEquals(8, replaced.search?.hits?.single()?.range?.first)
  }

  /** And replacing the **second** does not send the cursor back to the first - it loops instead. */
  @Test
  fun replacingTheLastMatchLoopsRatherThanGoingBackwards() {
    val replaced = searchingFor("cat", "dog", code("a", "cat and cat"))
      .withSearchStepped(reverse = false)
      .withCurrentMatchReplaced()

    assertEquals("cat and dog", replaced.sourceOf("a"))
    assertEquals(0, replaced.search?.hits?.single()?.range?.first)
  }

  /** A replace is a cell operation, so `Z` takes it back - see [CellUndo]'s `rewrote` rule. */
  @Test
  fun aReplaceIsUndoableAndPutsTheTextBack() {
    val replaced = searchingFor("cat", "dog", code("a", "cat and cat")).withCurrentMatchReplaced()

    assertEquals("cat and cat", replaced.undoneCellOperation().sourceOf("a"))
  }

  /** And the cell's field is told, or it would carry on showing the text from before. */
  @Test
  fun aReplaceTellsTheFieldItsTextWasRewritten() {
    val before = searchingFor("cat", "dog", code("a", "cat and cat"))
    val replaced = before.withCurrentMatchReplaced()

    assertEquals(
      (before.sourceRevisions["a"] ?: 0) + 1,
      replaced.sourceRevisions["a"],
    )
  }

  @Test
  fun replaceAllRewritesEveryMatchInEveryCell() {
    val replaced = searchingFor("cat", "dog", code("a", "cat cat"), code("b", "a cat"))
      .withAllMatchesReplaced()

    assertEquals("dog dog", replaced.sourceOf("a"))
    assertEquals("a dog", replaced.sourceOf("b"))
    assertEquals("-/-", replaced.searchCounter())
  }

  /** One command is one undoable operation, which is `CellEdits.applying`'s own boundary. */
  @Test
  fun replaceAllIsOneUndoNotOnePerCell() {
    val replaced = searchingFor("cat", "dog", code("a", "cat cat"), code("b", "a cat"))
      .withAllMatchesReplaced()

    val undone = replaced.undoneCellOperation()
    assertEquals("cat cat", undone.sourceOf("a"))
    assertEquals("a cat", undone.sourceOf("b"))
  }

  @Test
  fun replacingWithNothingFoundChangesNothing() {
    val searching = searchingFor("zzz", "dog", code("a", "cat"))

    assertEquals(searching, searching.withCurrentMatchReplaced())
    assertEquals(searching, searching.withAllMatchesReplaced())
  }

  /** `preserveCase` is the view's, so re-running the query must not reset it. */
  @Test
  fun theReplacementHalfSurvivesANewQuery() {
    val searching = searchingFor("cat", "dog", code("a", "cat"))
      .withPreserveCase(true)
      .withReplaceShown(true)
      .withSearchQuery(SearchQuery("ca"))

    assertEquals("dog", searching.search?.replaceText)
    assertEquals(true, searching.search?.preserveCase)
    assertEquals(true, searching.search?.replaceShown)
  }

  @Test
  fun preserveCaseReachesTheReplacement() {
    val replaced = searchingFor("cat", "dog", code("a", "CAT")).withPreserveCase(true)
      .withCurrentMatchReplaced()

    assertEquals("DOG", replaced.sourceOf("a"))
  }

  // ---- the two filters ----

  private fun notebookPrinting(cell: Cell, printed: String) = EditorUiState(
    cells = listOf(cell),
    selectedCellId = cell.id,
    loaded = true,
    outputs = mapOf(cell.id to listOf(CellOutput.Stream(StreamName.STDOUT, printed))),
  )

  /** `output` is **false** by default there too (`packages/notebook/src/searchprovider.ts:229-243`). */
  @Test
  fun outputsAreNotSearchedUntilTheyAreAskedFor() {
    val searching = notebookPrinting(code("a", "x = 1"), "the cat sat")
      .withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))

    assertEquals("-/-", searching.searchCounter())
  }

  @Test
  fun theOutputFilterFindsWhatACellPrinted() {
    val searching = notebookPrinting(code("a", "print('cat')"), "the cat sat")
      .withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))
      .withSearchOutputs(true)

    assertEquals("1/2", searching.searchCounter())
    // The source first, then the output - the order the reference aggregates them in.
    assertEquals(listOf(null, 0), searching.search?.hits?.map { it.outputAt })
  }

  /** A hit in an output is not in the field, so the field must not be handed it. */
  @Test
  fun anOutputHitIsDrawnByTheOutputAndNotByTheField() {
    val searching = notebookPrinting(code("a", "x = 1"), "the cat sat")
      .withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))
      .withSearchOutputs(true)

    assertEquals(CellSearchHighlight.NONE, searching.searchHighlightOf("a"))
    assertEquals(listOf(4..6), searching.outputSearchHighlightsOf("a").single().matches)
  }

  /**
   * `supportReplace: false`, and the reference's own filter text - *"not available when replace
   * options are shown"* (`:233-236`). One rule rather than a guard in each replace: with the row
   * open there are no output hits, so a replace cannot stand on one.
   */
  @Test
  fun outputsAreNotSearchedWhileAReplacementIsBeingWritten() {
    val searching = notebookPrinting(code("a", "x = 1"), "the cat sat")
      .withSearchOpened()
      .withSearchOutputs(true)
      .withSearchQuery(SearchQuery("cat"))
    assertEquals("1/1", searching.searchCounter())

    assertEquals("-/-", searching.withReplaceShown(true).withSearchRecomputed().searchCounter())
  }

  /** And the toggle is remembered, so hiding the row again brings the outputs back. */
  @Test
  fun theOutputFilterSurvivesTheReplaceRowOpeningAndClosing() {
    val searching = notebookPrinting(code("a", "x = 1"), "the cat sat")
      .withSearchOpened()
      .withSearchOutputs(true)
      .withSearchQuery(SearchQuery("cat"))
      .withReplaceShown(true)
      .withSearchRecomputed()
      .withReplaceShown(false)
      .withSearchRecomputed()

    assertEquals("1/1", searching.searchCounter())
  }

  /** `setIsActive(!filters.selection || isSelectedOrActive(cell))` (`:369-374`). */
  @Test
  fun theSelectionFilterSearchesOnlyTheMarkedRun() {
    val marked = notebook.copy(selectedCellId = "a", anchorCellId = "a")
      .withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))
    assertEquals("1/3", marked.searchCounter())

    assertEquals("1/1", marked.withSearchInSelection(true).searchCounter())
  }

  /** Off, it is the whole notebook again - the filter narrows, it does not re-anchor. */
  @Test
  fun turningTheSelectionFilterOffSearchesTheWholeNotebookAgain() {
    val marked = notebook.copy(selectedCellId = "a", anchorCellId = "a")
      .withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))
      .withSearchInSelection(true)

    assertEquals("1/3", marked.withSearchInSelection(false).searchCounter())
  }

  /** Each cell is handed only its own matches, and only it can hold the current one. */
  @Test
  fun aCellIsHandedItsOwnMatchesAndWhetherTheCurrentIsAmongThem() {
    val searching = notebook.copy(selectedCellId = "c")
      .withSearchOpened()
      .withSearchQuery(SearchQuery("cat"))
    assertEquals(CellSearchHighlight(listOf(0..2), current = null), searching.searchHighlightOf("a"))
    assertEquals(CellSearchHighlight.NONE, searching.searchHighlightOf("b"))
    assertEquals(CellSearchHighlight(listOf(0..2, 4..6), current = 0..2), searching.searchHighlightOf("c"))
  }

  @Test
  fun aCellIsHandedNothingWhileNoSearchIsOpen() {
    assertEquals(CellSearchHighlight.NONE, notebook.searchHighlightOf("a"))
  }
}

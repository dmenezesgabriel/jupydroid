package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
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

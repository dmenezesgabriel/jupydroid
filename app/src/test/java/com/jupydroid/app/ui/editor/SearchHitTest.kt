package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CompiledQuery
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.compile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `NotebookSearchProvider` (`packages/notebook/src/searchprovider.ts`), reduced to the two things
 * that survive having no widgets: the **ordering** of the hits and the **arithmetic** of the
 * counter.
 *
 * The reference keeps one provider per cell and aggregates across them - `matchesCount` sums
 * (`:141-146`) and `currentMatchIndex` adds up the counts of every cell before the current one
 * (`:118-136`). A flat list in document order is that aggregate, so the sums stop needing to
 * exist.
 */
class SearchHitTest {
  private fun notebook(vararg cells: Cell) = EditorUiState(cells = cells.toList(), loaded = true)

  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)

  private val cat = (SearchQuery("cat").compile() as CompiledQuery.Matching).regex

  /** `startQuery` maps `content.widgets` in order, so hit 0 is the first hit in the first cell. */
  @Test
  fun hitsAreInDocumentOrderAcrossCells() {
    val state = notebook(code("a", "cat cat"), code("b", "dog"), code("c", "cat"))
    assertEquals(
      listOf(SearchHit("a", 0..2), SearchHit("a", 4..6), SearchHit("c", 0..2)),
      state.searchHits(cat),
    )
  }

  /**
   * **Every cell participates, hidden or not.** `startQuery` creates a provider per widget with no
   * visibility filter at all (`:352-386`) - so a match inside a collapsed section is counted, and
   * P2i-3 is what makes reaching it reveal it.
   */
  @Test
  fun aCellHiddenUnderACollapsedHeadingStillHasHits() {
    val state = notebook(
      Cell("h", CellKind.MARKDOWN, "# Section", headingCollapsed = true),
      code("under", "cat"),
    )
    assertEquals(listOf("under"), state.searchHits(cat).map { it.cellId })
    // The premise: this notebook really is hiding that cell.
    assertEquals(listOf("h"), state.cellsOnScreen.map { it.id })
  }

  /** A markdown cell is searched by its source, and a raw cell is searched at all. */
  @Test
  fun everyCellKindIsSearched() {
    val state = notebook(
      code("a", "cat"),
      Cell("b", CellKind.MARKDOWN, "# cat"),
      Cell("c", CellKind.RAW, "cat"),
    )
    assertEquals(listOf("a", "b", "c"), state.searchHits(cat).map { it.cellId })
  }

  @Test
  fun aNotebookWithNoHitsHasNone() {
    assertEquals(emptyList<SearchHit>(), notebook(code("a", "dog")).searchHits(cat))
  }

  /** `'-/-'` when there is nothing to count (`packages/documentsearch/src/searchview.tsx:345-351`). */
  @Test
  fun theCounterReadsDashesWithNoMatches() {
    assertEquals("-/-", searchCounterText(current = null, total = 0))
  }

  /** `${currentIndex + 1}/${totalMatches}` - the index on screen is one-based. */
  @Test
  fun theCounterIsOneBased() {
    assertEquals("1/7", searchCounterText(current = 0, total = 7))
    assertEquals("7/7", searchCounterText(current = 6, total = 7))
  }

  /** `props.currentIndex === null ? '-' : …` - matches exist but none is the current one. */
  @Test
  fun theCounterReadsADashForTheCurrentWhenNoneIsCurrent() {
    assertEquals("-/7", searchCounterText(current = null, total = 7))
  }

  /** `_stepNext` with `loop = true`, which is what `highlightNext()`'s default passes. */
  @Test
  fun nextWrapsPastTheLastHit() {
    assertEquals(1, nextHit(current = 0, total = 3))
    assertEquals(0, nextHit(current = 2, total = 3))
  }

  @Test
  fun previousWrapsPastTheFirstHit() {
    assertEquals(1, previousHit(current = 2, total = 3))
    assertEquals(2, previousHit(current = 0, total = 3))
  }

  @Test
  fun steppingWithNoHitsStaysNowhere() {
    assertNull(nextHit(current = null, total = 0))
    assertNull(previousHit(current = null, total = 0))
  }

  /** `_currentProviderIndex` is seeded from `activeCellIndex` before either step runs (`:646-648`). */
  @Test
  fun steppingFromNowhereTakesTheFirstAndTheLast() {
    assertEquals(0, nextHit(current = null, total = 3))
    assertEquals(2, previousHit(current = null, total = 3))
  }

  /**
   * `startQuery` ends by highlighting from `activeCellIndex` (`:389-401`), so a search opened
   * halfway down a notebook lands on the hit under the cell being looked at, not on hit zero.
   */
  @Test
  fun aNewQueryLandsOnTheFirstHitFromTheSelectedCell() {
    val state = notebook(code("a", "cat"), code("b", "dog"), code("c", "cat"))
      .copy(selectedCellId = "b")
    assertEquals(1, state.searchHits(cat).firstHitFrom(state))
  }

  /**
   * **The selected cell's own hit is the one it lands on**, not the next cell's - `_stepNext` asks
   * the provider *at* `activeCellIndex` first, with `from: 'selection-start'` (`:389-401`,
   * `:646-660`). Its own case because the one above cannot tell the two apart: it survived a
   * mutation from `>=` to `>`, since its selected cell has no hit of its own.
   */
  @Test
  fun landingPrefersAHitInTheSelectedCellItself() {
    val state = notebook(code("a", "cat"), code("b", "cat"), code("c", "cat"))
      .copy(selectedCellId = "b")
    assertEquals(1, state.searchHits(cat).firstHitFrom(state))
  }

  /** Wraps, because the search covers the whole document and not only what is below. */
  @Test
  fun landingWrapsWhenNothingMatchesBelowTheSelectedCell() {
    val state = notebook(code("a", "cat"), code("b", "dog")).copy(selectedCellId = "b")
    assertEquals(0, state.searchHits(cat).firstHitFrom(state))
  }

  @Test
  fun landingOnANotebookWithNoHitsIsNowhere() {
    val state = notebook(code("a", "dog")).copy(selectedCellId = "a")
    assertNull(state.searchHits(cat).firstHitFrom(state))
  }
}

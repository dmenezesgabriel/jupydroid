package com.jupydroid.app.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the find bar is asking of the notebook, and what it found - `SearchDocumentModel`
 * (`packages/documentsearch/src/searchmodel.ts`) with the parts a browser needed left out.
 *
 * Per sitting and not persisted, like [EditorUiState.markdownBeingEdited]: nbformat has nowhere to
 * say a document is being searched, and the reference disposes its model with the view.
 */
data class SearchState(
  val query: SearchQuery = SearchQuery(""),
  /** Every match, in document order - see [searchHits]. Empty while the box is. */
  val hits: List<SearchHit> = emptyList(),
  /** Which hit the bar is standing on, as an index into [hits]. */
  val current: Int? = null,
  /** `_parsingError`: the pattern did not compile, and the box says so rather than throwing. */
  val parseError: String? = null,
)

/**
 * `documentsearch:start`. Re-opening an open bar keeps what is in it, because the icon is a way in
 * and not a reset.
 *
 * The box opens **empty**: `getInitialQuery` is `window.getSelection()?.toString()`
 * (`packages/notebook/src/searchprovider.ts:287-291`), which on a touch device with nothing
 * selected is the empty string the reference also gets.
 */
fun EditorUiState.withSearchOpened(): EditorUiState =
  if (search != null) this else copy(search = SearchState())

/** `documentsearch:end` - `endQuery()`, and the view goes with it. */
fun EditorUiState.withSearchClosed(): EditorUiState = if (search == null) this else copy(search = null)

/**
 * A new query typed into the box: find everything, and land beside the selected cell.
 *
 * `_updateSearch` compiles the expression and either starts the query or ends it
 * (`searchmodel.ts:334-352`), and `startQuery` finishes by highlighting from `activeCellIndex`
 * with `scroll: false` - so typing finds and counts, and **does not move the notebook**. Only the
 * arrows do that, which is `activateNewMatch`'s first line (`searchprovider.ts:604-610`).
 */
fun EditorUiState.withSearchQuery(query: SearchQuery): EditorUiState {
  if (search == null) return this
  val compiled = query.compile()
  val hits = if (compiled is CompiledQuery.Matching) searchHits(compiled.regex) else emptyList()
  return copy(
    search = SearchState(
      query = query,
      hits = hits,
      current = hits.firstHitFrom(this),
      parseError = (compiled as? CompiledQuery.Invalid)?.message,
    ),
  )
}

/**
 * Finds again over source that has changed under an open search.
 *
 * *"`startQuery` is also used as 'restartQuery' after each text change"* - the reference says so in
 * its own comment (`searchprovider.ts:391-395`), and without it the highlights would name
 * characters that have since moved.
 */
fun EditorUiState.withSearchRecomputed(): EditorUiState =
  search?.let { withSearchQuery(it.query) } ?: this

/** What the bar reads between its arrows - see [searchCounterText]. */
fun EditorUiState.searchCounter(): String =
  search?.let { searchCounterText(it.current, it.hits.size) } ?: searchCounterText(null, 0)

/**
 * The matches in one cell, and whether the current one is among them - what a field draws.
 *
 * Computed per cell rather than sliced once for the list, unlike the heading numbers beside it:
 * a heading's number depends on every heading before it, where a cell's matches depend only on
 * that cell, so there is nothing to thread and nothing quadratic to avoid.
 */
fun EditorUiState.searchHighlightOf(cellId: String): CellSearchHighlight {
  val searching = search ?: return CellSearchHighlight.NONE
  val mine = searching.hits.filter { it.cellId == cellId }
  return if (mine.isEmpty()) {
    // Its own answer rather than an empty list, so a cell with no matches compares equal to every
    // other cell with none and stays skippable through recomposition.
    CellSearchHighlight.NONE
  } else {
    CellSearchHighlight(
      matches = mine.map { it.range },
      current = searching.currentHit()?.takeIf { it.cellId == cellId }?.range,
    )
  }
}

/** The hit the arrows are on, or null when they are on none. */
private fun SearchState.currentHit(): SearchHit? = current?.let { hits.getOrNull(it) }

/**
 * The find bar, driven straight from the screen - the collaborator beside [NotebookCellSelection].
 *
 * Nothing here is written to disk, so it takes no [NotebookSaveDebounce]: a search is a fact about
 * this sitting, and the reference's own model is disposed with the view.
 *
 * ```
 * val search = NotebookSearch(uiState)
 * search.start()
 * search.queryChanged(SearchQuery("cat"))
 * ```
 */
class NotebookSearch(private val uiState: MutableStateFlow<EditorUiState>) {
  /** `documentsearch:start`. */
  fun start() = uiState.update { it.withSearchOpened() }

  /** The box was typed into. */
  fun queryChanged(query: SearchQuery) = uiState.update { it.withSearchQuery(query) }

  /** `documentsearch:end`. */
  fun end() = uiState.update { it.withSearchClosed() }
}

package com.jupydroid.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.CompiledQuery
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.SearchProgress
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.domain.model.compile
import com.jupydroid.app.domain.port.WorkspaceSearcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the search panel is showing - the box, what the walk found, and whether that is all of it.
 *
 * @property results one entry per file with a match, in the order the walk found them.
 * @property limitHit `ISearchComplete.limitHit` as the walk reported it - see [SearchProgress].
 * @property searching a walk is in flight; the panel draws progress rather than "nothing found".
 * @property parseError `_parsingError` - the pattern did not compile, and the box says so.
 */
data class WorkspaceSearchUiState(
  val query: SearchQuery = SearchQuery(""),
  val results: List<MatchedFile> = emptyList(),
  val limitHit: Boolean = false,
  val searching: Boolean = false,
  val parseError: String? = null,
)

/**
 * The workspace searched from the shell's own panel, where the find bar searches one document.
 *
 * `SearchView`'s model half (`vscode/…/search/browser/searchView.ts`), which answers **both**
 * halves here: JupyterLab registers no workspace search at all, so VS Code is the behaviour
 * reference as well as the presentation one (`docs/DECISIONS.md`, "The activity bar's order takes
 * both references").
 *
 * ```
 * WorkspaceSearchViewModel(appContainer.workspaceSearcher).queryChanged(SearchQuery("cat"))
 * ```
 *
 * `Flow.debounce` is still `@FlowPreview` at coroutines 1.10.2, and the opt-in is stated here
 * rather than suppressed: it is the operator the whole of `search.searchOnTypeDebouncePeriod`
 * rests on, and hand-rolling a timer to avoid the annotation would be the worse trade.
 */
@OptIn(FlowPreview::class)
class WorkspaceSearchViewModel(private val searcher: WorkspaceSearcher) : ViewModel() {
  private val _uiState = MutableStateFlow(WorkspaceSearchUiState())
  val uiState: StateFlow<WorkspaceSearchUiState> = _uiState.asStateFlow()

  /**
   * The box's value on its way to the walk, held back by [SEARCH_ON_TYPE_DEBOUNCE_MILLIS].
   *
   * A `SharedFlow` and not a `StateFlow`, so the same text typed twice is two requests: a
   * `StateFlow` conflates equal values, and re-submitting a query is a thing the reference lets you
   * do (`onSearchSubmit`). `DROP_OLDEST` because only the newest keystroke can be worth walking.
   */
  private val typed = MutableSharedFlow<SearchQuery>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  init {
    // **`collectLatest`, and that is the cancellation half rather than a style choice**: the walk a
    // superseded query started is dropped mid-flight, so one query's hits can never land under
    // another query's text. The reference gets the same from a `Delayer` plus a cancellation token.
    //
    // **Not `NotebookSaveDebounce`**, which is the other debounce in this app: that one holds a
    // *write* back and reports whether one is still owed on the way out. Nothing here is owed, and
    // what is needed instead - the superseded job cancelled and its partial results discarded - is
    // what `collectLatest` already is.
    viewModelScope.launch { typed.debounce(SEARCH_ON_TYPE_DEBOUNCE_MILLIS).collectLatest { walk(it) } }
  }

  /**
   * A keystroke in the box: shown at once, walked on a delay.
   *
   * The complaint about a pattern is immediate and the walk is not, which is where the reference
   * puts each of them - `isInputValid` is the input box's own validation, checked as you type,
   * while the query itself goes through `triggerQueryDelayer` (`searchView.ts:1589-1601`).
   */
  fun queryChanged(query: SearchQuery) {
    _uiState.update { it.copy(query = query, parseError = (query.compile() as? CompiledQuery.Invalid)?.message) }
    typed.tryEmit(query)
  }

  /**
   * One walk of the workspace, collected into the state as it arrives.
   *
   * Two things do not start one, and both are the reference's own: a blank box **clears** what was
   * found (`if (contentPattern.length === 0) { this.clearSearchResults(false); return; }`,
   * `searchView.ts:1631-1632`), and a pattern that does not compile leaves what was found alone
   * (`if (!isInputValid()) { return; }`, `:1612-1614`) - a regex is unparseable for most of the
   * time it is being typed, and emptying the tree under each keystroke would be worse than saying
   * nothing.
   */
  private suspend fun walk(query: SearchQuery) {
    if (query.text.isEmpty()) {
      _uiState.update { it.copy(results = emptyList(), limitHit = false, searching = false) }
      return
    }
    if (query.compile() is CompiledQuery.Invalid) return
    // `showEmptyStage()` - the tree is emptied when the walk *starts*, not as the box is typed in.
    _uiState.update { it.copy(results = emptyList(), limitHit = false, searching = true) }
    searcher.search(query, SearchScope()).collect { progress -> _uiState.update { it.after(progress) } }
  }
}

/** One more thing the walk said, folded into what is on screen. */
private fun WorkspaceSearchUiState.after(progress: SearchProgress): WorkspaceSearchUiState = when (progress) {
  is SearchProgress.Found -> copy(results = results + progress.file)
  is SearchProgress.Complete -> copy(searching = false, limitHit = progress.limitHit)
}

/**
 * `search.searchOnTypeDebouncePeriod`' registered default (`search.common.contribution.ts:29-32`).
 *
 * `search.searchOnType` itself is true by default and there is no setting screen to turn it off
 * until P6c, so typing is what runs a search here - the reference's own arrangement.
 */
internal const val SEARCH_ON_TYPE_DEBOUNCE_MILLIS = 300L

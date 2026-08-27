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
 * @property completed a walk has **finished** and [results] is its whole answer. What the panel
 *   says about a search hangs off this rather than off [results] being empty: the reference adds
 *   its messages in `onSearchComplete` (`searchView.ts:1900-1905`), so a query being typed shows
 *   the last settled answer rather than "No results found" for the length of the debounce.
 * @property parseError `_parsingError` - the pattern did not compile, and the box says so.
 * @property scope which files the walk reaches - the two glob boxes behind the `…`.
 * @property scopeShown whether those boxes are drawn - `queryDetailsExpanded`
 *   (`searchView.ts:500`), which the reference keeps in its viewlet state rather than in the DOM.
 *   Here that is this ViewModel, which is what outlives the panel being switched away from.
 * @property replaceShown whether the replace row is drawn - `.replace-container.disabled` until
 *   `onToggleReplaceButton` opens it (`searchWidget.ts:553`).
 * @property replaceText what a replace would write. Changes nothing that is *found*.
 * @property preserveCase `ReplaceInput`'s own toggle (`searchWidget.ts:570-576`), which is why it
 *   lives here and not as a fourth flag on [SearchQuery]: it is a property of the replacement, and
 *   the walk is over before it is read.
 */
data class WorkspaceSearchUiState(
  val query: SearchQuery = SearchQuery(""),
  val scope: SearchScope = SearchScope(),
  val scopeShown: Boolean = false,
  val results: List<MatchedFile> = emptyList(),
  val limitHit: Boolean = false,
  val searching: Boolean = false,
  val completed: Boolean = false,
  val parseError: String? = null,
  val replaceShown: Boolean = false,
  val replaceText: String = "",
  val preserveCase: Boolean = false,
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
   * What has been asked for, each carrying how long it is willing to wait.
   *
   * A `SharedFlow` and not a `StateFlow`, so the same text typed twice is two requests: a
   * `StateFlow` conflates equal values, and re-submitting a query is a thing the reference lets you
   * do (`onSearchSubmit`). `DROP_OLDEST` because only the newest request can be worth walking.
   */
  private val asked = MutableSharedFlow<SearchRequest>(
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
    // **The delay is the request's and not this line's**, which is `triggerQueryChange`'s own shape:
    // one path for every caller, and the caller says how long it waits (`searchView.ts:1589-1601`).
    viewModelScope.launch { asked.debounce { it.waitMillis }.collectLatest { walk(it.query, it.scope) } }
  }

  /**
   * A keystroke in the box: shown at once, walked on a delay.
   *
   * The complaint about a pattern is immediate and the walk is not, which is where the reference
   * puts each of them - `isInputValid` is the input box's own validation, checked as you type,
   * while the query itself goes through `triggerQueryDelayer` (`searchView.ts:1589-1601`).
   */
  fun queryChanged(query: SearchQuery) = ask(query, SEARCH_ON_TYPE_DEBOUNCE_MILLIS)

  /**
   * One of the three option toggles flipped: shown at once and **walked at once**.
   *
   * `onDidOptionChange` calls `triggerQueryChange()` bare (`searchView.ts:712-713`), and that
   * function's own default is `delay: 0` (`:1590`) - typing is the one caller that passes a period
   * (`:584`). The reason holds outside the reference too: a debounce exists to wait for the next
   * character, and a toggle has no next character coming.
   */
  fun optionChanged(query: SearchQuery) = ask(query, AT_ONCE)

  /**
   * A keystroke in one of the two glob boxes: shown at once, walked on the **same** delay the query
   * box uses.
   *
   * The one place the reference does not take its own zero: `onFilePatternSubmit` passes
   * `delay: searchOnTypeDebouncePeriod` (`searchView.ts:582-588`), and it is fired per keystroke by
   * `inputBox.onDidChange` (`patternInputWidget.ts:156`). A toggle is a decision already made; a
   * glob is being typed, so there is a next character to wait for.
   */
  fun scopeChanged(scope: SearchScope) {
    _uiState.update { it.copy(scope = scope) }
    ask(_uiState.value.query, SEARCH_ON_TYPE_DEBOUNCE_MILLIS)
  }

  /** The `…` - `toggleQueryDetails` (`searchView.ts:1489-1518`), without its focus half. */
  fun toggleScopeShown() = _uiState.update { it.copy(scopeShown = !it.scopeShown) }

  /** The chevron - `onToggleReplaceButton` (`searchWidget.ts:600-616`). Starts nothing walking. */
  fun toggleReplaceShown() = _uiState.update { it.copy(replaceShown = !it.replaceShown) }

  /** The replace box was typed into. What is *found* is untouched, so no walk is asked for. */
  fun replaceTextChanged(text: String) = _uiState.update { it.copy(replaceText = text) }

  /** `preserveCase` - see [WorkspaceSearchUiState.preserveCase] for why it is not a query flag. */
  fun togglePreserveCase() = _uiState.update { it.copy(preserveCase = !it.preserveCase) }

  /**
   * What all three are - the panel updated now, the walk requested for [waitMillis] from now.
   *
   * **The scope is read off the state rather than passed**, which is what makes it stand while the
   * query changes under it: `commonQuery` folds the two glob boxes into every query the view builds
   * (`queryBuilder.ts:140-160`), not into the one that changed them.
   */
  private fun ask(query: SearchQuery, waitMillis: Long) {
    _uiState.update { it.copy(query = query, parseError = (query.compile() as? CompiledQuery.Invalid)?.message) }
    asked.tryEmit(SearchRequest(query, _uiState.value.scope, waitMillis))
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
  private suspend fun walk(query: SearchQuery, scope: SearchScope) {
    if (query.text.isEmpty()) {
      _uiState.update { it.copy(results = emptyList(), limitHit = false, searching = false, completed = false) }
      return
    }
    if (query.compile() is CompiledQuery.Invalid) return
    // `showEmptyStage()` - the tree is emptied when the walk *starts*, not as the box is typed in.
    _uiState.update { it.copy(results = emptyList(), limitHit = false, searching = true, completed = false) }
    searcher.search(query, scope).collect { progress -> _uiState.update { it.after(progress) } }
  }
}

/**
 * A walk asked for, and how long it waits before it starts.
 *
 * `triggerQueryChange`'s `{ delay }` as a value rather than as an argument, because what carries it
 * here is a flow: `debounce { it.waitMillis }` is the `Delayer` the reference re-arms per call.
 */
private data class SearchRequest(val query: SearchQuery, val scope: SearchScope, val waitMillis: Long)

/** One more thing the walk said, folded into what is on screen. */
private fun WorkspaceSearchUiState.after(progress: SearchProgress): WorkspaceSearchUiState = when (progress) {
  is SearchProgress.Found -> copy(results = results + progress.file)
  is SearchProgress.Complete -> copy(searching = false, completed = true, limitHit = progress.limitHit)
}

/**
 * `search.searchOnTypeDebouncePeriod`' registered default (`search.common.contribution.ts:29-32`).
 *
 * `search.searchOnType` itself is true by default and there is no setting screen to turn it off
 * until P6c, so typing is what runs a search here - the reference's own arrangement.
 */
internal const val SEARCH_ON_TYPE_DEBOUNCE_MILLIS = 300L

/** `triggerQueryChange`'s own default (`searchView.ts:1590`) - everything but a keystroke. */
private const val AT_ONCE = 0L

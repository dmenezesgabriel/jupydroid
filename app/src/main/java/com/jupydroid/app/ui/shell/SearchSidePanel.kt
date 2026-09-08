package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.SidePanel
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.port.WorkspaceSearcher
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.editor.codeTextStyle

/**
 * `workbench.view.search` - the workspace searched, where P2i's find bar searches one document.
 *
 * The box, then whatever the last finished walk has to say, then what it found grouped by file.
 * VS Code answers for **both** halves of this panel: JupyterLab registers no workspace search at
 * all (`docs/DECISIONS.md`, "The activity bar's order takes both references").
 *
 * **The same chrome as the two panels beside it** - a `Scaffold` and a `TopAppBar`, for the reason
 * `TableOfContentsSidePanel`'s own doc gives: the bar is also what pads the panel out of the status
 * bar, and drawn as a bare `Column` its header sat on top of the clock.
 *
 * ```
 * SidePanel.SEARCH -> SearchSidePanel(searcher, replace, onOpenMatch = { f, _ -> … })
 * ```
 *
 * @param replace what a Replace All goes through - the editor's model where a document is open and
 *   the file where it is not ([WorkspaceReplace]). Built by the shell, because it needs the open
 *   document registry this panel has no business knowing about.
 * @param onOpenMatch a tap on a result. Opening is the shell's job rather than this panel's, the
 *   same way the explorer hands a path up rather than choosing an editor for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSidePanel(
  searcher: WorkspaceSearcher,
  replace: WorkspaceReplace,
  onOpenMatch: (MatchedFile, MatchedLine) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: WorkspaceSearchViewModel = viewModel { WorkspaceSearchViewModel(searcher, replace) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // Which files are folded away. Keyed on the query, so a fold set cannot outlive the results it
  // folds - `TocFolds`' own lifetime rule, and the reference rebuilds its tree per search anyway.
  var collapsed by remember(state.query.text) { mutableStateOf(emptySet<String>()) }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = chromePalette.sideBarBackground,
    topBar = { TopAppBar(title = { Text(SidePanel.SEARCH.label) }, colors = sideBarTopAppBarColors()) },
  ) { padding ->
    // **`imePadding`, and a screenshot is what asked for it.** Typing into the box raises the
    // keyboard, and the results are drawn under whatever is left - the first run of
    // `p2x-3-workspace-search` on the tablet found `notes.py`'s three matches below the fold with
    // no way to reach them, and Maestro's tap at their coordinates landed on the space bar
    // (`evidence/p2x-3-workspace-search/`). The reference has no soft keyboard to inset for; every
    // editor screen in this app already does this, and a panel whose whole point is a text field
    // over a list needs it most.
    // **One scrolling surface, box included**, which is where this departs from the reference and
    // why: `searchView` keeps its widgets fixed above a scrolling tree, but it has no soft keyboard
    // taking half the view. With the glob boxes open (P2x-3e) the fixed chrome here is about 290dp,
    // and an IME leaves less than that - measured on the tablet, with the tree composed, correct
    // and **zero pixels tall**. A reader scrolls the panel; the box comes back the same way.
    LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
      item {
        SearchBox(
          query = state.query,
          onTextChanged = { viewModel.queryChanged(state.query.copy(text = it)) },
          onOptionChanged = viewModel::optionChanged,
        )
      }
      // Between the box and `.query-details`, which is where `.replace-container` is rendered -
      // `renderReplaceInput` is called after the search input and before the query details
      // (`searchWidget.ts:414`).
      item {
        SearchReplaceRow(
          shown = state.replaceShown,
          replaceText = state.replaceText,
          preserveCase = state.preserveCase,
          onToggleShown = viewModel::toggleReplaceShown,
          onReplaceTextChanged = viewModel::replaceTextChanged,
          onTogglePreserveCase = viewModel::togglePreserveCase,
          replaceAllEnabled = state.replaceAllEnabled,
          onReplaceAll = viewModel.replaceAll::requested,
        )
      }
      // Under the box, where `.query-details` sits (`searchview.css:118-146`).
      item {
        SearchScopeBoxes(
          scope = state.scope,
          shown = state.scopeShown,
          onToggle = viewModel::toggleScopeShown,
          onScopeChanged = viewModel::scopeChanged,
        )
      }
      // Under the box, where the reference's own regex complaint goes.
      state.parseError?.let { why -> item { PanelMessage(text = why, color = MaterialTheme.colorScheme.error) } }
      // `progressService.withProgress` on the view's own location - a walk of a rootfs is slow
      // enough that a still panel would read as "nothing found" while it was still looking.
      if (state.searching) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
      // One slot, and the report wins it: `replaceAll` calls `clearMessage()` before appending its
      // own (`searchView.ts:842-845`), so a report and *"No results found"* are never both drawn.
      val report = state.report
      if (report != null) {
        item { SearchReplaceReport(report, onUndo = viewModel.replaceAll::undo) }
      } else {
        settledMessages(state)
      }
      searchResults(
        results = state.results,
        collapsed = collapsed,
        onToggleFile = { path -> collapsed = if (path in collapsed) collapsed - path else collapsed + path },
        onOpenMatch = onOpenMatch,
      )
    }
  }
  state.confirming?.let { asked ->
    ReplaceAllDialog(
      asked = asked,
      onReplace = viewModel.replaceAll::confirmed,
      onDismiss = viewModel.replaceAll::dismissed,
    )
  }
}

/**
 * What the **finished** walk has to say, above the tree - `onSearchComplete`'s messages
 * (`searchView.ts:1836-1905`).
 *
 * Nothing at all until a walk has finished, which is what [WorkspaceSearchUiState.completed] is
 * for: a box holding "cat" with the debounce still running would otherwise read *"No results
 * found"* for 300 ms of every query.
 */
private fun LazyListScope.settledMessages(state: WorkspaceSearchUiState) {
  if (!state.completed) return
  if (state.results.isEmpty()) {
    item { PanelMessage(NO_RESULTS_FOUND) }
    return
  }
  item { PanelMessage(resultCountMessage(state.results.sumOf { it.matches.size }, state.results.size)) }
  // **The cap drawn rather than silent**, which is the thing P2x-3 exists to fix: the walk stops at
  // `search.maxResults` and until now nothing said so.
  if (state.limitHit) item { PanelMessage(RESULTS_ARE_A_SUBSET) }
}

/**
 * One line above the tree - a `.message` in the search view's messages element.
 *
 * `internal` rather than private since P2x-5h, because [SearchReplaceReport] draws into the same
 * slot: the reference appends both into `this.messagesElement`, and only one of them is ever in it.
 */
@Composable
internal fun PanelMessage(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = color,
    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
  )
}

/**
 * The query box - `.monaco-inputbox` at the top of the search view (`searchWidget.ts:437-460`).
 *
 * **An `OutlinedTextField` and not a `BasicTextField`**, which reverses `FindTopBar.FindBox`' call
 * rather than contradicting it: that box lives *inside* a 64dp `TopAppBar` row where Material's
 * 56dp container does not fit. This one is in the panel's body, which is a column with room for
 * it - and the container is what makes an empty field read as somewhere to type at all.
 *
 * **The reference's placeholder is dropped, and it was a measurement that dropped it.** With
 * `search.placeHolder`'s *"Search"* in the box, `SearchSidePanelTest.thePanelNamesItself` failed
 * with *"Expected at most 1 node but found 2"*: the panel's own title is `SidePanel.SEARCH.label`,
 * which is the same word, eight dp above. VS Code has both too, but its view title is small
 * upper-case chrome at the far edge of a workbench, not the line directly over the field. Two
 * controls that mean the same thing must not be spelled the same way here - a flow closing the
 * panel asserts the box is gone, and Maestro matches whole nodes (`docs/DECISIONS.md`). The
 * magnifier takes over what the placeholder was for, and the name a screen reader hears is
 * [SEARCH_WORKSPACE_BOX] - the reference giving its box an aria-label of its own is the same split
 * (`searchWidget.ts:291`).
 */
@Composable
private fun SearchBox(query: SearchQuery, onTextChanged: (String) -> Unit, onOptionChanged: (SearchQuery) -> Unit) {
  OutlinedTextField(
    value = query.text,
    onValueChange = onTextChanged,
    singleLine = true,
    textStyle = codeTextStyle(),
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    // Inside the input at its trailing edge, which is where `.controls` sits (`findInput.ts:196`).
    trailingIcon = { SearchOptionToggles(query = query, onOptionChanged = onOptionChanged) },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    modifier = Modifier
      .fillMaxWidth()
      .semantics { contentDescription = SEARCH_WORKSPACE_BOX }
      .padding(horizontal = Spacing.md, vertical = Spacing.sm),
  )
}

/**
 * `buildResultCountMessage` (`searchView.ts:2184-2195`), all four arms - because *"1 results in 1
 * files"* is not English and the reference does not print it.
 */
internal fun resultCountMessage(results: Int, files: Int): String {
  val result = if (results == 1) "result" else "results"
  val file = if (files == 1) "file" else "files"
  return "$results $result in $files $file"
}

/**
 * `noResultsFound` (`searchView.ts:1857`), **without its second sentence**.
 *
 * The reference says *"Review your configured exclusions and check your gitignore files"* and puts
 * an *Open Settings* button beside it. This app has neither a settings screen nor gitignore
 * support - P6c is where the first arrives - so advice pointing at neither would be advice nobody
 * can act on. Named here rather than left to look like the whole string.
 */
internal const val NO_RESULTS_FOUND = "No results found"

/** `searchMaxResultsWarning` (`searchView.ts:1898`), word for word. */
internal const val RESULTS_ARE_A_SUBSET =
  "The result set only contains a subset of all matches. Be more specific in your search to " +
    "narrow down the results."

/**
 * What the box answers to - see [SearchBox] for why it is not the placeholder's string.
 *
 * Deliberately not `SidePanel.SEARCH.label`: a flow that closes the panel asserts the box is gone,
 * and two controls a tap apart must not be spelled the same way (`docs/DECISIONS.md`).
 */
internal const val SEARCH_WORKSPACE_BOX = "Search the workspace"

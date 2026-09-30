package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.replacementFor
import com.jupydroid.app.theme.Spacing

/**
 * The bar that stands in for [EditorTopBar] while the notebook is being searched -
 * `documentsearch:start`, and its `✕` is `documentsearch:end`.
 *
 * **A contextual bar rather than the reference's floating overlay**, and the width is why. The
 * reference anchors a panel at the top-right *of the document* carrying a find box, three option
 * toggles, a counter, two arrows, a filter button and a close - nine controls in one row
 * (`packages/documentsearch/src/searchview.tsx:640-760`). At 360dp that is the arithmetic
 * [SelectionTopBar] already did once and lost. So this takes the bar, exactly as the selection
 * mode does, and the notebook keeps its whole width.
 *
 * The two modes are exclusive by construction: marking a run drops out of edit mode and searching
 * is asked for from the document's own bar, so [EditorScreen] only ever has one of the three up.
 *
 * ```
 * FindTopBar(query = state.search!!.query, counter = state.searchCounter(), …)
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindTopBar(
  /** The whole search, read the way [EditorCell] reads a `Cell` - see [SearchState]. */
  search: SearchState,
  /** How many cells a marked run holds, or 0 when none is marked - names the `selection` filter. */
  markedCells: Int = 0,
  /** `${current + 1}/${total}` or `-/-` - see [searchCounterText]. */
  counter: String,
  onQueryChanged: (SearchQuery) -> Unit,
  /** `documentsearch:highlightPrevious`. */
  onPrevious: () -> Unit,
  /** `documentsearch:highlightNext`. */
  onNext: () -> Unit,
  onClose: () -> Unit,
  /** `documentsearch:startWithReplace`, and its other half - see [SearchState.replaceShown]. */
  onToggleReplaceShown: () -> Unit = {},
  onReplaceTextChanged: (String) -> Unit = {},
  onTogglePreserveCase: () -> Unit = {},
  /** `Replace` - the match the arrows are on. */
  onReplace: () -> Unit = {},
  /** `Replace All` - every match in the notebook, as one undoable operation. */
  onReplaceAll: () -> Unit = {},
  /** The `output` filter. */
  onToggleSearchOutputs: () -> Unit = {},
  /** The `selection` filter. */
  onToggleSearchInSelection: () -> Unit = {},
) {
  val box = remember { FocusRequester() }
  // Nothing found is nothing to step through - `-/-` is the counter's own way of saying it.
  val steppable = counter != searchCounterText(current = null, total = 0)
  // The box takes the caret when the bar arrives, which is what the reference's `autoFocus` on the
  // find input does (`searchview.tsx:170-180`). A find bar that had to be tapped before it could
  // be typed into would waste the one gesture that opened it.
  LaunchedEffect(Unit) { box.requestFocus() }
  // A `Column` rather than the bar alone, because `TopAppBar` is one 64dp row and the replace half
  // is a second one. The reference stacks them too - its overlay grows a row when the chevron is
  // opened (`searchview.tsx:690-700`), and the row it grows carries the same three controls.
  Column {
    TopAppBar(
      // Tinted like [SelectionTopBar], so this reads as a mode rather than as the notebook's own bar
      // with different buttons on it.
      colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ),
      navigationIcon = {
        IconButton(onClick = onClose, modifier = named(CLOSE_SEARCH)) {
          Icon(Icons.Filled.Close, contentDescription = null)
        }
      },
      title = {
        Column {
          FindBox(query = search.query, onQueryChanged = onQueryChanged, focusRequester = box)
          // Under the box, where the reference puts it too (`REGEX_ERROR_CLASS`, `searchview.tsx:669`).
          search.parseError?.let { error ->
            Text(
              text = error,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      actions = {
        // Named as well as read, because "1/3" alone tells a screen reader nothing about what is
        // being counted - and it is what a Maestro flow addresses.
        Text(
          text = counter,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier
            .padding(horizontal = Spacing.sm)
            .semantics { contentDescription = "$MATCH_COUNT $counter" },
        )
        // Greyed rather than absent while there is nothing to step through, which is the rule this
        // app already applies to every command whose two states would look identical - and here it
        // also keeps the bar's shape from changing under a thumb as the query is typed.
        IconButton(onClick = onPrevious, enabled = steppable, modifier = named(PREVIOUS_MATCH)) {
          Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
        }
        IconButton(onClick = onNext, enabled = steppable, modifier = named(NEXT_MATCH)) {
          Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        SearchOptionsMenu(
          search = search,
          markedCells = markedCells,
          onQueryChanged = onQueryChanged,
          onToggleReplaceShown = onToggleReplaceShown,
          onTogglePreserveCase = onTogglePreserveCase,
          onToggleSearchOutputs = onToggleSearchOutputs,
          onToggleSearchInSelection = onToggleSearchInSelection,
        )
      },
    )
    if (search.replaceShown) {
      ReplaceRow(
        replaceText = search.replaceText,
        canReplace = steppable,
        onReplaceTextChanged = onReplaceTextChanged,
        onReplace = onReplace,
        onReplaceAll = onReplaceAll,
      )
    }
  }
}

/**
 * The second row `documentsearch:startWithReplace` opens - a box and the two buttons the reference
 * puts beside it, *"Replace"* and *"Replace All"* (`searchview.tsx:237-272`).
 *
 * Both are dead while nothing is found, for the arrows' reason: a control whose two states look
 * identical is one this app does not draw live.
 */
@Composable
private fun ReplaceRow(
  replaceText: String,
  canReplace: Boolean,
  onReplaceTextChanged: (String) -> Unit,
  onReplace: () -> Unit,
  onReplaceAll: () -> Unit,
) {
  Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, bottom = Spacing.sm),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicTextField(
        value = replaceText,
        onValueChange = onReplaceTextChanged,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSecondaryContainer),
        modifier = Modifier.weight(1f).semantics { contentDescription = REPLACE_BOX },
        decorationBox = { field ->
          if (replaceText.isEmpty()) {
            Text(
              text = REPLACE_BOX,
              style = LocalTextStyle.current,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = PLACEHOLDER_ALPHA),
            )
          }
          field()
        },
      )
      TextButton(onClick = onReplace, enabled = canReplace) { Text(REPLACE_ONE) }
      TextButton(onClick = onReplaceAll, enabled = canReplace) { Text(REPLACE_ALL) }
    }
  }
}

/**
 * The three things the reference puts on the box as toggle buttons - Match Case, Match Whole Word
 * and Use Regular Expression (`packages/documentsearch/src/searchview.tsx:185-210`).
 *
 * **In an overflow rather than on the bar**, which is the arithmetic [SelectionTopBar] already did
 * once and lost: the bar already carries a close, a box, a counter and two arrows, and three more
 * targets at 360dp leaves the box no room to show what was typed into it. They are also the half of
 * the search surface somebody changes once and then leaves alone, which is what an overflow is for.
 *
 * Flip labels rather than check marks, this app's convention throughout its menus - Android's
 * accessibility tree keeps a `DropdownMenuItem`'s toggled state on a node that carries no label,
 * so a check mark is a state no Maestro flow can read.
 */
@Composable
private fun SearchOptionsMenu(
  search: SearchState,
  markedCells: Int,
  onQueryChanged: (SearchQuery) -> Unit,
  onToggleReplaceShown: () -> Unit,
  onTogglePreserveCase: () -> Unit,
  onToggleSearchOutputs: () -> Unit,
  onToggleSearchInSelection: () -> Unit,
) {
  val query = search.query
  var open by remember { mutableStateOf(false) }
  // Boxed with its menu, which anchors to its parent layout node - the lesson [CellHeader],
  // [NotebookBar] and [EditorTopBar] each record.
  Box {
    IconButton(onClick = { open = true }, modifier = named(SEARCH_OPTIONS)) {
      Icon(Icons.Filled.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      SearchOptionRow(if (query.caseSensitive) IGNORE_CASE else MATCH_CASE) {
        open = false
        onQueryChanged(query.copy(caseSensitive = !query.caseSensitive))
      }
      SearchOptionRow(if (query.wholeWords) MATCH_ANYWHERE else MATCH_WHOLE_WORD) {
        open = false
        onQueryChanged(query.copy(wholeWords = !query.wholeWords))
      }
      SearchOptionRow(if (query.useRegex) SEARCH_PLAIN_TEXT else USE_REGULAR_EXPRESSION) {
        open = false
        onQueryChanged(query.copy(useRegex = !query.useRegex))
      }
      // The `output` filter, and **not offered while the replace row is open** - the reference
      // declares it `supportReplace: false` and says so in the filter's own text, *"not available
      // when replace options are shown"* (`packages/notebook/src/searchprovider.ts:233-236`).
      if (!search.replaceShown) {
        SearchOptionRow(if (search.searchOutputs) SEARCH_SOURCE_ONLY else SEARCH_CELL_OUTPUTS) {
          open = false
          onToggleSearchOutputs()
        }
      }
      // The `selection` filter, offered only while there *is* a marked run - this app's rule for a
      // control whose two states would look identical, and the reference's own count in its label.
      if (markedCells > 0) {
        SearchOptionRow(if (search.searchSelection) SEARCH_WHOLE_NOTEBOOK else searchInSelection(markedCells)) {
          open = false
          onToggleSearchInSelection()
        }
      }
      SearchOptionRow(if (search.replaceShown) HIDE_REPLACE else SHOW_REPLACE) {
        open = false
        onToggleReplaceShown()
      }
      // Only while there is a replacement for it to change, which is this app's rule for a control
      // whose two states look identical - and the reference's own placement, since it lives inside
      // the replace row there (`searchview.tsx:245-255`).
      if (search.replaceShown) {
        SearchOptionRow(if (search.preserveCase) REPLACE_VERBATIM else PRESERVE_CASE) {
          open = false
          onTogglePreserveCase()
        }
      }
    }
  }
}

@Composable
private fun SearchOptionRow(label: String, onPick: () -> Unit) {
  DropdownMenuItem(text = { Text(label) }, onClick = onPick)
}

/**
 * The find box: one line, and Search rather than Enter on the keyboard.
 *
 * A [BasicTextField] rather than a `TextField`, because Material's own text field brings a label,
 * a container and 56dp of height that a bar this size has nowhere to put - the same reason the
 * cells use one.
 */
@Composable
private fun FindBox(query: SearchQuery, onQueryChanged: (SearchQuery) -> Unit, focusRequester: FocusRequester) {
  BasicTextField(
    value = query.text,
    onValueChange = { onQueryChanged(query.copy(text = it)) },
    singleLine = true,
    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSecondaryContainer),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(),
    modifier = Modifier
      .focusRequester(focusRequester)
      .semantics { contentDescription = FIND_BOX },
    decorationBox = { field ->
      if (query.text.isEmpty()) {
        Text(
          text = FIND_BOX,
          style = LocalTextStyle.current,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = PLACEHOLDER_ALPHA),
        )
      }
      field()
    },
  )
}

/**
 * `documentsearch:start`, on the notebook's own bar - named for where it searches, because "Find"
 * alone leaves a screen reader to guess in what.
 *
 * **Deliberately not [FIND_BOX]'s string**, and that is a harness rule rather than a preference: a
 * flow closing the bar asserts the box is gone, and Maestro matches whole nodes - so two controls
 * that mean the same thing must not be spelled the same way, or the assertion is answered by the
 * icon that just came back.
 */
internal const val FIND_ACTION = "Find in notebook"

/** The box itself - the reference's own placeholder and title, *"Find"* (`searchview.tsx:173,179`). */
internal const val FIND_BOX = "Find"

/** `documentsearch:end` - the reference's own *"Close Search Box"* (`searchview.tsx:748`). */
internal const val CLOSE_SEARCH = "Close search"

/** Prefixes the counter so `1/3` is something a screen reader can say. */
internal const val MATCH_COUNT = "Matches"

/**
 * `documentsearch:highlightNext`, the reference's own *"Next Match"* (`searchview.tsx:320`).
 *
 * The name goes on the `IconButton` and not on the `Icon` inside it, which is the same call
 * [EditorTopBar]'s undo pair makes and for the same measured reason: Android's accessibility tree
 * keeps `enabled` on the button, so a label on the icon lands on a child reporting `enabled=true`
 * under a disabled parent, and a flow asking for the greyed control cannot find it.
 */
internal const val NEXT_MATCH = "Next match"

/** `documentsearch:highlightPrevious` - *"Previous Match"* (`searchview.tsx:305`). */
internal const val PREVIOUS_MATCH = "Previous match"

/** The overflow the three query options live behind - see [SearchOptionsMenu]. */
internal const val SEARCH_OPTIONS = "Search options"

/**
 * *"Match Case"* (`searchview.tsx:189`), as two labels rather than one and a check mark - the
 * convention `SHOW_LINE_NUMBERS` records, and for the same measured reason.
 */
internal const val MATCH_CASE = "Match case"

/** The same option from the other side - the flags are `gim` rather than `gm`. */
internal const val IGNORE_CASE = "Ignore case"

/** *"Match Whole Word"* (`searchview.tsx:197`) - the query wrapped in `\b`. */
internal const val MATCH_WHOLE_WORD = "Match whole word"

/** The same option from the other side - see [MATCH_WHOLE_WORD]. */
internal const val MATCH_ANYWHERE = "Match anywhere"

/**
 * *"Use Regular Expression"* (`searchview.tsx:205`) - whether the box holds a pattern or the
 * characters it is made of. Off is the escaping half of `parseQuery`; see [SearchQuery].
 */
internal const val USE_REGULAR_EXPRESSION = "Use regular expression"

/** The same option from the other side - see [USE_REGULAR_EXPRESSION]. */
internal const val SEARCH_PLAIN_TEXT = "Search for plain text"

/**
 * `documentsearch:startWithReplace`, whose own control is a chevron beside the box - *"Show
 * Replace"* / *"Hide Replace"* (`searchview.tsx:694-699`). The reference's two labels, kept.
 *
 * **Adaptation:** it is a menu row rather than a chevron, because the bar has no room for a
 * seventh target at 320dp - the same arithmetic that put the three query options behind the
 * overflow. So `startWithReplace` is this bar opened and one more tap, rather than a command of
 * its own.
 */
internal const val SHOW_REPLACE = "Show replace"

/** The same control from the other side - see [SHOW_REPLACE]. */
internal const val HIDE_REPLACE = "Hide replace"

/** The replacement box - the reference's own placeholder and title, *"Replace"* (`searchview.tsx:237-241`). */
internal const val REPLACE_BOX = "Replace with"

/** *"Replace"* - the one match the arrows are standing on (`searchview.tsx:263`). */
internal const val REPLACE_ONE = "Replace"

/** *"Replace All"* (`searchview.tsx:271`). */
internal const val REPLACE_ALL = "Replace all"

/** *"Preserve Case"* (`searchview.tsx:249`) - see [replacementFor] for what it does. */
internal const val PRESERVE_CASE = "Preserve case"

/** The same option from the other side - see [PRESERVE_CASE]. */
internal const val REPLACE_VERBATIM = "Replace verbatim"

/**
 * The `output` filter - *"Search Cell Outputs"* (`packages/notebook/src/searchprovider.ts:230`).
 *
 * **Narrower than the reference's**, and stated in the label rather than hidden: this searches the
 * outputs it can also *show* a match in - stream, stderr, a traceback and `text/plain` - where the
 * reference walks its rendered DOM and so reaches a table's text too (ADR-0043).
 */
internal const val SEARCH_CELL_OUTPUTS = "Search cell outputs"

/** The same filter from the other side - see [SEARCH_CELL_OUTPUTS]. */
internal const val SEARCH_SOURCE_ONLY = "Search cell source only"

/**
 * The `selection` filter - the reference pluralises its own label off the count,
 * *"Search in %1 Selected Cell(s)"* (`searchprovider.ts:246-256`), and so does this.
 */
internal fun searchInSelection(cells: Int) =
  if (cells == 1) "Search in 1 selected cell" else "Search in $cells selected cells"

/** The same filter from the other side - see [searchInSelection]. */
internal const val SEARCH_WHOLE_NOTEBOOK = "Search the whole notebook"

/** See [NEXT_MATCH]: one node carries both the name and the enabled state. */
private fun named(what: String) = Modifier.semantics { contentDescription = what }

private const val PLACEHOLDER_ALPHA = 0.6f

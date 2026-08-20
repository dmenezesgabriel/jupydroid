package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

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
  query: SearchQuery,
  /** `${current + 1}/${total}` or `-/-` - see [searchCounterText]. */
  counter: String,
  /** `_parsingError`, or null - shown under the box rather than swallowed. */
  parseError: String?,
  onQueryChanged: (SearchQuery) -> Unit,
  /** `documentsearch:highlightPrevious`. */
  onPrevious: () -> Unit,
  /** `documentsearch:highlightNext`. */
  onNext: () -> Unit,
  onClose: () -> Unit,
) {
  val box = remember { FocusRequester() }
  // Nothing found is nothing to step through - `-/-` is the counter's own way of saying it.
  val steppable = counter != searchCounterText(current = null, total = 0)
  // The box takes the caret when the bar arrives, which is what the reference's `autoFocus` on the
  // find input does (`searchview.tsx:170-180`). A find bar that had to be tapped before it could
  // be typed into would waste the one gesture that opened it.
  LaunchedEffect(Unit) { box.requestFocus() }
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
      IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = CLOSE_SEARCH }) {
        Icon(Icons.Filled.Close, contentDescription = null)
      }
    },
    title = {
      Column {
        FindBox(query = query, onQueryChanged = onQueryChanged, focusRequester = box)
        // Under the box, where the reference puts it too (`REGEX_ERROR_CLASS`, `searchview.tsx:669`).
        if (parseError != null) {
          Text(
            text = parseError,
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
          .padding(horizontal = 8.dp)
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
    },
  )
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
 * icon that just came back (`docs/DECISIONS.md`).
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

/** See [NEXT_MATCH]: one node carries both the name and the enabled state. */
private fun named(what: String) = Modifier.semantics { contentDescription = what }

private const val PLACEHOLDER_ALPHA = 0.6f

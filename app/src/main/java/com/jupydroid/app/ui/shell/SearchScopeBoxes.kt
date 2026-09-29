package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.SearchScope
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.codeTextStyle

/**
 * Which files the walk reaches - the `.query-details` block under the query box
 * (`searchView.ts:504-580`).
 *
 * The `…` is drawn whether or not the boxes are, at the **right** of its own row, which is where
 * the reference puts it: `.query-details .more` is `position: absolute; right: -2px`
 * (`searchview.css:124-133`). The two boxes below it are `display: none` until it is tapped
 * (`:135-146`).
 *
 * ```
 * SearchScopeBoxes(state.scope, shown = state.scopeShown, onToggle = …, onScopeChanged = …)
 * ```
 *
 * @param shown `queryDetailsExpanded` - see [WorkspaceSearchUiState.scopeShown] for where it lives.
 */
@Composable
fun SearchScopeBoxes(scope: SearchScope, shown: Boolean, onToggle: () -> Unit, onScopeChanged: (SearchScope) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      IconButton(
        onClick = onToggle,
        modifier = Modifier
          .padding(end = Spacing.md)
          .semantics { contentDescription = if (shown) HIDE_SEARCH_DETAILS else SHOW_SEARCH_DETAILS },
      ) {
        Icon(Icons.Filled.MoreHoriz, contentDescription = null)
      }
    }
    if (!shown) return@Column
    // `toggleQueryDetails`'s `moveFocus` (`searchView.ts:1499-1505`): the `…` was tapped to type a
    // glob, so the cursor lands where one is typed. **Its closing half is not modelled** - the
    // reference gives focus back to the query box, and on Android that re-raises the soft keyboard
    // the user just put away by closing the details, which inverts what the tap meant.
    val includeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { includeFocus.requestFocus() }
    GlobBox(
      heading = FILES_TO_INCLUDE,
      glob = scope.include,
      placeholder = INCLUDE_EXAMPLE,
      focusRequester = includeFocus,
      onGlobChanged = { onScopeChanged(scope.copy(include = it)) },
    )
    GlobBox(
      heading = FILES_TO_EXCLUDE,
      glob = scope.exclude,
      placeholder = EXCLUDE_EXAMPLE,
      onGlobChanged = { onScopeChanged(scope.copy(exclude = it)) },
    )
  }
}

/**
 * One glob box under its heading - an `h4` over a `.monaco-inputbox` (`searchView.ts:538-546`).
 *
 * The heading is drawn *and* is what the box answers to, which is the reference's own arrangement:
 * its `ariaLabel` is the same string as the `h4` above the field (`:542`). The example goes in the
 * placeholder, where `showPlaceholderOnFocus` puts it.
 */
@Composable
private fun GlobBox(
  heading: String,
  glob: String,
  placeholder: String,
  onGlobChanged: (String) -> Unit,
  focusRequester: FocusRequester? = null,
) {
  Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
    Text(
      text = heading,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      // **Drawn, and out of the tree**, because the field below carries the same name: the reference
      // gives the input an `ariaLabel` equal to this `h4` (`searchView.ts:542`), which in a browser
      // costs nothing and here made two nodes answer to one string - and a flow tapped the label
      // (`docs/DECISIONS.md`, and `evidence/p2x-3-search-scope/README.md` for the run that found it).
      modifier = Modifier.clearAndSetSemantics {},
    )
    OutlinedTextField(
      value = glob,
      onValueChange = onGlobChanged,
      singleLine = true,
      textStyle = codeTextStyle(),
      placeholder = { Text(placeholder, style = codeTextStyle()) },
      modifier = Modifier
        .fillMaxWidth()
        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        .semantics { contentDescription = heading },
    )
  }
}

/** `searchScope.includes` (`searchView.ts:538`), which is also what the box answers to. */
internal const val FILES_TO_INCLUDE = "files to include"

/** `searchScope.excludes` (`searchView.ts:564`). */
internal const val FILES_TO_EXCLUDE = "files to exclude"

/**
 * `placeholder.includes` (`searchView.ts:543`), with the reference's TypeScript example read as
 * this app's Python - a workspace here holds `.py` and `.ipynb`, and an example naming neither
 * would be an example of nothing.
 */
private const val INCLUDE_EXAMPLE = "e.g. *.py, src/**/include"

/** `placeholder.excludes` (`searchView.ts:569`), read the same way. */
private const val EXCLUDE_EXAMPLE = "e.g. *.py, src/**/exclude"

/**
 * The `…`, named for what a tap will do rather than by `aria-expanded` - the rule every toggle in
 * this app follows, and the reference's own label is the one-string
 * *"Toggle Search Details"* only because a browser has `aria-expanded` to carry the state.
 */
internal const val SHOW_SEARCH_DETAILS = "Show search details"

/** The same control from the other side - see [SHOW_SEARCH_DETAILS]. */
internal const val HIDE_SEARCH_DETAILS = "Hide search details"

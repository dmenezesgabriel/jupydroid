package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.HIDE_REPLACE
import com.jupydroid.app.ui.editor.PRESERVE_CASE
import com.jupydroid.app.ui.editor.REPLACE_VERBATIM
import com.jupydroid.app.ui.editor.SHOW_REPLACE
import com.jupydroid.app.ui.editor.codeTextStyle

/**
 * What a replace would write - the `.replace-container` under the query box
 * (`searchWidget.ts:553-580`), disclosed by the chevron beside it.
 *
 * **The chevron gets a column of its own on this row, where the reference spans it across both
 * inputs, and that is a measurement rather than a preference.** `searchWidget.ts:434` puts the
 * button left of the search *and* replace inputs, in a workbench sidebar; here the query box is
 * 336dp and its three option toggles already leave 192dp including the magnifier - about 144dp of
 * query text, measured on the tablet (`evidence/p2x-3-search-options/`). A fourth leading column
 * takes that to ~96dp, roughly eleven characters. Disclosing the row from the start of the row it
 * discloses costs height instead, which this panel has: it is one scrolling surface already
 * ([SearchSidePanel]).
 *
 * **The box and the `…` open from opposite ends, which is the reference's own arrangement** - the
 * replace chevron is at the left of its column and `.query-details .more` is
 * `position: absolute; right: -2px` (`searchview.css:124-133`).
 *
 * ```
 * SearchReplaceRow(state.replaceShown, state.replaceText, state.preserveCase, …)
 * ```
 *
 * @param shown `.replace-container.disabled` - see [WorkspaceSearchUiState.replaceShown].
 */
@Composable
fun SearchReplaceRow(
  shown: Boolean,
  replaceText: String,
  preserveCase: Boolean,
  onToggleShown: () -> Unit,
  onReplaceTextChanged: (String) -> Unit,
  onTogglePreserveCase: () -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      onClick = onToggleShown,
      modifier = Modifier
        .padding(start = Spacing.xs)
        .semantics { contentDescription = if (shown) HIDE_REPLACE else SHOW_REPLACE },
    ) {
      // `search-hide-replace` is a right chevron and `search-show-replace` a down one
      // (`searchIcons.ts:14-15`) - the shape says which way the row is, so the label says what a
      // tap will do, which is this app's rule for every toggle (`docs/DECISIONS.md`).
      Icon(if (shown) Icons.Filled.ExpandMore else Icons.Filled.KeyboardArrowRight, contentDescription = null)
    }
    if (!shown) return@Row
    OutlinedTextField(
      value = replaceText,
      onValueChange = onReplaceTextChanged,
      singleLine = true,
      textStyle = codeTextStyle(),
      // `search.replace.placeHolder` (`searchWidget.ts:559`). **Kept, where the query box's was
      // dropped** - `SearchBox`'s doc explains that one: the panel's own title is the same word
      // eight dp above it. Nothing on this panel is called *"Replace"*, so the reference's own
      // placeholder is what tells an empty box what it is.
      placeholder = { Text(REPLACE_PLACEHOLDER, style = codeTextStyle()) },
      // Inside the input at its trailing edge, which is where `ReplaceInput` puts its one toggle
      // (`replaceInput.ts:119-124`) - the same place [SearchOptionToggles] sits on the query box.
      trailingIcon = {
        SearchOptionToggle(
          glyph = PRESERVE_CASE_GLYPH,
          on = preserveCase,
          label = if (preserveCase) REPLACE_VERBATIM else PRESERVE_CASE,
          onToggle = onTogglePreserveCase,
        )
      },
      // `weight`, not `fillMaxWidth`: the chevron has already taken its column, and the box is
      // what stretches into the rest of the row - which is `FindTopBar.ReplaceRow`'s own arrangement.
      modifier = Modifier
        .weight(1f)
        .semantics { contentDescription = REPLACE_IN_WORKSPACE_BOX }
        .padding(end = Spacing.md, bottom = Spacing.sm),
    )
  }
}

/**
 * `Codicon.preserveCase` as the letters it is drawn from - two capitals
 * (`vscode-codicons/src/icons/preserve-case.svg`, read this phase: an `A` path and a `B` path and
 * nothing else).
 *
 * Transcribed for [SearchOptionToggles]' reason - Material ships no `preserve_case` either, and the
 * reference's own icon is typographic.
 */
private const val PRESERVE_CASE_GLYPH = "AB"

/**
 * What the replace box answers to - **not** the find bar's `REPLACE_BOX`, and the collision is real
 * rather than hypothetical: at 840dp this panel and an open document are two panes of one window,
 * so a find bar with its replace row shown puts a second *"Replace with"* in the same tree. The
 * query box was named apart for the same rule and it is written out at [SEARCH_WORKSPACE_BOX].
 *
 * The chevron and the preserve-case toggle keep the find bar's names, because their counterparts
 * there live inside a closed overflow menu (`FindTopBar.SearchOptionsMenu`) - which is also why the
 * three option toggles could already be reused.
 */
internal const val REPLACE_IN_WORKSPACE_BOX = "Replace across the workspace"

/** `search.replace.placeHolder` (`searchWidget.ts:559`), word for word. */
private const val REPLACE_PLACEHOLDER = "Replace"

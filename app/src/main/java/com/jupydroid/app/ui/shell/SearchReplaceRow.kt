package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
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
import com.jupydroid.extension.api.FocusRingIconButton
import com.jupydroid.extension.api.FocusRingTextButton

/**
 * What a replace would write - the `.replace-container` under the query box
 * (`searchWidget.ts:553-580`), disclosed by the chevron beside it.
 *
 * **The chevron gets a column of its own on this row, where the reference spans it across both
 * inputs, and that is a measurement rather than a preference.** `searchWidget.ts:434` puts the
 * button left of the search *and* replace inputs, in a workbench sidebar; here the query box is
 * 336dp and its three option toggles already leave 192dp including the magnifier - about 144dp of
 * query text, measured on the tablet. A fourth leading column
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
 * **The Replace All action is a labelled button on a row of its own, where the reference draws an
 * icon inline** (`.replace-container` is an `inline-flex` and the action bar is pushed onto it after
 * the input, `searchWidget.ts:583-586`, `searchview.css:90-115`). Both halves of that departure were
 * measured rather than preferred, and the first was measured **on the device**:
 *
 * - **The glyph.** `Codicon.replaceAll` is typographic - the SVG read this phase is a `b`
 *   letterform, a curved arrow and a box of letters - and Material's only transcription of the idea
 *   is `find_replace`, a magnifier wrapped in a circular arrow. Drawn at 24dp it read as
 *   **refresh**, next to a query box whose own leading icon
 *   is the magnifier it is built from. An action that rewrites files may not be drawn as the one
 *   glyph that means "do it again, harmlessly". Where [SearchOptionToggles] answered a missing
 *   codicon by drawing its letters, the answer for an action is the control Material gives
 *   type: a `TextButton`.
 * - **The row.** A label inline would take the box the 100dp it needs, and this panel spends
 *   **height** rather than width - the same trade the chevron's own column made above and the one
 *   that made the panel one scrolling surface ([SearchSidePanel]). On its own row the box keeps its
 *   full width and the label keeps its scope, which is what the find bar's *"Replace all"* cannot
 *   be told apart from otherwise - see [REPLACE_ALL_IN_WORKSPACE].
 *
 * @param shown `.replace-container.disabled` - see [WorkspaceSearchUiState.replaceShown].
 * @param replaceAllEnabled whether a walk has found anything to replace - `searchView.ts:792`.
 */
@Composable
fun SearchReplaceRow(
  shown: Boolean,
  replaceText: String,
  preserveCase: Boolean,
  onToggleShown: () -> Unit,
  onReplaceTextChanged: (String) -> Unit,
  onTogglePreserveCase: () -> Unit,
  replaceAllEnabled: Boolean,
  onReplaceAll: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      ReplaceChevron(shown, onToggleShown)
      if (!shown) return@Column
      ReplaceBox(replaceText, preserveCase, onReplaceTextChanged, onTogglePreserveCase)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      FocusRingTextButton(
        onClick = onReplaceAll,
        enabled = replaceAllEnabled,
        // The reference's disabled label is a *tooltip*, and a phone has no hover to raise one. It
        // is what a screen reader hears instead: the button's own text stays constant so the row
        // does not reflow as a walk settles, and the reason it cannot be pressed rides beside it.
        modifier = Modifier
          .padding(end = Spacing.sm)
          .semantics { if (!replaceAllEnabled) contentDescription = REPLACE_ALL_DISABLED },
      ) {
        Text(REPLACE_ALL_IN_WORKSPACE)
      }
    }
  }
}

/** `search-hide-replace` / `search-show-replace` (`searchIcons.ts:14-15`) - see [SearchReplaceRow]. */
@Composable
private fun ReplaceChevron(shown: Boolean, onToggleShown: () -> Unit) {
  FocusRingIconButton(
    onClick = onToggleShown,
    modifier = Modifier
      .padding(start = Spacing.xs)
      .semantics { contentDescription = if (shown) HIDE_REPLACE else SHOW_REPLACE },
  ) {
    // The shape says which way the row is, so the label says what a tap will *do*, which is this
    // app's rule for every toggle.
    Icon(
      if (shown) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
    )
  }
}

/** `.replace-input` and the one toggle inside it (`replaceInput.ts:119-124`). */
@Composable
private fun RowScope.ReplaceBox(
  replaceText: String,
  preserveCase: Boolean,
  onReplaceTextChanged: (String) -> Unit,
  onTogglePreserveCase: () -> Unit,
) {
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

/**
 * What the Replace All button says - **not** the find bar's [REPLACE_ALL], and the collision this
 * avoids is one a screen reader cannot hear round.
 *
 * The reference's own label is *"Replace All"* (`searchWidget.ts:120`) and JupyterLab's, already
 * shipped on `FindTopBar`, is *"Replace all"* (`searchview.tsx:271`). At 840dp this panel and an
 * open notebook are two panes of one window, so both can be in the tree at once - two buttons a tap
 * apart, one rewriting a document and one rewriting every file in the workspace, spelled the same
 * way. The scope is what tells them apart, and it is in the label rather than only in a tooltip
 * nobody on a phone can raise. [REPLACE_IN_WORKSPACE_BOX] was named apart for the same rule.
 *
 * The dialog behind the button keeps the reference's strings verbatim, because a modal dialog is a
 * tree of its own and nothing of the find bar is in it ([ReplaceAllDialog]).
 */
internal const val REPLACE_ALL_IN_WORKSPACE = "Replace all in workspace"

/**
 * `REPLACE_ALL_DISABLED_LABEL` (`searchWidget.ts:118`) - what a screen reader hears while the button
 * cannot be pressed, where the reference raises it as a tooltip.
 *
 * **The button is drawn disabled rather than hidden**, and it is the one place this app draws a
 * control that cannot act. It earns that: the reference's own label explains *why* it cannot, which
 * is exactly what an absent control fails to say. the rule is that a row which can
 * only fail is a control that lies; a control that states its own precondition is the case that rule
 * does not cover.
 *
 * The **visible** text does not change with the state, so the row does not reflow as a walk settles.
 */
internal const val REPLACE_ALL_DISABLED = "Replace all in workspace (Submit Search to Enable)"

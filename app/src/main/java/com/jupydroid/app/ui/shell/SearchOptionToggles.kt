package com.jupydroid.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.ui.editor.IGNORE_CASE
import com.jupydroid.app.ui.editor.MATCH_ANYWHERE
import com.jupydroid.app.ui.editor.MATCH_CASE
import com.jupydroid.app.ui.editor.MATCH_WHOLE_WORD
import com.jupydroid.app.ui.editor.SEARCH_PLAIN_TEXT
import com.jupydroid.app.ui.editor.USE_REGULAR_EXPRESSION
import com.jupydroid.app.ui.editor.symbolKeyStyle

/**
 * The three toggles that change how the box is read - `showCommonFindToggles`
 * (`searchWidget.ts:454`), drawn where the reference draws them: inside the input, at its trailing
 * edge.
 *
 * The order is `findInput.ts:181-197`'s own - **case, whole word, regex** - which is also the order
 * the notebook's find bar lists them in, so one habit serves both surfaces.
 *
 * ```
 * SearchOptionToggles(state.query, onOptionChanged = viewModel::optionChanged)
 * ```
 *
 * @param onOptionChanged the whole query with one flag flipped. **Not a keystroke**: the walk it
 *   causes runs at once, which is why the ViewModel has a second door for it.
 */
@Composable
fun SearchOptionToggles(query: SearchQuery, onOptionChanged: (SearchQuery) -> Unit) {
  Row {
    SearchOptionToggle(
      glyph = CASE_SENSITIVE_GLYPH,
      on = query.caseSensitive,
      label = if (query.caseSensitive) IGNORE_CASE else MATCH_CASE,
    ) { onOptionChanged(query.copy(caseSensitive = !query.caseSensitive)) }
    SearchOptionToggle(
      glyph = WHOLE_WORD_GLYPH,
      underlined = true,
      on = query.wholeWords,
      label = if (query.wholeWords) MATCH_ANYWHERE else MATCH_WHOLE_WORD,
    ) { onOptionChanged(query.copy(wholeWords = !query.wholeWords)) }
    SearchOptionToggle(
      glyph = REGEX_GLYPH,
      on = query.useRegex,
      label = if (query.useRegex) SEARCH_PLAIN_TEXT else USE_REGULAR_EXPRESSION,
    ) { onOptionChanged(query.copy(useRegex = !query.useRegex)) }
  }
}

/**
 * One option, on or off - the reference's `Toggle`, which marks *on* with a wash rather than a mark.
 *
 * **The wash is measured, not chosen, and since P7a-1b the role *is* this id.**
 * `inputOption.activeBackground` / `activeForeground` resolve in the 2026 themes to
 * `#313233` / `#bfbfbf` dark and `#D6D6D6` / `#202020` light (`scripts/resolve_vscode_theme.py`),
 * and `secondaryContainer` / `onSecondaryContainer` now hold `#313233` / `#BBBEBF` and
 * `#D6D6D6` / `#202020` - the background exact and the foreground 4/255 off in dark and equal in
 * light.
 *
 * It used to be a *fold*: the role was `list.inactiveSelectionBackground`, borrowed here because
 * the two ids measured 4/255 apart. The fold did not survive that id's alpha - light states it
 * `#DADADA99`, which paints `#E7E7E8` and would have made this wash translucent where the
 * reference draws it opaque, and 17/255 from the id this doc cites. `docs/DECISIONS.md` holds the
 * numbers and what retired the fold.
 *
 * **And the wash is not the accessible state.** Android's tree keeps a toggled state on a node that
 * carries no label, so what says which way this is set is the label itself, flipped - the rule every
 * toggle in this app follows since the export picker (`docs/DECISIONS.md`). [label] is therefore
 * what a *tap will do*, never what is currently true.
 *
 * Shared with [SearchReplaceRow]'s preserve-case toggle rather than copied: the reference draws
 * that one with the same `Toggle` widget, inside the replace input (`replaceInput.ts:119-124`).
 */
@Composable
internal fun SearchOptionToggle(
  glyph: String,
  on: Boolean,
  label: String,
  underlined: Boolean = false,
  onToggle: () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(MINIMUM_TAP_TARGET)
      .clip(RoundedCornerShape(TOGGLE_CORNER))
      .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
      .clickable(onClick = onToggle)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = glyph,
      style = symbolKeyStyle(),
      color = if (on) {
        MaterialTheme.colorScheme.onSecondaryContainer
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      textDecoration = if (underlined) TextDecoration.Underline else null,
    )
  }
}

/**
 * `Codicon.caseSensitive` as the letters it is drawn from - a capital and a lower-case A
 * (`vscode-codicons/src/icons/case-sensitive.svg`, read this phase).
 *
 * **Transcribed rather than substituted, because Material ships none of these three.** The extended
 * icon set has no `match_case`, no `match_word` and no `regex` - checked in the artifact - and
 * reaching for `Spellcheck` or `Abc` would put a glyph on screen that means something else. The
 * reference's own icons are typographic, so type is what draws them, in [symbolKeyStyle] - the
 * style that exists for exactly this, monospace for the shape of a mark rather than because it is
 * source (`scripts/check_monospace_home.py`).
 */
private const val CASE_SENSITIVE_GLYPH = "Aa"

/** `Codicon.wholeWord` - two letters over a full-width rule, which is [TextDecoration.Underline]. */
private const val WHOLE_WORD_GLYPH = "ab"

/** `Codicon.regex` - an asterisk and a dot, the two shapes that svg is built from. */
private const val REGEX_GLYPH = ".*"

/** `Toggle`'s own rounding, at the scale a 48dp target is drawn rather than a 20px one. */
private val TOGGLE_CORNER = 8.dp

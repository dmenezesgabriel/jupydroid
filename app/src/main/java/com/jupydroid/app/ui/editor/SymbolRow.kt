package com.jupydroid.app.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * [SymbolRow]'s own handle for an external driver - `CONSOLE_INPUT_FIELD_TAG`'s own precedent.
 * A Maestro flow cannot tell this row's `Tab` key apart from a soft keyboard's *own* Tab key by
 * text alone: at least one real IME (Gboard) carries the identical accessible text "Tab"
 * (`resource-id: com.google.android.inputmethod.latin:id/key_pos_tab`, found live on the
 * physical tablet - 7), so `assertNotVisible: "Tab"` is not reliable once
 * the keyboard is up.
 */
internal const val SYMBOL_ROW_TAG = "symbol_row"

/**
 * The row of characters a Python keyboard does not have, above the soft keyboard.
 *
 * This is what edit-mode keys mean on a phone. JupyterLab and IntelliJ both hang a great deal on
 * keys a touch keyboard simply lacks - and the ones a *code* editor needs most are not letters at
 * all: `:` ends every block header, brackets and quotes come in pairs, `#` starts every comment,
 * and Tab is how a line gets its indent. On a soft keyboard each of those is two taps away behind
 * a symbols page.
 *
 * Shown only in [CellMode.EDIT], because there is nothing to type into otherwise, and it would
 * cost a row of the notebook to say so.
 *
 * `imePadding` puts it on top of the keyboard; `navigationBarsPadding` keeps it clear of the
 * gesture bar in the moment between the keyboard closing and the mode changing.
 *
 * **It is also this app's edit-mode key surface**, which is what earns the history pair a place on
 * it. `notebook:access-previous-history-entry` and its sibling are bound to `Alt ArrowUp` and
 * `Alt ArrowDown` under a `.jp-Notebook.jp-mod-editMode` selector
 * (`packages/notebook-extension/schema/tracker.json:452-462`); there is no Alt key here, and this
 * row is the only thing on screen exactly when that selector would match. They sit right after
 * `Tab`, at the near end - see the comment on their placement, which is a measurement.
 *
 * ```
 * if (state.mode == CellMode.EDIT) SymbolRow(input = focusedCellInput)
 * ```
 */
@Composable
internal fun SymbolRow(
  input: FocusedCellInput,
  modifier: Modifier = Modifier,
  /**
   * `accessKernelHistory` - off by default, as the reference ships it. The two keys are **absent**
   * rather than disabled: both actions return on their first line without the setting
   * (`packages/notebook/src/actions.tsx:2616`, `:2634`), so a key drawn here would be one that
   * lights up and does nothing.
   */
  accessKernelHistory: Boolean = false,
  onRecallPreviousEntry: () -> Unit = {},
  onRecallNextEntry: () -> Unit = {},
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag(SYMBOL_ROW_TAG),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        // Scrolls rather than wraps or shrinks: thirteen keys do not fit 320dp at a size worth
        // tapping, and a Row keeps every one in the semantics tree where a LazyRow would drop
        // the off-screen ones - which is what a screen reader and a test both walk.
        .horizontalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding(),
    ) {
      SymbolKey(label = "Tab", onClick = input::indent)
      // Second and third rather than last, and that is a measurement rather than a preference:
      // at the end of the row both were off the right edge of the editor pane on the tablet, the
      // second one entirely (before this). The row scrolls,
      // so nothing was broken - but these are two keys somebody turned a setting on to reach, and
      // putting them where every tap costs a swipe first is the row's frequency ordering read
      // backwards. Nobody who leaves the setting off pays for the position.
      if (accessKernelHistory) {
        SymbolKey(label = RECALL_PREVIOUS_KEY, describedAs = RECALL_PREVIOUS_ENTRY, onClick = onRecallPreviousEntry)
        SymbolKey(label = RECALL_NEXT_KEY, describedAs = RECALL_NEXT_ENTRY, onClick = onRecallNextEntry)
      }
      TYPED_KEYS.forEach { key -> SymbolKey(label = key, onClick = { input.type(key) }) }
      SymbolKey(label = "←", onClick = { input.moveCaret(-1) })
      SymbolKey(label = "→", onClick = { input.moveCaret(1) })
    }
  }
}

/**
 * A `TextButton` rather than a hand-rolled box: it carries Material's 48dp minimum touch target,
 * which is the one thing every key here has to have.
 *
 * [describedAs] is for the keys whose glyph is not the whole story. A typing key needs none - `:`
 * announces itself by being the character it inserts - but `Hist ↑` is an abbreviation, and both a
 * screen reader and a Maestro flow read the description rather than the label when there is one.
 */
@Composable
private fun SymbolKey(label: String, onClick: () -> Unit, describedAs: String? = null) {
  TextButton(
    onClick = onClick,
    modifier = if (describedAs == null) Modifier else Modifier.semantics { contentDescription = describedAs },
  ) {
    Text(text = label, style = symbolKeyStyle())
  }
}

/**
 * The two history keys' labels.
 *
 * **Not a bare `↑`/`↓`**, which is the shape that would have fitted the row and lied: beside `←`
 * and `→`, which move the caret one character, a lone arrow reads as moving it one *line*. The
 * word is what separates walking the document from walking the kernel's history.
 */
private const val RECALL_PREVIOUS_KEY = "Hist ↑"
private const val RECALL_NEXT_KEY = "Hist ↓"

/**
 * `notebook:access-previous-history-entry`, under the reference's own label - *"Access Previous
 * Kernel History Entry"* (`packages/notebook-extension/src/index.ts:5179`) - in this app's
 * sentence case, without the verb a command palette needs and a key does not.
 */
internal const val RECALL_PREVIOUS_ENTRY = "Previous kernel history entry"

/** `notebook:access-next-history-entry` (`:5194`) - see [RECALL_PREVIOUS_ENTRY]. */
internal const val RECALL_NEXT_ENTRY = "Next kernel history entry"

/**
 * Ordered by how often Python reaches for them, not by where a keyboard hides them: the colon
 * ends every block header, the pairs come next, then the comment hash and the underscore that
 * every snake_case name needs.
 */
private val TYPED_KEYS = listOf(":", "(", ")", "[", "]", "{", "}", "'", "\"", "=", "#", "_")

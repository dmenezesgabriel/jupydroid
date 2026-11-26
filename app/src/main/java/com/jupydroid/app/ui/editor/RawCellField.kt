package com.jupydroid.app.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.syntax.PlainText
import com.jupydroid.app.theme.editorPalette

/**
 * One raw cell's editor.
 *
 * **A raw cell has no second state to be in.** JupyterLab's `RawCell` is an editor and nothing
 * else - no renderer, no output area, no rendered/source toggle
 * (`packages/cells/src/widget.ts:2671-2694`) - which is why this is a field rather than a
 * [MarkdownCell]-shaped pair.
 *
 * **And its content is plain text**, so there is no highlighting, no bracket matching, no
 * completion and no Python typing rules. `RawCellModel` is the one cell model that sets no
 * mimeType at all, where `MarkdownCellModel` sets `text/x-ipythongfm`
 * (`packages/cells/src/model.ts:550-578`). nbconvert is what a raw cell is for, and what it holds
 * is LaTeX, reST or an HTML fragment - none of them this notebook's language.
 *
 * **No placeholder**, which is the reference's rule rather than an omission:
 * `jp-mod-emptyMarkdownPlaceholder` is markdown's alone (`packages/cells/style/widget.css:145`).
 * An empty raw cell is still a visible row, because the panel behind it and its gutter are drawn
 * whether or not there is text in them.
 *
 * **And no focus request on arrival**, which is the one thing it would have been wrong to copy
 * from [MarkdownCellField]. That field exists only because a tap asked for it, so it takes the
 * caret; a raw cell is in this state always, so requesting focus here would take the caret away
 * from whatever was being typed every time a raw cell scrolled into view. [takesCaret] is the
 * same ask the code cell's field has - a command names the cell, and only then does the caret move.
 *
 * It keeps the line-number gutter, for [MarkdownCellField]'s reason rather than the reference's
 * `raw: { lineNumbers: false }` (`packages/notebook/src/widget.ts:1349-1353`): [lineNumbers] counts
 * lines rather than code, and this app already made that departure for prose.
 *
 * ```
 * RawCellField(state = state, source = cell.source, onSourceEdited = { … })
 * ```
 */
@Composable
fun RawCellField(
  state: TextFieldState,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
  /** Null where no symbol row is on screen, which is every test that drives a field alone. */
  input: FocusedCellInput? = null,
  /** `notebook:toggle-all-cell-line-numbers`, the notebook's - see [gutterWidth]. */
  lineNumbersShown: Boolean = true,
  /** See [codeSourceStyle]. */
  codeFontSizeSp: Int = CodeFontSize.DEFAULT,
  /** What the find bar found in this cell - see [CellSearchHighlight]. */
  search: CellSearchHighlight = CellSearchHighlight.NONE,
  /** [CodeCellField]'s own pair: a command named this cell, so its field takes the caret. */
  takesCaret: Boolean = false,
  onCaretTaken: () -> Unit = {},
) {
  val caret = remember { FocusRequester() }
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val sourceStyle = codeSourceStyle(fontSize = codeFontSizeSp.sp)
  val numberStyle = sourceStyle.copy(color = editorPalette.lineNumber)
  val gutter = gutterWidth(state.text, numberStyle, lineNumbersShown)
  val searchStyles = searchMatchStyles()
  val searching = rememberUpdatedState(search)

  reportCellEdits(state, onSourceEdited)
  // The symbol row is on screen in edit mode whatever the cell is, so this field has to be
  // reachable from it - the omission that once left it inert in a markdown cell.
  input.attachWhileFocused(state, focused.value)

  // [CodeCellField]'s own ask, copied to here because `enter-edit-mode` means a raw cell too.
  LaunchedEffect(takesCaret) {
    if (!takesCaret) return@LaunchedEffect
    caret.requestFocus()
    onCaretTaken()
  }

  BasicTextField(
    state = state,
    modifier = modifier.focusRequester(caret).lineNumbers(state.text, layout, numberStyle, gutter),
    onTextLayout = { getResult -> layout = getResult() },
    textStyle = sourceStyle,
    interactionSource = interactions,
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    // The one transformation a raw cell has, and it colours nothing: [PlainText] emits no spans,
    // so this draws the find bar's matches and leaves every other character alone. A raw cell is
    // searched like any other - `CellSearchProvider` wraps every cell's editor
    // (`packages/cells/src/searchprovider.ts:30-58`) - and it is the one cell type with no
    // language to highlight (`RawCellModel` sets no mimeType at all).
    outputTransformation = remember(searchStyles) {
      HighlightTransformation(PlainText, emptyMap(), focused, null, searching, searchStyles)
    },
  )
}

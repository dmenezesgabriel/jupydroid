package com.jupydroid.app.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.syntax.PythonSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.theme.editorPalette

/**
 * One cell's code editor.
 *
 * A plain Compose text field, so IME composing regions, autocorrect, gesture typing,
 * selection handles and the magnifier are the platform's problem rather than ours. What is
 * added sits on top of it and never inside it: colour and bracket matching through
 * [OutputTransformation], which style what is drawn without touching what is stored, the
 * indent/bracket edits through [InputTransformation], and the [lineNumbers] gutter, which reads
 * the field's layout rather than the field reading it.
 *
 * **[state] is the caller's**, which is Compose's own hoisting rule: the text and the caret have
 * a second reader now - the cell's own actions, which need both to split a cell where the caret
 * is - so the state belongs at the lowest ancestor that holds them. Held here it was unreachable
 * from outside, and a field could neither be asked where its caret was nor told about a source it
 * had not typed.
 *
 * ```
 * val state = rememberTextFieldState(cell.source)
 * CodeCellField(state = state, onSourceEdited = { … })
 * ```
 */
@Composable
fun CodeCellField(
  state: TextFieldState,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
  completionsAt: suspend (code: String, cursorOffset: Int) -> CompletionResult = NO_COMPLETIONS,
  highlighter: SyntaxHighlighter = remember { PythonSyntax() },
  /** `notebook:toggle-autoclosing-brackets`, universal - see [PythonTypingRules]. */
  autoClosingBrackets: Boolean = true,
  typing: InputTransformation = remember(autoClosingBrackets) { PythonTypingRules(autoClosingBrackets) },
  /** Null where no symbol row is on screen, which is every test that drives a field alone. */
  input: FocusedCellInput? = null,
  /** `notebook:toggle-all-cell-line-numbers`, the notebook's - see [gutterWidth]. */
  lineNumbersShown: Boolean = true,
  /** `docs/PLAN.md` P6c-4 - see [codeSourceStyle]. */
  codeFontSizeSp: Int = CodeFontSize.DEFAULT,
  /** `docs/PLAN.md` P6c-5 - false scrolls the code horizontally instead of wrapping it. */
  wrapEnabled: Boolean = true,
  /** Breakpoints set on this cell, by line - see [NotebookDebugger]. Code cells only. */
  breakpoints: List<Breakpoint> = emptyList(),
  /** A tap in the gutter, reporting the one-based line it hit - see [Modifier.lineNumbers]. */
  onGutterTap: (line: Int) -> Unit = {},
  /** A long-press on an armed line's gutter (P6a-16) - see [Modifier.lineNumbers]. */
  onGutterLongPress: (line: Int) -> Unit = {},
  /** Where the kernel is paused in this cell, one-based, or null - see [Modifier.lineNumbers]. */
  stoppedLine: Int? = null,
  /** What the find bar found in this cell - see [CellSearchHighlight]. */
  search: CellSearchHighlight = CellSearchHighlight.NONE,
  /**
   * How the text came out, for a caller that has to put a *place in it* on screen.
   *
   * The field owns this - it is what `BasicTextField` measured - and a caller that guessed instead
   * would be guessing wrong the moment a line wraps. `FileEditorScreen` reads it to scroll a search
   * match into view (P2x-3f); everything else takes the default and never hears about layout.
   */
  onLayoutMeasured: (TextLayoutResult) -> Unit = {},
) {
  val styles = syntaxStyles()
  val matched = bracketMatchStyle()
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val codeStyle = codeSourceStyle(fontSize = codeFontSizeSp.sp)
  val numberStyle = codeStyle.copy(color = editorPalette.lineNumber)
  val gutter = gutterWidth(state.text, numberStyle, lineNumbersShown)
  val completions by rememberCellCompletions(state, focused, completionsAt)
  val searchStyles = searchMatchStyles()
  val searching = rememberUpdatedState(search)
  // The code a popup was already answered for, so accepting or dismissing one does not
  // immediately re-offer against the very text it just produced.
  var settled by remember { mutableStateOf<String?>(null) }
  val scroll = remember { ScrollState(0) }

  reportCellEdits(state, onSourceEdited)

  input.attachWhileFocused(state, focused.value)

  Box {
    BasicTextField(
      state = state,
      // The gutter's width comes from the line *count*, so it never depends on the layout this
      // callback publishes - which is what keeps writing state from here out of a measure loop.
      // Wrapping off reaches the gutter through GutterOverlay below rather than through this
      // chain - `docs/PLAN.md` P6c-5's own finding that a gutter scrolled inside the same
      // modifiers as the field it decorates scrolls away with it (`evidence/p6c-5-word-wrap/`).
      modifier = if (wrapEnabled) {
        modifier.lineNumbers(
          state.text,
          layout,
          numberStyle,
          gutter,
          breakpoints,
          onGutterTap,
          onGutterLongPress,
          stoppedLine,
        )
      } else {
        modifier.horizontalScroll(scroll).padding(start = gutter)
      },
      onTextLayout = { getResult -> layout = getResult()?.also(onLayoutMeasured) },
      textStyle = codeStyle,
      interactionSource = interactions,
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      inputTransformation = typing,
      // The highlight is read through a `State` rather than closed over, so a search moving does
      // not rebuild the transformation - see [HighlightTransformation].
      outputTransformation = remember(highlighter, styles, matched, searchStyles) {
        HighlightTransformation(highlighter, styles, focused, matched, searching, searchStyles)
      },
    )
    if (!wrapEnabled) {
      GutterOverlay(
        text = state.text,
        layout = layout,
        style = numberStyle,
        width = gutter,
        breakpoints = breakpoints,
        onGutterTap = onGutterTap,
        onGutterLongPress = onGutterLongPress,
        stoppedLine = stoppedLine,
      )
    }
    val offered = completions?.takeIf { it.query.code != settled }
    if (offered != null && layout != null) {
      CompletionPopup(
        completions = offered,
        caret = caretRect(layout, offered.query.cursorOffset, gutter, scroll.value),
        onPick = { match ->
          applyCompletion(state, offered, match)
          settled = state.text.toString()
        },
        onDismiss = { settled = offered.query.code },
      )
    }
  }
}

/**
 * Where the text cursor is within this field, gutter included.
 *
 * The whole rect rather than just its bottom edge: the popup needs the *top* too, so it can sit
 * above the caret when there is no room below without covering the character being completed.
 *
 * [TextLayoutResult.getCursorRect] answers in the text's own coordinates, which start at the
 * gutter's right edge rather than at the cell's left - and, once [scrollOffsetPx] is nonzero
 * (`docs/PLAN.md` P6c-5, `wrapEnabled = false`), further right than where the code is actually
 * drawn on screen.
 */
@Composable
private fun caretRect(layout: TextLayoutResult?, cursorOffset: Int, gutter: Dp, scrollOffsetPx: Int = 0): IntRect {
  val gutterPx = with(LocalDensity.current) { gutter.roundToPx() }
  val rect = layout?.getCursorRect(cursorOffset.coerceIn(0, layout.layoutInput.text.length))
    ?: return IntRect(gutterPx, 0, gutterPx, 0).shiftedBy(scrollOffsetPx)
  return IntRect(
    left = gutterPx + rect.left.toInt(),
    top = rect.top.toInt(),
    right = gutterPx + rect.right.toInt(),
    bottom = rect.bottom.toInt(),
  ).shiftedBy(scrollOffsetPx)
}

/** This rect translated [dxPx] left - the caret's own drift once its field has scrolled that far. */
internal fun IntRect.shiftedBy(dxPx: Int): IntRect = IntRect(left - dxPx, top, right - dxPx, bottom)

/** A cell with no kernel behind it still edits; it just has nothing to suggest. */
private val NO_COMPLETIONS: suspend (String, Int) -> CompletionResult = { _, _ -> CompletionResult.NONE }

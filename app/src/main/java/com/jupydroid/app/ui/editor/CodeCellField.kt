package com.jupydroid.app.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.syntax.PythonSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.syntax.SyntaxKind
import com.jupydroid.app.syntax.bracketPairAround
import com.jupydroid.app.theme.editorPalette
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

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
  /** The notebook's copy, which [sourceRevision] decides whether to take. */
  source: String = state.text.toString(),
  /** See [trackCellSource]: bumped when a cell operation rewrote this cell's text. */
  sourceRevision: Int = 0,
  completionsAt: suspend (code: String, cursorOffset: Int) -> CompletionResult = NO_COMPLETIONS,
  highlighter: SyntaxHighlighter = remember { PythonSyntax() },
  typing: InputTransformation = remember { PythonTypingRules() },
  /** Null where no symbol row is on screen, which is every test that drives a field alone. */
  input: FocusedCellInput? = null,
) {
  val styles = syntaxStyles()
  val matched = bracketMatchStyle()
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val palette = editorPalette
  val codeStyle = LocalTextStyle.current.copy(
    fontFamily = FontFamily.Monospace,
    // The editor's `TEXT` foreground, so text the highlighter leaves alone is the colour
    // IntelliJ leaves it - and so the palette's contrast assertions describe what is drawn.
    color = palette.syntax.plain,
  )
  val numberStyle = codeStyle.copy(color = palette.lineNumber)
  val gutter = gutterWidth(state.text, numberStyle)
  val completions by rememberCellCompletions(state, focused, completionsAt)
  // The code a popup was already answered for, so accepting or dismissing one does not
  // immediately re-offer against the very text it just produced.
  var settled by remember { mutableStateOf<String?>(null) }

  trackCellSource(state, source, sourceRevision, onSourceEdited)

  input.attachWhileFocused(state, focused.value)

  Box {
    BasicTextField(
      state = state,
      // The gutter's width comes from the line *count*, so it never depends on the layout this
      // callback publishes - which is what keeps writing state from here out of a measure loop.
      modifier = modifier.lineNumbers(state.text, layout, numberStyle, gutter),
      onTextLayout = { getResult -> layout = getResult() },
      textStyle = codeStyle,
      interactionSource = interactions,
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      inputTransformation = typing,
      outputTransformation = remember(highlighter, styles, matched) {
        HighlightTransformation(highlighter, styles, focused, matched)
      },
    )
    val offered = completions?.takeIf { it.query.code != settled }
    if (offered != null && layout != null) {
      CompletionPopup(
        completions = offered,
        caret = caretRect(layout, offered.query.cursorOffset, gutter),
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
 * gutter's right edge rather than at the cell's left.
 */
@Composable
private fun caretRect(layout: TextLayoutResult?, cursorOffset: Int, gutter: Dp): IntRect {
  val gutterPx = with(LocalDensity.current) { gutter.roundToPx() }
  val rect = layout?.getCursorRect(cursorOffset.coerceIn(0, layout.layoutInput.text.length))
    ?: return IntRect(gutterPx, 0, gutterPx, 0)
  return IntRect(
    left = gutterPx + rect.left.toInt(),
    top = rect.top.toInt(),
    right = gutterPx + rect.right.toInt(),
    bottom = rect.bottom.toInt(),
  )
}

/**
 * Keeps a field and the notebook in step, in **both** directions.
 *
 * *Out:* an edit is reported debounced, because a highlighter runs on every keystroke anyway and
 * the ViewModel does not also need to hear about each one. `drop(1)` skips the initial value,
 * which would otherwise mark an untouched notebook dirty the moment it opened.
 *
 * *In:* when [revision] changes, a cell operation rewrote this cell's text under it - split, or
 * the undo of one - and [source] is taken. **A counter and never a comparison:** the model is
 * routinely one debounce behind whatever was just typed, so a field that is merely ahead looks
 * exactly like one that is behind, and adopting on the difference would eat keystrokes.
 *
 * **Both directions share one effect, and that is the point rather than tidiness.** A report is
 * held for 150ms before it is sent; a rewrite that arrived in that window would be followed by the
 * *pre-rewrite* text landing on the notebook and undoing it. Restarting the effect cancels that
 * pending report, and the `drop(1)` then swallows the adopted value instead of echoing it back.
 *
 * Shared with [MarkdownCellField], which has no revisions of its own yet: the two fields have
 * nothing else in common, but when a field and a notebook disagree is a property of the notebook,
 * not of the language.
 */
@Composable
internal fun trackCellSource(
  state: TextFieldState,
  source: String,
  revision: Int,
  onSourceEdited: (String) -> Unit,
) {
  val report by rememberUpdatedState(onSourceEdited)
  // Seeded with the revision this field arrived on, so a cell scrolled back into view does not
  // re-adopt - and so the first composition, where the state was seeded from `source` anyway,
  // adopts nothing.
  var adopted by rememberSaveable(state) { mutableIntStateOf(revision) }
  LaunchedEffect(state, revision) {
    if (revision != adopted) {
      adopted = revision
      // The caret to the start of what arrived: for a split that is the split point, since the
      // surviving fragment begins exactly where the caret was.
      state.edit {
        replace(0, length, source)
        selection = TextRange(0)
      }
    }
    snapshotFlow { state.text.toString() }
      .drop(1)
      .debounce(EDIT_REPORT_MILLIS)
      .collect(report)
  }
  // A debounced edit is lost if the field leaves before the delay elapses, and the markdown
  // cell's rendered/editing toggle makes exactly that reachable: type, tap Run, lose the last
  // keystrokes. Reported straight away on the way out instead.
  DisposableEffect(state) {
    val initial = state.text.toString()
    onDispose {
      val last = state.text.toString()
      if (last != initial) report(last)
    }
  }
}

private const val EDIT_REPORT_MILLIS = 150L

/** A cell with no kernel behind it still edits; it just has nothing to suggest. */
private val NO_COMPLETIONS: suspend (String, Int) -> CompletionResult = { _, _ -> CompletionResult.NONE }

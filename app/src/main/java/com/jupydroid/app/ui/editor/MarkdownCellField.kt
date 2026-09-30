package com.jupydroid.app.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.syntax.MarkdownSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.theme.editorPalette

/** What an empty markdown cell says, so it is never a row with nothing in it. */
internal const val MARKDOWN_PLACEHOLDER = "Write markdown…"

/**
 * One markdown cell's editor.
 *
 * Prose, not code: no bracket matching, no Python auto-indent and no kernel completions - every
 * one of those is meaningless here, which is why this is its own field rather than
 * [CodeCellField] with the language parts switched off.
 *
 * It does get a line-number gutter, because [lineNumbers] counts lines rather than code, and a
 * line number is as useful for finding your place in prose as in Python.
 *
 * **[state] is the caller's**, for the reason [CodeCellField]'s is: a cell has one source however
 * it is being shown, so the cell owns the text and the caret and hands the same state to whichever
 * field is drawing it. That is what lets a markdown cell be split, and it is why the caret survives
 * a cell being rendered and opened again.
 *
 * ```
 * MarkdownCellField(state = state, source = cell.source, onSourceEdited = { … })
 * ```
 */
@Composable
fun MarkdownCellField(
  state: TextFieldState,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
  highlighter: SyntaxHighlighter = remember { MarkdownSyntax() },
  /** Null where no symbol row is on screen, which is every test that drives a field alone. */
  input: FocusedCellInput? = null,
  /**
   * Whether this editor was asked for, rather than merely composed - see
   * [EditorUiState.markdownTakingCaret]. Defaults to false, so a field that nobody asked for
   * leaves the caret where it is.
   */
  takesCaret: Boolean = false,
  /** `notebook:toggle-all-cell-line-numbers`, the notebook's - see [gutterWidth]. */
  lineNumbersShown: Boolean = true,
  /** See [codeSourceStyle]. */
  codeFontSizeSp: Int = CodeFontSize.DEFAULT,
  /** What the find bar found in this cell - see [CellSearchHighlight]. */
  search: CellSearchHighlight = CellSearchHighlight.NONE,
  /** Whether this is the cell the caret is in - see [cellIsBeingEdited] and [cellEditorBox]. */
  editing: Boolean = false,
) {
  val styles = syntaxStyles()
  val caret = remember { FocusRequester() }
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()
  val empty by remember { derivedStateOf { state.text.isEmpty() } }
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val sourceStyle = codeSourceStyle(fontSize = codeFontSizeSp.sp)
  val numberStyle = sourceStyle.copy(color = editorPalette.lineNumber)
  val gutter = gutterWidth(state.text, numberStyle, lineNumbersShown)
  val searchStyles = searchMatchStyles()
  val searching = rememberUpdatedState(search)

  reportCellEdits(state, onSourceEdited)
  // The row is on screen in edit mode whatever the cell is, so a markdown cell has to be reachable
  // from it. Without this it sat above the keyboard doing nothing.
  input.attachWhileFocused(state, focused.value)
  // A tap on the rendered cell takes the caret with it: without this the tap left an unfocused
  // field and the next keystroke went nowhere - a tap that opens an editor you must tap again is
  // half a tap. Invisible to the Compose tests, whose `performTextInput` focuses the node itself;
  // `a1-markdown` caught it.
  //
  // **Conditional since P2e-3**, which gave this field a second way to appear. `changeCellType`
  // unrenders every cell it visits (`actions.tsx:3308-3312`) and ends in command mode, so a run
  // changed to markdown composed several of these at once - each asking for the caret, one of them
  // winning it, and the soft keyboard up over a notebook nobody was typing in.
  LaunchedEffect(takesCaret) { if (takesCaret) caret.requestFocus() }

  BasicTextField(
    state = state,
    // The panel is the editor's, not the cell's: it is what says "you are typing in this", the
    // same way a code cell reads. Rendered prose sitting in a box would be wrong, so this is
    // here rather than around MarkdownCell.
    //
    // Since P2m-2 it is literally the same panel: this wrote its own out of
    // `colorScheme.surfaceVariant` while the code and raw fields used `cellBackground` - one role,
    // two colours, and nothing that would have caught the divergence.
    modifier = modifier
      .cellEditorBox(editing)
      .focusRequester(caret)
      .lineNumbers(state.text, layout, numberStyle, gutter),
    onTextLayout = { getResult -> layout = getResult() },
    textStyle = sourceStyle,
    interactionSource = interactions,
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    // No bracket matching: a parenthesis in prose is punctuation, not structure.
    outputTransformation = remember(highlighter, styles, searchStyles) {
      HighlightTransformation(highlighter, styles, focused, null, searching, searchStyles)
    },
    decorator = { field ->
      // Dimmed and italic, the way JupyterLab marks an empty markdown cell
      // (packages/cells/style/widget.css, jp-mod-emptyMarkdownPlaceholder).
      if (empty) {
        Text(
          text = MARKDOWN_PLACEHOLDER,
          modifier = Modifier.padding(start = gutter),
          style = MaterialTheme.typography.bodyMedium,
          fontStyle = FontStyle.Italic,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      field()
    },
  )
}

package com.jupydroid.app.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.CodeFontSize
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import com.jupydroid.app.syntax.PythonSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.theme.editorPalette
import kotlinx.coroutines.delay
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
  completionsAt: suspend (code: String, cursorOffset: Int) -> CompletionResult = NO_COMPLETIONS,
  /**
   * The "Symbol info" ask - `textDocument/hover` at the caret. Unlike [completionsAt]
   * it is never invoked by typing: [symbolAsk] is the only trigger, which is the menu row's job.
   */
  hoverAt: suspend (code: String, cursorOffset: Int) -> LanguageHover? = NO_HOVER,
  /**
   * The signature-help ask - `textDocument/signatureHelp` at the caret. Like
   * [completionsAt] it is driven by typing: a `(` or `,` inside a call asks, and the card
   * stays while the call stays open.
   */
  signatureHelpAt: suspend (code: String, cursorOffset: Int) -> LanguageSignatureHelp? = NO_SIGNATURE_HELP,
  /**
   * The "Symbol info" request count - bumped by the surface that owns the menu, so one ask is
   * one number rather than a boolean a second tap could not flip back. `0` is "never asked".
   */
  symbolAsk: Int = 0,
  highlighter: SyntaxHighlighter = remember { PythonSyntax() },
  /** `notebook:toggle-autoclosing-brackets`, universal - see [PythonTypingRules]. */
  autoClosingBrackets: Boolean = true,
  /** `indentUnit` - the level a `:` line adds - see [PythonTypingRules]. */
  indentUnit: IndentUnit = IndentUnit.SPACES_4,
  typing: InputTransformation = remember(autoClosingBrackets, indentUnit) {
    PythonTypingRules(autoClosingBrackets, indentUnit)
  },
  /** Null where no symbol row is on screen, which is every test that drives a field alone. */
  input: FocusedCellInput? = null,
  /** `notebook:toggle-all-cell-line-numbers`, the notebook's - see [gutterWidth]. */
  lineNumbersShown: Boolean = true,
  /** See [codeSourceStyle]. */
  codeFontSizeSp: Int = CodeFontSize.DEFAULT,
  /** False scrolls the code horizontally instead of wrapping it. */
  wrapEnabled: Boolean = true,
  /** False never asks the kernel for completions. */
  completionEnabled: Boolean = true,
  /** Breakpoints set on this cell, by line - see [NotebookDebugger]. Code cells only. */
  breakpoints: List<Breakpoint> = emptyList(),
  /** The language server's diagnostics on this cell, by line - see [NotebookDiagnostics]. */
  diagnostics: List<CellDiagnostic> = emptyList(),
  /** A tap in the gutter, reporting the one-based line it hit - see [Modifier.lineNumbers]. */
  onGutterTap: (line: Int) -> Unit = {},
  /** A long-press on an armed line's gutter - see [Modifier.lineNumbers]. */
  onGutterLongPress: (line: Int) -> Unit = {},
  /** Where the kernel is paused in this cell, one-based, or null - see [Modifier.lineNumbers]. */
  stoppedLine: Int? = null,
  /** What the find bar found in this cell - see [CellSearchHighlight]. */
  search: CellSearchHighlight = CellSearchHighlight.NONE,
  /**
   * A definition jump waiting to be taken, or null - `EditorUiState.caretLanding` filtered to
   * this cell. VS Code's `_openReference` (`goToCommands.ts`): the caret takes the range's
   * start, the field takes focus, and the target range flashes for a beat (`symbolHighlight`,
   * 350 ms), so the jumped-to name is picked out of the line it landed in.
   */
  caretLanding: DefinitionTarget? = null,
  /** Told once the landing was applied - clears the state's one-shot. */
  onCaretLanded: () -> Unit = {},
  /**
   * `true` when the notebook asked this cell's field to take the caret - `EditorUiState.cellTakingCaret`
   * filtered to this cell. The run chords' way of reaching `mode = 'edit'`: `runAndAdvance` and
   * `runAndInsert` set it on the cell they made, and the field taking focus is what edits it
   * (`packages/notebook/src/actions.tsx:839-848`, `:913-918` - ours through the caret, theirs
   * through the flag).
   */
  takesCaret: Boolean = false,
  /** Told once the ask was taken - clears the state's one-shot, so a recomposition cannot re-yank it. */
  onCaretTaken: () -> Unit = {},
  /**
   * How the text came out, for a caller that has to put a *place in it* on screen.
   *
   * The field owns this - it is what `BasicTextField` measured - and a caller that guessed instead
   * would be guessing wrong the moment a line wraps. `FileEditorScreen` reads it to scroll a search
   * match into view; everything else takes the default and never hears about layout.
   */
  onLayoutMeasured: (TextLayoutResult) -> Unit = {},
  /**
   * What a screen reader calls this field. It sits on the node that takes the text, so one string is
   * both what is spoken and what a flow addresses; a caller that knows what the field edits says so
   * (ADR-0143).
   */
  fieldName: String = CODE_EDITOR_NAME,
) {
  val styles = syntaxStyles()
  val matched = bracketMatchStyle()
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val codeStyle = codeSourceStyle(fontSize = codeFontSizeSp.sp)
  val numberStyle = codeStyle.copy(color = editorPalette.lineNumber)
  val gutter = gutterWidth(state.text, numberStyle, lineNumbersShown)
  val completions by rememberCellCompletions(state, focused, completionsAt, enabled = completionEnabled)
  val signatureHelp by rememberCellSignatureHelp(state, focused, highlighter, signatureHelpAt)
  val searchStyles = searchMatchStyles()
  val searching = rememberUpdatedState(search)
  // The code a popup was already answered for, so accepting or dismissing one does not
  // immediately re-offer against the very text it just produced.
  var settled by remember { mutableStateOf<String?>(null) }
  // The line whose squiggle was tapped, whose findings the detail popup lists.
  // Derived visibility: when a republish leaves the line with no
  // findings, there is nothing left to show and the popup goes on its own.
  var detailLine by remember { mutableStateOf<Int?>(null) }
  // The hover the last "Symbol info" ask returned. Null rather than stale on a miss:
  // an answered-nothing clears whatever an earlier ask showed, so the popup never quotes a
  // symbol the caret has since moved off.
  var hover by remember { mutableStateOf<LanguageHover?>(null) }
  val scroll = remember { ScrollState(0) }
  val landingFocus = remember { FocusRequester() }
  val symbolFlash = definitionLandingFlash(state, caretLanding, landingFocus, onCaretLanded)

  // The chord's caret ask lands on the field's own requester - the field has to exist and be
  // on screen before it can hold a caret, the same fact `inViewportChanged` waits on upstream.
  LaunchedEffect(takesCaret) {
    if (!takesCaret) return@LaunchedEffect
    landingFocus.requestFocus()
    onCaretTaken()
  }

  LaunchedEffect(symbolAsk) {
    if (symbolAsk > 0) hover = hoverAt(state.text.toString(), state.selection.end)
  }

  // VS Code's rule - `contentHoverController` cancels the hover on `onDidChangeModelContent`:
  // text that changed under the answer means the answer no longer describes it. Same
  // `state.text` watch `reportCellEdits` runs, minus the debounce - a stale card hides at once.
  LaunchedEffect(state) {
    snapshotFlow { state.text.toString() }.drop(1).collect { hover = null }
  }

  reportCellEdits(state, onSourceEdited)

  input.attachWhileFocused(state, focused.value)

  // The field's own modifier: the focus requester a definition landing asks for is hoisted out
  // of the wrap/no-wrap split, since both branches want the same one - and `symbolFlash` lands
  // on the same `TextLayoutResult` coordinates `diagnosticSquiggles` draws in.
  val fieldModifier = modifier.focusRequester(landingFocus).semantics { contentDescription = fieldName }

  Box {
    SourceKeyboard {
      BasicTextField(
        state = state,
        // The gutter's width comes from the line *count*, so it never depends on the layout this
        // callback publishes - which is what keeps writing state from here out of a measure loop.
        // Wrapping off reaches the gutter through GutterOverlay below rather than through this
        // chain - its own finding that a gutter scrolled inside the same
        // modifiers as the field it decorates scrolls away with it.
        modifier = if (wrapEnabled) {
          // The squiggle sits after `lineNumbers`: its own trailing `.padding(start = gutter)` makes
          // this `drawBehind` the innermost one, so its draw scope starts at the code's left edge -
          // the `TextLayoutResult` coordinates `drawSquiggleSpan` resolves, one-to-one.
          fieldModifier
            .lineNumbers(
              state.text,
              layout,
              numberStyle,
              gutter,
              breakpoints,
              diagnostics,
              onGutterTap,
              onGutterLongPress,
              stoppedLine,
            )
            .diagnosticSquiggles(diagnostics, layout)
            .diagnosticTaps(diagnostics, layout) { detailLine = it }
            .symbolFlash(symbolFlash, layout)
        } else {
          fieldModifier
            .horizontalScroll(scroll)
            .padding(start = gutter)
            .diagnosticSquiggles(diagnostics, layout)
            .diagnosticTaps(diagnostics, layout) { detailLine = it }
            .symbolFlash(symbolFlash, layout)
        },
        onTextLayout = { getResult -> layout = getResult()?.also(onLayoutMeasured) },
        textStyle = codeStyle,
        // Source is not prose, so the keyboard must not correct it. Clearing the flag alone did not
        // stop Gboard splitting `getpass` into `get pass`; [SourceKeyboard] asks for the rest.
        // CodeCellFieldKeyboardTest reads both off the EditorInfo.
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        interactionSource = interactions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        inputTransformation = typing,
        // The highlight is read through a `State` rather than closed over, so a search moving does
        // not rebuild the transformation - see [HighlightTransformation].
        outputTransformation = remember(highlighter, styles, matched, searchStyles) {
          HighlightTransformation(highlighter, styles, focused, matched, searching, searchStyles)
        },
        decorator = { innerTextField ->
          Box {
            innerTextField()
            CellPopups(
              state = state,
              layout = layout,
              gutter = gutter,
              diagnostics = diagnostics,
              completions = completions,
              settled = settled,
              onSettled = { settled = it },
              detailLine = detailLine,
              onDetailDismissed = { detailLine = null },
              hover = hover,
              onHoverDismissed = { hover = null },
              signatureHelp = signatureHelp,
            )
          }
        },
      )
    }
    if (!wrapEnabled) {
      GutterOverlay(
        text = state.text,
        layout = layout,
        style = numberStyle,
        width = gutter,
        breakpoints = breakpoints,
        diagnostics = diagnostics,
        onGutterTap = onGutterTap,
        onGutterLongPress = onGutterLongPress,
        stoppedLine = stoppedLine,
      )
    }
  }
}

/**
 * The four popups a cell emits over its own text: the completion list, a finding's detail card,
 * the hover card and the signature-help card.
 *
 * They are emitted inside the field's `decorator` because a `Popup` anchors on the layout node
 * it is emitted under, and here that node's origin IS the text's own - inside every padding the
 * caller's modifier adds (`cellEditorBox`), inside the gutter's start padding and inside
 * `horizontalScroll`. A caret or line rect in `TextLayoutResult` coordinates then needs nothing
 * added and nothing subtracted. Emitted beside the field they anchored on the padded Box
 * instead, and the card's top sat one padding inside the line it described - `PopupAnchorAsserts`
 * reads the same bounds on screen.
 */
@Composable
private fun CellPopups(
  state: TextFieldState,
  layout: TextLayoutResult?,
  gutter: Dp,
  diagnostics: List<CellDiagnostic>,
  completions: CellCompletions?,
  settled: String?,
  onSettled: (String) -> Unit,
  detailLine: Int?,
  onDetailDismissed: () -> Unit,
  hover: LanguageHover?,
  onHoverDismissed: () -> Unit,
  signatureHelp: CellSignatureHelp?,
) {
  val offered = completions?.takeIf { it.query.code != settled }
  if (offered != null && layout != null) {
    CompletionPopup(
      completions = offered,
      caret = caretRect(layout, offered.query.cursorOffset),
      onPick = { match ->
        applyCompletion(state, offered, match)
        onSettled(state.text.toString())
      },
      onDismiss = { onSettled(offered.query.code) },
    )
  }
  val gutterPx = with(LocalDensity.current) { gutter.roundToPx() }
  val detail = detailLine?.let { line ->
    diagnostics.filter { it.line == line }.takeIf { it.isNotEmpty() }?.let { line to it }
  }
  val anchor = detail?.let { (line, _) ->
    layout?.let { diagnosticLineRect(it, logicalLineStarts(state.text), line, gutterPx) }
  }
  if (detail != null && anchor != null) {
    DiagnosticDetailPopup(detail.second, anchor, onDetailDismissed)
  }
  if (hover != null && layout != null) {
    HoverPopup(
      hover = hover,
      caret = caretRect(layout, state.selection.end),
      onDismiss = onHoverDismissed,
    )
  }
  if (signatureHelp != null && layout != null) {
    SignatureHelpPopup(
      cellHelp = signatureHelp,
      caret = caretRect(layout, signatureHelp.query.cursorOffset),
    )
  }
}

/**
 * Where the text cursor is within this field, in the text's own coordinates.
 *
 * The whole rect rather than just its bottom edge: the popup needs the *top* too, so it can sit
 * above the caret when there is no room below without covering the character being completed.
 *
 * No gutter or scroll term is added: the popup is emitted inside the field's `decorator`, whose
 * anchor's origin is the text layout's own - so [TextLayoutResult.getCursorRect]'s answer is
 * already in the space the `Popup` positions against.
 */
private fun caretRect(layout: TextLayoutResult?, cursorOffset: Int): IntRect {
  val rect = layout?.getCursorRect(cursorOffset.coerceIn(0, layout.layoutInput.text.length))
    ?: return IntRect(0, 0, 0, 0)
  return IntRect(
    left = rect.left.toInt(),
    top = rect.top.toInt(),
    right = rect.right.toInt(),
    bottom = rect.bottom.toInt(),
  )
}

/**
 * `_openReference`'s three moves (`goToCommands.ts`) as the field-side effect they are:
 * `Range.collapseToStart` for the caret, `.focus()` on the requester the caller hoisted,
 * and the `symbolHighlight` mark that holds the target range for 350 ms. Answers the
 * flash's live range so the field can draw it on the same `TextLayoutResult` coordinates
 * `diagnosticSquiggles` uses. Its own composable rather than three more branches in
 * [CodeCellField] - detekt counted them.
 */
@Composable
private fun definitionLandingFlash(
  state: TextFieldState,
  caretLanding: DefinitionTarget?,
  focusRequester: FocusRequester,
  onCaretLanded: () -> Unit,
): TextRange? {
  var symbolFlash by remember { mutableStateOf<TextRange?>(null) }
  LaunchedEffect(caretLanding) {
    val landing = caretLanding ?: return@LaunchedEffect
    state.edit { selection = TextRange(landing.offset.coerceIn(0, length)) }
    focusRequester.requestFocus()
    val text = state.text.toString()
    val start = landing.offset.coerceIn(0, text.length)
    symbolFlash = TextRange(start, landing.endOffset.coerceIn(start, text.length))
    onCaretLanded()
  }
  LaunchedEffect(symbolFlash) {
    if (symbolFlash == null) return@LaunchedEffect
    delay(SYMBOL_FLASH_MILLIS)
    symbolFlash = null
  }
  return symbolFlash
}

/** The name of a field whose caller says nothing more specific: a focusable field is never anonymous. */
internal const val CODE_EDITOR_NAME = "Code editor"

/** How long a definition's `symbolHighlight` mark stays on - `_openReference`, 350 ms. */
private const val SYMBOL_FLASH_MILLIS = 350L

/** A cell with no kernel behind it still edits; it just has nothing to suggest. */
private val NO_COMPLETIONS: suspend (String, Int) -> CompletionResult = { _, _ -> CompletionResult.NONE }

/** A field nobody wired the ask into still edits; "Symbol info" simply stays silent on it. */
private val NO_HOVER: suspend (String, Int) -> LanguageHover? = { _, _ -> null }

/** The same for signature help - a field with no server behind it asks nothing of anyone. */
private val NO_SIGNATURE_HELP: suspend (String, Int) -> LanguageSignatureHelp? = { _, _ -> null }

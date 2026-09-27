package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette

/**
 * One cell of the notebook: its header, its editor and its outputs.
 *
 * Its own file rather than a corner of [EditorScreen], which is what the file-length gate asked
 * for when the table of contents pane pushed that one past 500 lines - and the seam it asked for
 * was already there. The screen wires a document to its bars and decides where the table of
 * contents goes; this draws one cell and knows nothing about either.
 */
@Composable
internal fun EditorCell(
  cell: Cell,
  outputs: List<CellOutput>,
  executionCount: Int?,
  sourceRevision: Int,
  execution: CellExecution?,
  canPaste: Boolean,
  selected: Boolean,
  mark: CellMark?,
  markedCount: Int,
  tinted: Boolean,
  mode: CellMode,
  onRun: () -> Unit,
  onStop: () -> Unit,
  onSourceEdited: (String) -> Unit,
  /** `(cellId, code, cursorOffset)` - the language server keeps one document per cell (P5c). */
  completionsAt: suspend (cellId: String, code: String, cursorOffset: Int) -> CompletionResult,
  /**
   * `(cellId, code, cursorOffset)` - the "Symbol info" ask, P5e-3. Answers null where no
   * server or definition can respond, which the field then simply does not show.
   */
  hoverAt: suspend (cellId: String, code: String, cursorOffset: Int) -> LanguageHover? = NO_HOVER,
  /**
   * `(cellId, code, cursorOffset)` - the signature-help ask, P5e-4. Driven by typing like
   * [completionsAt]: a `(` or `,` inside a call asks, and the card stays while the call does.
   */
  signatureHelpAt: suspend (
    cellId: String,
    code: String,
    cursorOffset: Int,
  ) -> LanguageSignatureHelp? = NO_SIGNATURE_HELP,
  /**
   * Whether a session can answer the inspect rows, so the menu and this cell's accessibility
   * context menu only promise what can come back - `EditorUiState.kernelConnected`.
   */
  inspectionOffered: Boolean = false,
  onCellAction: (CellAction, CellEditorText) -> Unit,
  onFocusChanged: (Boolean) -> Unit,
  onToggleSelected: () -> Unit,
  markdownRendered: Boolean,
  markdownTakesCaret: Boolean,
  onMarkdownEditRequested: () -> Unit,
  /** How many cells this heading is hiding, or 0 when it is hiding none - see [HiddenCellsRow]. */
  hiddenBelow: Int,
  /** `notebook:toggle-all-cell-line-numbers`, the notebook's - see [gutterWidth]. */
  lineNumbersShown: Boolean,
  /** `docs/PLAN.md` P6c-4 - see [codeSourceStyle]. */
  codeFontSizeSp: Int,
  /** `docs/PLAN.md` P6c-5 - code cells only, see [CodeCellField]'s own parameter. */
  wrapEnabled: Boolean,
  /** `docs/PLAN.md` P6c-6 - code cells only, see [CodeCellField]'s own parameter. */
  completionEnabled: Boolean,
  /** Breakpoints set on this cell, by line - code cells only. See [NotebookDebugger]. */
  breakpoints: List<Breakpoint>,
  /** The language server's diagnostics on this cell, by line - code cells only. See [NotebookDiagnostics]. */
  diagnostics: List<CellDiagnostic>,
  /** A tap in this cell's gutter, reporting the one-based line it hit. */
  onGutterTap: (line: Int) -> Unit,
  /** A long-press on an armed line's gutter (P6a-16), reporting the one-based line it hit. */
  onGutterLongPress: (line: Int) -> Unit,
  /** Where the kernel is paused in this cell, one-based, or null - code cells only. See [NotebookDebugger]. */
  stoppedLine: Int?,
  /** `toc:display-numbering`'s answer for this cell's heading, or `""` - see [MarkdownCell]. */
  headingPrefix: String,
  /** What the find bar found in this cell's **source** - see [CellSearchHighlight]. */
  search: CellSearchHighlight,
  /**
   * What it found in this cell's outputs, one entry per *rendered* output and in their order -
   * empty while the `output` filter is off, which is its default.
   */
  outputSearch: List<CellSearchHighlight>,
  /** `notebook:toggle-autoclosing-brackets`, universal - code cells only, like the reference's. */
  autoClosingBrackets: Boolean,
  cellInput: FocusedCellInput,
  /**
   * A definition jump waiting to be taken, or null - `EditorUiState.caretLanding` already
   * filtered to this cell, so it is non-null only where the jump lands. [CodeCellField] applies
   * the caret and the flash, then [onCaretLanded] clears the state's one-shot.
   */
  caretLanding: DefinitionTarget? = null,
  /** Told once the landing was applied - `NotebookCellEdits.caretLanded` under another name. */
  onCaretLanded: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  // One text state per cell, owned here rather than inside the field: a cell has one source, and
  // the caret in it is read by the cell's actions as well as by the field. Created whether or not
  // a field is on screen, so collapsing a cell and expanding it keeps the caret and the typing
  // history instead of re-seeding from the model.
  val field = rememberTextFieldState(cell.source)
  // **Here and not in the field**, which is the seam [CellSourceTracking] records: a change of type
  // composes a different field over this same state, and a counter seeded per field would start
  // already up to date and adopt the rewrite that came with the change.
  adoptRewrittenSource(field, cell.source, sourceRevision)
  // Every action carries what this cell's field is holding, read when the action is chosen rather
  // than when the menu was built. Only Split reads it, and it is the one action that is not a pure
  // function of the document - see [CellEditorText]. Both surfaces dispatch through here, so the
  // accessibility context menu gets it too.
  //
  // **All but one**: `SymbolInfo` is answered here and not dispatched, because what it needs is
  // the caret this cell owns and the popup the field draws - [ChooseHeadingLevel]'s own shape of
  // a row the surface itself answers, so `CellEdits` never sees an ask that is not an edit. The
  // token and not a boolean: a second tap must re-ask even with nothing else changed, and a
  // remembered boolean that was already true would not.
  var symbolAsk by remember { mutableIntStateOf(0) }
  val act: (CellAction) -> Unit = { action ->
    if (action == CellAction.SymbolInfo) {
      symbolAsk++
    } else {
      onCellAction(action, CellEditorText(field.text.toString(), field.selection))
    }
  }
  // The one rule both the stripe and the editor's border hang off - see [cellIsBeingEdited].
  val editing = cellIsBeingEdited(selected, mode)
  val stripe = cellStripe(
    selected = selected,
    mode = mode,
    status = execution?.status ?: ExecutionStatus.NOT_STARTED,
    palette = editorPalette,
  )
  // The whole cell carries its actions for a screen reader, not only the `⋮` that opens them.
  CellFrame(
    stripe = stripe,
    tint = if (tinted) editorPalette.cellMultiSelected else Color.Unspecified,
    modifier = modifier.cellCustomActions(
      cell = cell,
      canPaste = canPaste,
      selecting = mark != null,
      // What *this* cell's menu will act on: the whole run when it is in it, itself when not.
      count = if (mark?.checked == true) markedCount else 1,
      inspection = inspectionOffered,
      onAction = act,
    ),
  ) {
    CellHeader(
      cell = cell,
      executionCount = executionCount,
      execution = execution,
      canPaste = canPaste,
      inspection = inspectionOffered,
      mark = mark,
      onToggleSelected = onToggleSelected,
      // One Run for both kinds now: `runCells` is what decides that a code cell reaches the kernel
      // and a markdown cell renders, exactly as `Private.runCells` does.
      onRun = onRun,
      onStop = onStop,
      onCellAction = act,
    )
    Spacer(modifier = Modifier.height(Spacing.xs))
    // Wrapped so the focus listener sits on the *input area* rather than the whole cell, which
    // is the distinction JupyterLab draws in `_evtFocusIn`: focus anywhere in a cell selects it,
    // but only focus inside the editor means edit mode. Put on the Column, the header's buttons
    // would count as typing.
    Box(modifier = onCellInputFocus(onFocusChanged)) {
      if (cell.sourceHidden) {
        // Collapsed beats every other source state: there is nothing to type into, and the tap
        // that expands must not also land in a text field.
        CollapsedCell(
          preview = firstNonEmptyLine(cell.source),
          description = COLLAPSED_SOURCE,
          onExpand = { act(CellAction.ToggleSourceHidden) },
        )
      } else {
        when (cell.kind) {
          CellKind.CODE -> CodeCellField(
            state = field,
            onSourceEdited = onSourceEdited,
            completionsAt = { code, cursorOffset -> completionsAt(cell.id, code, cursorOffset) },
            hoverAt = { code, cursorOffset -> hoverAt(cell.id, code, cursorOffset) },
            signatureHelpAt = { code, cursorOffset -> signatureHelpAt(cell.id, code, cursorOffset) },
            symbolAsk = symbolAsk,
            input = cellInput,
            lineNumbersShown = lineNumbersShown,
            codeFontSizeSp = codeFontSizeSp,
            wrapEnabled = wrapEnabled,
            completionEnabled = completionEnabled,
            breakpoints = breakpoints,
            diagnostics = diagnostics,
            onGutterTap = onGutterTap,
            onGutterLongPress = onGutterLongPress,
            stoppedLine = stoppedLine,
            search = search,
            autoClosingBrackets = autoClosingBrackets,
            caretLanding = caretLanding,
            onCaretLanded = onCaretLanded,
            modifier = Modifier.fillMaxWidth().cellEditorBox(editing),
          )

          CellKind.MARKDOWN -> MarkdownCell(
            state = field,
            source = cell.source,
            rendered = markdownRendered,
            // Null on anything that is not a heading, which is the reference's `level > 0` guard.
            fold = cell.headingLevel?.let { HeadingFold(it, cell.headingCollapsed) },
            onToggleHeadingCollapse = { act(CellAction.ToggleHeadingCollapse) },
            headingPrefix = headingPrefix,
            editing = editing,
            onEditRequested = onMarkdownEditRequested,
            onSourceEdited = onSourceEdited,
            input = cellInput,
            takesCaret = markdownTakesCaret,
            lineNumbersShown = lineNumbersShown,
            codeFontSizeSp = codeFontSizeSp,
            search = search,
            modifier = Modifier.fillMaxWidth(),
          )

          // The code cell's panel rather than the markdown editor's: the reference gives a raw
          // cell no styling of its own, so it draws in the same input area a code cell does.
          CellKind.RAW -> RawCellField(
            state = field,
            onSourceEdited = onSourceEdited,
            input = cellInput,
            lineNumbersShown = lineNumbersShown,
            codeFontSizeSp = codeFontSizeSp,
            search = search,
            modifier = Modifier.fillMaxWidth().cellEditorBox(editing),
          )
        }
      }
    }
    CellOutputs(
      outputs = outputs,
      hidden = cell.outputsHidden,
      scrolled = cell.outputsScrolled,
      search = outputSearch,
      onExpand = { act(CellAction.ToggleOutputsHidden) },
    )
    // Inside this cell's frame rather than between it and the next, because that is what it is
    // about: the reference appends the button to the heading cell's own node (`widget.ts:2429`).
    if (hiddenBelow > 0) {
      HiddenCellsRow(count = hiddenBelow, onExpand = { act(CellAction.ToggleHeadingCollapse) })
    }
  }
}

/** A cell whose caller never wired the ask has nothing to show - [NO_COMPLETIONS]'s own rule. */
private val NO_HOVER: suspend (String, String, Int) -> LanguageHover? = { _, _, _ -> null }

/** The same for signature help - [CodeCellField]'s own `NO_SIGNATURE_HELP` one level up. */
private val NO_SIGNATURE_HELP: suspend (String, String, Int) -> LanguageSignatureHelp? = { _, _, _ -> null }

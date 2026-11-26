package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageSignatureHelp

/**
 * The notebook's own cell list, split out of [EditorScreen] for the same reason
 * [EditorTopBars]/[EditorBottomBars] were: a file-length gate, not a change in responsibility -
 * this is still [EditorScreen]'s own body, drawn by the `Scaffold` slot that used to hold it
 * inline.
 */
@Composable
internal fun EditorContent(
  state: EditorUiState,
  onRunCell: (Cell) -> Unit,
  onStopCell: () -> Unit,
  /** `(cellId, answer)` - a cell's prompt field submitted. */
  onAnswerInput: (String, String) -> Unit,
  onEditCell: (String, String) -> Unit,
  /** A tap in a cell's gutter, reporting which cell and which one-based line it hit. */
  onToggleBreakpoint: (String, Int) -> Unit,
  /** A long-press on an armed line's gutter, confirmed through [BreakpointConditionDialog]. */
  onSetCondition: (cellId: String, line: Int, condition: String?) -> Unit,
  /** `(cellId, code, cursorOffset)` - the cell keys the language server's document. */
  completionsAt: suspend (cellId: String, code: String, cursorOffset: Int) -> CompletionResult,
  /** `(cellId, code, cursorOffset)` - the "Symbol info" ask - see [EditorCell]. */
  hoverAt: suspend (cellId: String, code: String, cursorOffset: Int) -> LanguageHover?,
  /** `(cellId, code, cursorOffset)` - the signature-help ask - see [EditorCell]. */
  signatureHelpAt: suspend (cellId: String, code: String, cursorOffset: Int) -> LanguageSignatureHelp?,
  onCellAction: (String, CellAction, CellEditorText) -> Unit,
  onCellFocusChanged: (String, Boolean) -> Unit,
  onToggleSelected: (String) -> Unit,
  onMarkdownEditRequested: (String) -> Unit,
  cellInput: FocusedCellInput,
  /** `notebook:toggle-autoclosing-brackets`, universal - code cells only, like the reference's. */
  autoClosingBrackets: Boolean,
  /** `indentUnit` - the level a `:` line adds; see [EditorSettings.indentUnit]. */
  indentUnit: IndentUnit,
  /** `notebook:toggle-all-cell-line-numbers` - app-wide and persisted. */
  lineNumbersShown: Boolean,
  /** App-wide and persisted, see [codeSourceStyle]. */
  codeFontSizeSp: Int,
  /** App-wide and persisted, code cells only. See [EditorCell]. */
  wrapEnabled: Boolean,
  /** App-wide and persisted, code cells only. See [EditorCell]. */
  completionEnabled: Boolean,
  /** `toc:display-numbering` / `toc:display-h1-numbering` - see [MarkdownCell]. */
  tocNumbering: TocNumbering,
  /** Every heading in the notebook, numbered as one document - see [tableOfContentsHeadings]. */
  tocHeadings: List<TocHeading>,
  /** Hoisted so the table of contents' click-to-jump drives the same list a tap does. */
  listState: LazyListState,
  /**
   * The one [FocusRequester] the head cell's frame attaches - hoisted for the same reason
   * [listState] is: `enter-command-mode` and the focus-following effect are asked of it from
   * here and from [EditorScreen]'s BackHandler, and a requester a caller cannot reach is one it
   * cannot aim.
   */
  headFocus: FocusRequester,
  /** The frame's `_evtFocusIn` report - routed to `NotebookCellSelection.activate`. */
  onCellFrameFocused: (String) -> Unit,
  /** Told once a landing field took its caret - `NotebookCellEdits.caretLanded` wired through. */
  onCaretLanded: () -> Unit,
  /** Told once a caret-asked field took it - `NotebookCellEdits.caretTaken` wired through. */
  onCaretTaken: () -> Unit,
  /**
   * A hardware key chord resolved to a notebook command - see [notebookChord]. Wired once on the
   * list rather than per cell, which is where a focused field's key events surface on their way
   * up: `onPreviewKeyEvent` runs before the editor's own Enter handling, so a claimed chord never
   * reaches the field that would have made it a newline.
   */
  onNotebookChord: (NotebookChordIntent) -> Unit,
  /** A tap on the footer's "Code" or "Markdown", reporting the kind to append - see [AddCellFooter]. */
  onAddCell: (CellKind) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Which cell/line's condition is being edited, or null while the dialog is closed - ephemeral
  // UI state, not something `EditorUiState` itself needs to carry: nothing outside this composable
  // reads it, the same reason `settled`/`tocOpen`-shaped flags in this file's siblings stay local.
  var conditionPrompt by remember { mutableStateOf<Pair<String, Int>?>(null) }
  when {
    state.error != null -> ErrorMessage(message = state.error, modifier = modifier)
    !state.loaded -> EmptyMessage("Loading…", modifier)
    // No empty branch: a loaded notebook always has at least one cell, given one by
    // toEditorUiState if the file had none. A "No cells yet" message would advertise a state
    // that cannot happen, and used to be a dead end when it did.
    else -> {
      // Computed once for the whole list rather than per cell: `selectedCellIds` walks the cells
      // to find both ends, and asking it inside the item lambda would make drawing the notebook
      // quadratic in its length.
      val marked = state.selectedCellIds.toSet()
      // Walks the whole notebook, so it is computed once for the list rather than per item -
      // asking inside the item lambda would make drawing a notebook quadratic in its length.
      val onScreen = state.cellsOnScreen
      // Numbered once for the whole notebook rather than per cell, for the reason the two above
      // are: the numbers are the *document's* - one running counter over every heading - so asking
      // inside the item lambda would be both wrong and quadratic. Keyed by cell, so a heading a
      // code cell printed is counted but leaves no prefix for a markdown cell to draw.
      val headingPrefixes = tocHeadings.map { it.cellId }.zip(tocHeadings.tocPrefixes(tocNumbering)).toMap()
      // The `.jp-Cell:focus` selector as a flag: whether the head cell's frame itself owns focus,
      // rather than one of its descendants - a `⋮` focused inside it also surfaces chord events
      // here, and upstream's `Enter` binding means the frame alone (`tracker.json:447`).
      var frameOwnsFocus by remember { mutableStateOf(false) }
      // `focusActiveCell`'s precondition as the one function: `requestFocus` throws on a requester
      // nothing has attached, so the ask stands down on a head the list has not composed.
      val requestHeadFocus = {
        if (listState.composesCell(state.selectedCellId)) headFocus.requestFocus()
      }
      // The key codes of chords the down-stroke claimed: a claimed press is ours end to end, so
      // its up is consumed too. An Escape up left to propagate reaches the platform's Esc→Back
      // translation, which fires `onBackPressed` the moment command mode has already landed and
      // no `BackHandler` stands enabled - the notebook itself backs out under the frame that
      // just took focus.
      val claimedKeys = remember { mutableSetOf<Int>() }
      // `focusActiveCell` (`packages/notebook/src/actions.tsx:2585`): the head moves and focus
      // follows it, so command mode's keys always have their owner. `selecting` is also a key,
      // so the frame takes focus back the moment marking ends.
      LaunchedEffect(state.selectedCellId, state.mode, state.selecting) {
        if (state.commandFocusMayMove()) requestHeadFocus()
      }
      // **Beside the notebook, never instead of it** - see [EditorUiState.saveFailure]. A failure to
      // save is a state that goes on being true, so it is a banner over the document rather than
      // the snackbar an export's outcome gets, and there is nothing to remember having shown.
      Column(modifier = modifier.fillMaxSize()) {
        state.saveFailure?.let { SaveFailureBanner(it) }
        LazyColumn(
          state = listState,
          // `testTagsAsResourceId` on the list, as the launcher and terminal surfaces set it:
          // the head frame's tag rides the accessibility dump a flow or a `uiautomator` capture
          // reads, which is how the focus owner is named from outside the app.
          modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.onPreviewKeyEvent { event ->
            dispatchNotebookKey(event, state.mode, frameOwnsFocus, claimedKeys, requestHeadFocus, onNotebookChord)
          },
        ) {
          // contentType as well as key: a code cell and a markdown cell compose to very different
          // trees, and telling the list so lets it reuse each kind against its own pool instead of
          // rebuilding whenever the two alternate.
          itemsIndexed(onScreen, key = { _, cell -> cell.id }, contentType = { _, cell -> cell.kind }) { at, cell ->
            EditorCell(
              cell = cell,
              outputs = state.outputs[cell.id].orEmpty(),
              inputRequest = state.inputRequests[cell.id],
              executionCount = state.executionCounts[cell.id],
              sourceRevision = state.sourceRevisions[cell.id] ?: 0,
              execution = state.executionOf(cell.id),
              canPaste = state.clipboard.isNotEmpty(),
              selected = state.selectedCellId == cell.id,
              // Null unless a run is being marked, which is what keeps the tick and the `⋮` from
              // ever being on screen together. The position is 1-based and is the screen reader's,
              // not the eye's - see [CellMark].
              mark = if (state.selecting) CellMark(cell.id in marked, position = at + 1) else null,
              markedCount = marked.size,
              // The reference's rule, read literally: the tint is on the cell node and the active
              // cell only joins it once the run holds more than one - `jp-mod-multiSelected` is
              // added at `count > 1` (`packages/notebook/src/widget.ts:2763-2765`).
              tinted = marked.size > 1 && cell.id in marked,
              mode = state.mode,
              onRun = { onRunCell(cell) },
              onStop = onStopCell,
              onAnswerInput = { answer -> onAnswerInput(cell.id, answer) },
              onSourceEdited = { source -> onEditCell(cell.id, source) },
              completionsAt = completionsAt,
              hoverAt = hoverAt,
              signatureHelpAt = signatureHelpAt,
              // The asks can only answer while a session stands - `cellActionItems`'s own rule
              // about promising nothing that cannot come back.
              inspectionOffered = state.kernelConnected,
              onCellAction = { action, editor -> onCellAction(cell.id, action, editor) },
              onFocusChanged = { focused -> onCellFocusChanged(cell.id, focused) },
              onToggleSelected = { onToggleSelected(cell.id) },
              // Rendered unless somebody opened this one - see [MarkdownEditing]. Read from the state
              // rather than remembered in the cell, because a run has to be able to close the editor.
              markdownRendered = state.isMarkdownRendered(cell.id),
              // Only the cell a tap asked for - a command that unrenders leaves the caret alone.
              markdownTakesCaret = state.markdownTakingCaret == cell.id,
              onMarkdownEditRequested = { onMarkdownEditRequested(cell.id) },
              // Only ever non-zero on a collapsed heading, which is the reference's own condition for
              // drawing the row at all (`packages/cells/src/widget.ts:2424-2427`).
              hiddenBelow = if (cell.headingCollapsed) state.headingSectionOf(cell.id).size else 0,
              // One flag for the whole notebook and all three cell types, which is what the command
              // is: `toggleAllLineNumbers` writes code, markdown and raw together.
              lineNumbersShown = lineNumbersShown,
              codeFontSizeSp = codeFontSizeSp,
              wrapEnabled = wrapEnabled,
              completionEnabled = completionEnabled,
              breakpoints = state.breakpointsByCellId[cell.id].orEmpty(),
              // The language server's own marks - see [EditorUiState.diagnosticsByCellId].
              diagnostics = state.diagnosticsByCellId[cell.id].orEmpty(),
              onGutterTap = { line -> onToggleBreakpoint(cell.id, line) },
              onGutterLongPress = { line -> conditionPrompt = cell.id to line },
              stoppedLine = state.stoppedLineOf(cell.id),
              headingPrefix = headingPrefixes[cell.id].orEmpty(),
              // Per cell rather than sliced once for the list, unlike the numbers above: a cell's
              // matches depend only on that cell - see [searchHighlightOf].
              search = state.searchHighlightOf(cell.id),
              outputSearch = state.outputSearchHighlightsOf(cell.id),
              autoClosingBrackets = autoClosingBrackets,
              indentUnit = indentUnit,
              cellInput = cellInput,
              // A pending jump names exactly one cell, so the state's landing is filtered to it
              // before it is handed down - every other cell's field sees null and does nothing.
              caretLanding = state.caretLanding?.takeIf { it.cellId == cell.id },
              onCaretLanded = onCaretLanded,
              // The chord's caret ask names exactly one cell, filtered the way `caretLanding` is.
              takesCaret = state.cellTakingCaret == cell.id,
              onCaretTaken = onCaretTaken,
              modifier = Modifier
                // `tabIndex="0"` on the active cell alone (`widget.ts:2734-2750`): only the head's
                // frame is a focus target, so the keyboard's focus walk reaches one surface.
                .then(
                  if (cell.id == state.selectedCellId) {
                    Modifier.headCellFrame(
                      headFocus,
                      onFocus = { self -> frameOwnsFocus = self },
                      // `_evtFocusIn`: focus entering the cell selects it into command mode.
                      onEnter = { onCellFrameFocused(cell.id) },
                    )
                  } else {
                    Modifier
                  },
                )
                // `_evtMouseDown` (`widget.ts:3565-3600`): a press the children did not take lands
                // on the cell, which is the same activate the focus report makes - except the
                // tap on the already-head's chrome asks the focus directly, there being no
                // selection change left to follow.
                .cellChromeTap(enabled = !state.selecting, cellId = cell.id) {
                  if (cell.id == state.selectedCellId) requestHeadFocus() else onCellFrameFocused(cell.id)
                },
            )
          }
          item(key = ADD_CELL_FOOTER_KEY, contentType = ADD_CELL_FOOTER_KEY) { AddCellFooter(onAdd = onAddCell) }
        }
      }
    }
  }
  conditionPrompt?.let { (cellId, line) ->
    val existing = state.breakpointsByCellId[cellId]?.firstOrNull { it.line == line }?.condition
    BreakpointConditionDialog(
      currentCondition = existing,
      onConfirm = { condition ->
        onSetCondition(cellId, line, condition)
        conditionPrompt = null
      },
      onDismiss = { conditionPrompt = null },
    )
  }
}

/**
 * `focusActiveCell`'s precondition as the one question the effect asks: command mode is where the
 * frame owns focus, marking is a touch surface that already cleared its own, and a caret ask is a
 * focus about to land - moving the frame under any of them steals the focus they are owed.
 */
private fun EditorUiState.commandFocusMayMove(): Boolean = mode == CellMode.COMMAND &&
  !selecting &&
  cellTakingCaret == null &&
  markdownTakingCaret == null &&
  caretLanding == null

/**
 * One hardware event against the keymap. A chord claimed on its down is ours end to end, so its
 * up is consumed with it ([claimedKeys] holds the key codes a down-stroke took): an Escape up
 * left to propagate reaches the platform's Esc→Back translation, which fires `onBackPressed` the
 * moment command mode has already landed and no `BackHandler` stands enabled.
 */
private fun dispatchNotebookKey(
  event: KeyEvent,
  mode: CellMode,
  frameOwnsFocus: Boolean,
  claimedKeys: MutableSet<Int>,
  requestHeadFocus: () -> Unit,
  onNotebookChord: (NotebookChordIntent) -> Unit,
): Boolean {
  if (event.type == KeyEventType.KeyUp) return claimedKeys.remove(event.nativeKeyEvent.keyCode)
  return event.type == KeyEventType.KeyDown &&
    claimNotebookKey(event, mode, frameOwnsFocus, claimedKeys, requestHeadFocus, onNotebookChord)
}

/**
 * A key-down the list previews: resolve the chord, then claim and dispatch the intent.
 * `.jp-Cell:focus`, not `:focus-within` (`tracker.json:447`) - Enter with a descendant control
 * focused (a `⋮`, say) is that control's own key, so [frameOwnsFocus] gates the one intent
 * upstream scoped to the cell node itself.
 */
private fun claimNotebookKey(
  event: KeyEvent,
  mode: CellMode,
  frameOwnsFocus: Boolean,
  claimedKeys: MutableSet<Int>,
  requestHeadFocus: () -> Unit,
  onNotebookChord: (NotebookChordIntent) -> Unit,
): Boolean {
  val intent = notebookChord(mode, event.chord())
    ?.takeUnless { it == NotebookChordIntent.EnterEditMode && !frameOwnsFocus }
    ?: return false
  claimedKeys += event.nativeKeyEvent.keyCode
  if (intent == NotebookChordIntent.EnterCommandMode) {
    // Upstream "just unfocuses the editor" (`actions.tsx`'s `enterCommandMode`); where focus goes
    // is ours to answer, and the head cell's frame is command mode's owner.
    requestHeadFocus()
  } else {
    onNotebookChord(intent)
  }
  return true
}

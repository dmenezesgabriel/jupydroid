package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.data.fake.FakeTerminalSettingsRepository
import com.jupydroid.app.domain.model.TerminalFontSize
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.domain.port.TerminalSettingsRepository
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.editor.codeSourceStyle
import com.jupydroid.app.ui.editor.codeTextStyle
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

/** So a Compose test - and a Maestro flow, through `testTagsAsResourceId` - can reach the field. */
const val TERMINAL_INPUT_TAG = "terminal-input"

/**
 * A shell, reachable, typeable and drawn faithfully - `docs/PLAN.md`'s P4i, sub-steps 1 and 2:
 * batched-run glyphs and a block cursor on a real [TerminalCanvas], the VS Code palette resolved
 * to the system's own dark/light theme, and a cell measured on this device rather than restated.
 *
 * ```
 * TerminalPaneScreen(session = TerminalSession("1"), terminalGateway)
 * ```
 */
@Composable
fun TerminalPaneScreen(
  session: TerminalSession,
  terminalGateway: TerminalGateway,
  modifier: Modifier = Modifier,
  /** The terminal's own font-size setting - `docs/PLAN.md`'s P4j-3c. */
  terminalSettingsRepository: TerminalSettingsRepository = FakeTerminalSettingsRepository(),
  // Read here, not inside the factory lambda below - `isSystemInDarkTheme` is itself composable
  // and the factory runs once, outside recomposition, the way `viewModel {}` always does.
  darkTheme: Boolean = isSystemInDarkTheme(),
  /**
   * The shell's OSC title, reported up to the tab that carries it - `term.onTitleChange` sets
   * `this.title.label` in the reference (`packages/terminal/src/widget.ts:486-488`), and the
   * label is the tab's, not a panel row's. Null means "no title set" and the caller falls back
   * to "Terminal N".
   */
  onTitleChanged: (String?) -> Unit = {},
  viewModel: TerminalPaneViewModel = viewModel {
    val palette = if (darkTheme) TerminalPalette.VS_CODE_DARK else TerminalPalette.VS_CODE_LIGHT
    TerminalPaneViewModel(session, terminalGateway, terminalSettingsRepository, palette = palette)
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  KeepTheShellAttached(viewModel)
  // `term.onTitleChange` → `title.label` (`packages/terminal/src/widget.ts:486-488`): the OSC title
  // is a *tab* fact, reported up every time it lands rather than drawn on a panel row the tab took.
  LaunchedEffect(state.title) { onTitleChanged(state.title) }
  // The one style [TerminalCanvas]'s glyphs and [terminalCellMetrics]'s own measurement share -
  // resolved once here rather than inside `TerminalCanvas`, which no
  // longer reads the setting itself.
  val textStyle = codeTextStyle(fontSize = state.fontSizeSp.sp)
  // the last cell height [TerminalCanvas] reported, so a drag's own
  // pixel delta can be turned into rows without waiting for a fresh `onGridSizeChanged` call -
  // `terminalCellMetrics`'s own measurement never changes mid-gesture, only mid-recomposition.
  var cellHeightPx by remember { mutableIntStateOf(1) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    // **The page a document is drawn on, stated rather than inherited.** `Scaffold`'s default is
    // `colorScheme.background`, which this app maps onto `editor.background` - the right colour
    // by way of a role that could be re-mapped, and `ColorSchemeTest` asserts only that a role
    // is *somewhere* on the ramp, never which position. VS Code separates a part's identity from
    // a surface's elevation, and this is the document's own id (`docs/DECISIONS.md`, "The
    // palette states its colours; it does not derive them from a Material role").
    containerColor = editorPalette.editorBackground,
    topBar = {
      TerminalBar(
        fontSizeSp = state.fontSizeSp,
        onIncreaseFontSize = { viewModel.changeFontSize(TerminalFontSize.STEP) },
        onDecreaseFontSize = { viewModel.changeFontSize(-TerminalFontSize.STEP) },
      )
    },
    // `EditorBottomBars`/`SymbolRow`'s own slot, so this row carries its
    // own `imePadding`/`navigationBarsPadding` and Scaffold reserves it in `padding` below exactly
    // as it already does for the editor's bottom bar.
    bottomBar = {
      TerminalExtraKeysRow(
        latches = state.latches,
        applicationCursorKeys = { viewModel.emulator.isApplicationCursorKeys },
        onTapModifier = viewModel::tapModifier,
        onLongPressModifier = viewModel::longPressModifier,
        onKey = viewModel::type,
      )
    },
  ) { padding ->
    // A `Box`, not a `Column` with the field docked below it - 1. The two
    // layers share the same bounds: `TerminalCanvas` draws every glyph and the block cursor, and
    // `TerminalInputField` sits over the whole of it so a tap anywhere in the terminal is a tap on
    // the one thing that receives input, the way Termux's `TerminalView` is both at once.
    //
    // `consumeWindowInsets(WindowInsets.ime)` - `TerminalExtraKeysRow` is the bottom bar and
    // already carries its own `imePadding` (`EditorScreen.kt`'s own rule: the bar owns the
    // keyboard's inset, the content beside it does not), so `padding` above already reflects the
    // bar's ime-grown height. Without this, `TerminalCanvas`'s own `imePadding` read the same,
    // still-unconsumed inset again and shrank an already-correct area a second time - the doubled
    // window inset `ShellSidePane.kt`'s `panelInsets` already names for the navigation bar, found
    // here for the ime by a device measurement rather than a re-reading of the source.
    //
    // `pointerInput(cellHeightPx) { detectTerminalScrollDrag(...) }` sits on this `Box` - the
    // ancestor of both `TerminalCanvas` and `TerminalInputField` - rather than `Modifier.scrollable`
    // on either sibling, 3's own device-measured finding: a `Modifier.scrollable`
    // on `TerminalCanvas` never once saw a real drag, because `TerminalInputField`'s own
    // `BasicTextField` already consumes touch movement in `PointerEventPass.Main` - the pass every
    // gesture detector in this codebase defaults to - before a sibling's or an ancestor's own
    // Main-pass handler would ever run. `PointerEventPass.Initial` runs ancestor-to-descendant,
    // ahead of the field's pass, so consuming a drag here is what actually pre-empts it.
    Box(
      modifier = Modifier.padding(padding).fillMaxSize().consumeWindowInsets(WindowInsets.ime)
        .pointerInput(cellHeightPx) { detectTerminalScrollDrag { delta -> viewModel.scrollBy(delta, cellHeightPx) } },
    ) {
      // Keyed on the redraw counter: `emulator.screen.grid` is mutated in place by
      // `TerminalPaneViewModel`, which Compose cannot observe on its own - `redraw` is the `State`
      // that stands in for it, exactly as `EditorUiState.sourceRevisions` stands in for a text
      // field Compose does not own either.
      key(state.redraw) {
        TerminalCanvas(
          emulator = viewModel.emulator,
          onGridSizeChanged = { grid, cell ->
            viewModel.resize(grid, cell.width, cell.height)
            cellHeightPx = cell.height
          },
          modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
          textStyle = textStyle,
          topRow = state.topRow,
        )
      }
      TerminalInputField(
        onTypedText = viewModel::typeCharacters,
        onEncodedKey = viewModel::type,
        applicationCursorKeys = { viewModel.emulator.isApplicationCursorKeys },
      )
    }
  }
}

/**
 * Public rather than `private` - [TerminalActionsMenuTest][com.jupydroid.app.TerminalActionsMenuTest]
 * drives this directly, [NotebookActionsMenuTest][com.jupydroid.app.ui.editor.NotebookActionsMenuTest]'s
 * own reason - a test composing [TerminalActionsMenu] on its own would still pass with the button
 * that opens it gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalBar(fontSizeSp: Int, onIncreaseFontSize: () -> Unit, onDecreaseFontSize: () -> Unit) {
  TopAppBar(
    // The tab above names it, and the OSC title goes there too - `onTitleChange` sets
    // `this.title.label`, which is the tab's (`packages/terminal/src/widget.ts:486-488`). No
    // navigation icon either: leaving is the strip's own act, the ✕ or the one-pane arrow.
    title = {},
    actions = {
      TerminalActionsMenu(
        fontSizeSp = fontSizeSp,
        onIncreaseFontSize = onIncreaseFontSize,
        onDecreaseFontSize = onDecreaseFontSize,
      )
    },
  )
}

/**
 * Attaches while the screen is at least visible, and cancels the moment it is not - `docs/PLAN.md`'s
 * P4i-3b. `ExplorerScreen.KeepTheListingFresh` is this codebase's own precedent for a
 * lifecycle-gated collection, but on `STARTED` here rather than its `RESUMED`, a deliberate
 * departure: `RESUMED` is right for a periodic poll that should skip a wakeup nobody is
 * interactively looking at, and it pauses on a transient partial-obscure too (a permission dialog,
 * the notification shade) - tearing a live pty connection down and replaying scrollback every time
 * one flickers over the screen would be a worse experience than what this sub-step is fixing, not
 * a match for it. `STARTED` is Android's own documented case for "stay alive while visible."
 *
 * Cancelling closes the socket without this class asking it to (`TerminalGateway.attach`'s own
 * KDoc), which is what makes backgrounding cheap; coming back is a re-attach the server answers by
 * replaying what was missed.
 */
@Composable
private fun KeepTheShellAttached(viewModel: TerminalPaneViewModel) {
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner, viewModel) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.attach() }
  }
}

/**
 * A drag over the terminal scrolls it into history - and must win
 * that decision *before* [TerminalInputField]'s own `BasicTextField` does. Measured on the
 * tablet, not guessed: `Modifier.scrollable` placed on either the canvas or its ancestor never
 * once saw a real drag, because the field's own touch handling runs on the default
 * [PointerEventPass.Main] - the pass every gesture detector in this codebase already uses - which
 * a descendant processes *before* an ancestor's or a sibling's own Main-pass handler ever would,
 * and the field consumes movement unconditionally rather than only once a drag is confirmed.
 * [PointerEventPass.Initial] runs ancestor-to-descendant, ahead of the field's own pass, so
 * consuming here is what actually pre-empts it; a plain tap (no travel past
 * [PointerInputScope.viewConfiguration]'s own touch slop) is left unconsumed, so tap-to-focus
 * still reaches the field beneath.
 *
 * [onDragBy] receives the drag's own screen-space `y` delta (positive when a finger moves down),
 * negated before it reaches [TerminalPaneViewModel.scrollBy] - a downward drag reveals what is
 * *above* the live screen, i.e. history, which is [TerminalGrid.clampedTopRow]'s negative
 * direction, not its positive one.
 *
 * Drag only, not fling - this step's own gate is "scroll back... and show it", not momentum. A
 * fling is a named follow-up rather than silently dropped.
 */
private suspend fun PointerInputScope.detectTerminalScrollDrag(onDragBy: (Float) -> Unit) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var overSlop = 0f
    var dragging = false
    while (true) {
      val event = awaitPointerEvent(pass = PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == down.id }
      if (change == null || change.changedToUpIgnoreConsumed()) break
      val delta = change.positionChange().y
      if (dragging) {
        change.consume()
        onDragBy(-delta)
      } else {
        overSlop += delta
        if (abs(overSlop) > viewConfiguration.touchSlop) dragging = true
      }
    }
  }
}

/**
 * Renders nothing of its own - [TerminalCanvas] draws every glyph and the block cursor from the
 * emulator's own grid, the same decoupling Termux's `TerminalView` gets for free from being one
 * `View`: `onCreateInputConnection` owns the IME, and the block cursor it draws is a rendering
 * concern with no tie back to it (confirmed reading `TerminalView.java` - it never touches
 * `CursorAnchorInfo`). `BasicTextField`'s own `TextFieldDecoratorModifier` - focus, touch-to-focus,
 * the IME session - sits on the field's outer `Box`, outside a [TextFieldDecorator]'s reach, so
 * never calling `innerTextField` here still leaves the field genuinely focusable and typeable.
 */
private val invisibleDecorator = TextFieldDecorator { }

/**
 * Printable characters and Enter go through [onTypedText] via [reportTerminalKeystroke] - the
 * soft keyboard's own text, subject to `TerminalPaneViewModel.typeCharacters`'s tapped Ctrl/Alt
 * latch. A hardware arrow key or Ctrl+letter is intercepted first and
 * sent through [terminalKeystroke] to [onEncodedKey] instead, verbatim - 2,
 * the encoder P4j-1's own KDoc deferred. [applicationCursorKeys] is read fresh on every key rather
 * than once at composition, since a shell can flip DECCKM at any time (entering `vi` does).
 *
 * The two callbacks are not interchangeable: [onEncodedKey] carries bytes that are already a
 * complete escape sequence (a hardware key's own [terminalKeystroke] output, or
 * [TerminalExtraKeysRow]'s own taps) and must reach the shell unmodified - running an escape
 * sequence through a Ctrl/Alt latch one character at a time would corrupt it.
 *
 * Sized to fill the terminal area it sits over ([TerminalPaneScreen]'s `Box`), not a strip below
 * it - 1, closing the gap P4i-3a's own commit named: typing went into a
 * floating box first, not after the `$` a user is looking at. [invisibleDecorator] is what makes
 * that safe: the field still owns real focus/IME/selection state, it just paints nothing, so a tap
 * anywhere in the terminal is a tap on this field.
 *
 * Focused the moment this screen is on it, the way a real terminal is the instant it has the
 * window - `RenameFileDialog`'s own reason applies here too: without it the first keystroke goes
 * nowhere and costs a tap nobody should need to make.
 *
 * Cleared on Enter rather than on every character: multi-line by default, so Enter inserts `\n`
 * like any other character rather than being intercepted as an IME "Done" action - one path
 * instead of two for [reportTerminalKeystroke] to cover, and it is what lets backspace correct a
 * line before it is sent, the way a shell's own canonical-mode editing expects to be driven.
 */
@Composable
private fun TerminalInputField(
  onTypedText: (String) -> Unit,
  onEncodedKey: (String) -> Unit,
  applicationCursorKeys: () -> Boolean,
) {
  val state = remember { TextFieldState() }
  val field = remember { FocusRequester() }
  LaunchedEffect(Unit) { field.requestFocus() }

  val report by rememberUpdatedState(onTypedText)
  LaunchedEffect(state) {
    var previous = ""
    // `drop(1)`: the first emission is `snapshotFlow`'s own read of the initial (empty) text,
    // `CellSourceTracking.reportCellEdits`'s own reason for the same drop - not an edit to report.
    snapshotFlow { state.text.toString() }
      .drop(1)
      .collect { current -> previous = reportTerminalKeystroke(state, previous, current, report) }
  }

  BasicTextField(
    state = state,
    // A terminal is not a text field and the IME must not treat it as one: on this repo's tablet
    // (a Samsung), Gboard's automatic quote-completion was measured inserting a `'` the user never
    // typed into `python3 -c 'import json, sys'`, and the diff below faithfully forwarded it.
    // `autoCorrectEnabled = false` alone clears only
    // `TYPE_TEXT_FLAG_AUTO_CORRECT`. `KeyboardType.Password` is the Compose lever for the inputType
    // the reference uses for exactly this - `TerminalView.onCreateInputConnection` sets
    // `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS` for Samsung keyboards
    // that ignore `TYPE_NULL` (termux-app terminal-view/.../TerminalView.java:307-334, their #686).
    // The field is invisible, so password masking shows nothing and costs nothing.
    keyboardOptions =
    KeyboardOptions(
      capitalization = KeyboardCapitalization.None,
      autoCorrectEnabled = false,
      keyboardType = KeyboardType.Password,
    ),
    textStyle = codeSourceStyle(),
    // Belt and braces alongside `invisibleDecorator`: even if a decorator ever did call through,
    // a transparent brush keeps the cursor bar itself from painting.
    cursorBrush = SolidColor(Color.Transparent),
    decorator = invisibleDecorator,
    modifier = Modifier.fillMaxSize()
      .imePadding()
      .focusRequester(field)
      .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val bytes =
          terminalKeystroke(event.key, event.isCtrlPressed, event.nativeKeyEvent.keyCode, applicationCursorKeys())
            ?: return@onPreviewKeyEvent false
        onEncodedKey(bytes)
        true
      }
      .semantics { testTagsAsResourceId = true }
      .testTag(TERMINAL_INPUT_TAG),
  )
}

/**
 * Diffs [state]'s current text against [previous] into keystrokes via [onType], then clears
 * [state] once its text ends in a newline - [TerminalInputField]'s own multi-line-by-default rule.
 * A plain function rather than inline in the `LaunchedEffect` above, so [PythonTypingRulesTest]'s
 * own reason applies here too: a real [TextFieldState] proves what the buffer looks like after an
 * edit without a Compose test harness for it.
 *
 * @return what [previous] should be compared against next time.
 */
internal fun reportTerminalKeystroke(
  state: TextFieldState,
  previous: String,
  current: String,
  onType: (String) -> Unit,
): String {
  sendTerminalDiff(from = previous, to = current, onType = onType)
  if (!current.endsWith("\n")) return current
  state.edit { replace(0, length, "") }
  return ""
}

/**
 * What one field edit means on the wire: delete back to the longest common prefix of [from] and
 * [to] - one DEL (`\u007F`, the byte most shells' `stty erase` answers to) per character removed -
 * then type whatever [to] adds past that prefix. A pure append sends no DELs and a pure deletion
 * sends no text, so the two easy cases fall out of the one rule; the case P4j-1 left unsent - an
 * edit that rewrites a token mid-line - is now a delete-and-retype rather than a silent drop that
 * drifts the field and the shell apart (where a
 * Samsung IME's quote-completion was measured doing exactly that). [TerminalInputField] also asks
 * the IME not to rewrite the buffer in the first place (`KeyboardType.Password`); this is the
 * backstop for whatever still gets through.
 */
private fun sendTerminalDiff(from: String, to: String, onType: (String) -> Unit) {
  val shared = from.commonPrefixWith(to).length
  if (from.length > shared) onType("\u007F".repeat(from.length - shared))
  if (to.length > shared) onType(to.substring(shared))
}

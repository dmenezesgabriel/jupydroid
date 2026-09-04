package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.codeSourceStyle
import kotlinx.coroutines.flow.drop

/** So a Compose test - and a Maestro flow, through `testTagsAsResourceId` - can reach the field. */
const val TERMINAL_INPUT_TAG = "terminal-input"

/**
 * A shell, reachable, typeable and drawn faithfully - `docs/PLAN.md`'s P4i, sub-steps 1 and 2:
 * batched-run glyphs and a block cursor on a real [TerminalCanvas], the VS Code palette resolved
 * to the system's own dark/light theme, and a cell measured on this device rather than restated.
 *
 * ```
 * TerminalPaneScreen(session = TerminalSession("1"), terminalGateway, onBack = { backStack.removeLastOrNull() })
 * ```
 */
@Composable
fun TerminalPaneScreen(
  session: TerminalSession,
  terminalGateway: TerminalGateway,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  // Read here, not inside the factory lambda below - `isSystemInDarkTheme` is itself composable
  // and the factory runs once, outside recomposition, the way `viewModel {}` always does.
  darkTheme: Boolean = isSystemInDarkTheme(),
  viewModel: TerminalPaneViewModel = viewModel {
    val palette = if (darkTheme) TerminalPalette.VS_CODE_DARK else TerminalPalette.VS_CODE_LIGHT
    TerminalPaneViewModel(session, terminalGateway, palette = palette)
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  KeepTheShellAttached(viewModel)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { TerminalBar(title = state.title, onBack = onBack) },
  ) { padding ->
    // A `Box`, not a `Column` with the field docked below it - `docs/PLAN.md`'s P4j-1. The two
    // layers share the same bounds: `TerminalCanvas` draws every glyph and the block cursor, and
    // `TerminalInputField` sits over the whole of it so a tap anywhere in the terminal is a tap on
    // the one thing that receives input, the way Termux's `TerminalView` is both at once.
    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
      // Keyed on the redraw counter: `emulator.screen.grid` is mutated in place by
      // `TerminalPaneViewModel`, which Compose cannot observe on its own - `redraw` is the `State`
      // that stands in for it, exactly as `EditorUiState.sourceRevisions` stands in for a text
      // field Compose does not own either.
      key(state.redraw) {
        TerminalCanvas(
          emulator = viewModel.emulator,
          onGridSizeChanged = { grid, cell -> viewModel.resize(grid, cell.width, cell.height) },
          modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        )
      }
      TerminalInputField(onType = viewModel::type)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalBar(title: String?, onBack: () -> Unit) {
  TopAppBar(
    title = { Text(title ?: "Terminal") },
    navigationIcon = {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
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
 * The minimal keyboard capture this sub-step's gate needs: printable characters and Enter,
 * forwarded to [onType] verbatim (`TerminalGateway.send`'s own contract - `"ls\n"` runs `ls`).
 *
 * Full key encoding - arrows, Ctrl, latching modifiers, an extra-keys row built on `SymbolRow`'s
 * pattern - is `docs/PLAN.md`'s P4j-2/P4j-3, not this.
 *
 * Sized to fill the terminal area it sits over ([TerminalPaneScreen]'s `Box`), not a strip below
 * it - `docs/PLAN.md`'s P4j-1, closing the gap P4i-3a's own commit named: typing went into a
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
private fun TerminalInputField(onType: (String) -> Unit) {
  val state = remember { TextFieldState() }
  val field = remember { FocusRequester() }
  LaunchedEffect(Unit) { field.requestFocus() }

  val report by rememberUpdatedState(onType)
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
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
    textStyle = codeSourceStyle(),
    // Belt and braces alongside `invisibleDecorator`: even if a decorator ever did call through,
    // a transparent brush keeps the cursor bar itself from painting.
    cursorBrush = SolidColor(Color.Transparent),
    decorator = invisibleDecorator,
    modifier = Modifier.fillMaxSize()
      .imePadding()
      .focusRequester(field)
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
 * What one field edit means on the wire: an appended suffix is typed text, a shortened prefix is
 * that many backspaces - DEL (`\u007F`), the byte most shells' `stty erase` answers to today -
 * and anything else (autocorrect rewriting more than the tail) is not sent at all rather than
 * guessed at, which is this sub-step's deliberately minimal scope; P4j-2's own key encoder is where
 * a fuller edit-to-bytes mapping belongs.
 */
private fun sendTerminalDiff(from: String, to: String, onType: (String) -> Unit) {
  when {
    to.length > from.length && to.startsWith(from) -> onType(to.removePrefix(from))
    to.length < from.length && from.startsWith(to) -> onType("\u007F".repeat(from.length - to.length))
  }
}

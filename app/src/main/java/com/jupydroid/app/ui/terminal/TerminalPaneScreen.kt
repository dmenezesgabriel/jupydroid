package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.codeSourceStyle
import com.jupydroid.app.ui.editor.codeTextStyle

/** So a Compose test - and a Maestro flow, through `testTagsAsResourceId` - can reach the field. */
const val TERMINAL_INPUT_TAG = "terminal-input"

/**
 * A shell, drawn plainly: `docs/PLAN.md`'s P4i, sub-step 1 - reachable and typeable, with the
 * faithful glyph-and-cursor rendering of sub-step 2 still to come.
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

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { TerminalBar(title = state.title, onBack = onBack) },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
      // Keyed on the redraw counter: `emulator.screen.grid` is mutated in place by
      // `TerminalPaneViewModel`, which Compose cannot observe on its own - `redraw` is the `State`
      // that stands in for it, exactly as `EditorUiState.sourceRevisions` stands in for a text
      // field Compose does not own either.
      key(state.redraw) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.md)) {
          terminalScreenLines(viewModel).forEach { line -> Text(text = line, style = codeTextStyle()) }
        }
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
 * Every row the emulator currently holds, one [Text] each - the plain-text stand-in sub-step 2
 * replaces with cells drawn on a `Canvas`. One node per row rather than one blob for two real
 * reasons, not merely a Maestro convenience: TalkBack would otherwise read a whole screenful as
 * one utterance, and `TerminalRow.spaceUsed` is the row's full column width from construction
 * (`RowText.kt`) - trimmed here, since column-width padding is a rendering concern this stand-in
 * does not have yet and sub-step 2's `Canvas` is where it belongs.
 */
private fun terminalScreenLines(viewModel: TerminalPaneViewModel): List<String> {
  val screen = viewModel.emulator.screen
  return (0 until screen.rows).map { row ->
    screen.grid.rowAt(row).let { String(it.text, 0, it.spaceUsed) }.trimEnd()
  }
}

/**
 * The minimal keyboard capture this sub-step's gate needs: printable characters and Enter,
 * forwarded to [onType] verbatim (`TerminalGateway.send`'s own contract - `"ls\n"` runs `ls`).
 *
 * Full key encoding - arrows, Ctrl, latching modifiers, an extra-keys row built on `SymbolRow`'s
 * pattern - is `docs/PLAN.md`'s P4j, not this. The field keeps whatever the shell hands back on
 * screen elsewhere; what this diffs against is only what the field itself last held, so a change
 * is read as *typed* rather than compared to the terminal's own output.
 *
 * Focused the moment this screen is on it, the way a real terminal is the instant it has the
 * window - `RenameFileDialog`'s own reason applies here too: without it the first keystroke goes
 * nowhere and costs a tap nobody should need to make.
 *
 * Cleared on Enter rather than on every character: multi-line by default, so Enter inserts `\n`
 * like any other character rather than being intercepted as an IME "Done" action - one path
 * instead of two for [sendTerminalDiff] to cover, and it is what lets backspace correct a line
 * before it is sent, the way a shell's own canonical-mode editing expects to be driven.
 *
 * `imePadding` sits on the field itself, not a shared ancestor - `SymbolRow`/`NotebookBar`'s own
 * precedent, both of which pad only the bar that would otherwise sit behind the keyboard. Padding
 * the whole screen instead pulled the output column's own height budget negative, which read as a
 * production defect until the field moved here made it a hierarchy accident.
 */
@Composable
private fun TerminalInputField(onType: (String) -> Unit) {
  var draft by remember { mutableStateOf("") }
  val field = remember { FocusRequester() }
  LaunchedEffect(Unit) { field.requestFocus() }
  BasicTextField(
    value = draft,
    onValueChange = { typed ->
      sendTerminalDiff(from = draft, to = typed, onType = onType)
      draft = if (typed.endsWith("\n")) "" else typed
    },
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
    // `codeTextStyle()` alone draws black here regardless of theme - `EditorTheme.kt`'s own
    // documented `BasicTextField` pitfall (`codeSourceStyle`'s KDoc). Measured on this screen too:
    // the field composed and took input, but nothing typed into it was ever visible.
    textStyle = codeSourceStyle(),
    modifier = Modifier.fillMaxWidth()
      .padding(Spacing.md)
      .imePadding()
      .focusRequester(field)
      .semantics { testTagsAsResourceId = true }
      .testTag(TERMINAL_INPUT_TAG),
  )
}

/**
 * What one field edit means on the wire: an appended suffix is typed text, a shortened prefix is
 * that many backspaces - DEL (`\u007F`), the byte most shells' `stty erase` answers to today -
 * and anything else (autocorrect rewriting more than the tail) is not sent at all rather than
 * guessed at, which is this sub-step's deliberately minimal scope; P4j's own key encoder is where
 * a fuller edit-to-bytes mapping belongs.
 */
private fun sendTerminalDiff(from: String, to: String, onType: (String) -> Unit) {
  when {
    to.length > from.length && to.startsWith(from) -> onType(to.removePrefix(from))
    to.length < from.length && from.startsWith(to) -> onType("\u007F".repeat(from.length - to.length))
  }
}

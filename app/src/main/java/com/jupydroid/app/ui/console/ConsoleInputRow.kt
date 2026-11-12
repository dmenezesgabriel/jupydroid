package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jupydroid.app.domain.model.IndentUnit
import com.jupydroid.app.theme.FocusRingIconButton
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.CodeCellField
import com.jupydroid.app.ui.editor.FocusedCellInput
import com.jupydroid.app.ui.editor.RECALL_NEXT_ENTRY
import com.jupydroid.app.ui.editor.RECALL_PREVIOUS_ENTRY
import com.jupydroid.app.ui.editor.SymbolRowWhileTyping
import com.jupydroid.app.ui.editor.cellEditorBox
import com.jupydroid.app.ui.editor.cellRunIcon

/** Locates the input field in a test, the same role `EVALUATE_FIELD_TAG` plays for the debugger's. */
const val CONSOLE_INPUT_FIELD_TAG = "console-input-field"

/** What a screen reader calls the Console's input field. */
internal const val CONSOLE_INPUT_NAME = "Console input"

/**
 * Where a Console entry is typed and run.
 *
 * `Enter` is left doing exactly what [CodeCellField] already does with it - insert a newline - and
 * [onSubmit] is the dedicated action standing in for `Shift Enter`, per the design decision
 * `ConsoleViewModel`'s own doc comment records. [running] disables it the way `RunControl`'s own
 * `QUEUED` branch disables Run rather than drawing a spinner nothing here can cancel yet - a run
 * in progress is stopped from [ConsoleActionsMenu]'s own Interrupt Kernel row instead, matching
 * where both references put it.
 *
 * No line-number gutter: unlike a notebook's persistent cell, a submitted entry has no line a
 * breakpoint or a completion popup's gutter offset needs to survive past this one run.
 *
 * **[CodeCellField] is wrapped in its own weighted `Box`, not handed `Modifier.weight(1f)`
 * directly.** `CodeCellField`'s own root is an unmodified `Box` around the field it actually
 * measures - the caller's modifier reaches the [androidx.compose.foundation.text.BasicTextField]
 * three layers down, not the node `RowScope.weight` reads parent data from, so a weight passed
 * straight through was silently inert: the field measured to its own intrinsic (near-zero, empty)
 * width and every pixel past it sat unclaimed between the caret and Run - visible in a screenshot
 * taken before this row existed to explain it. [cellEditorBox] is the same border,
 * background and padding the notebook's own cell editor draws (`CellEditorBox.kt`), which an
 * unstyled `BasicTextField` drew none of - together the two are why the field was reachable but
 * invisible.
 *
 * **The history pair is unconditional, unlike [com.jupydroid.app.ui.editor.SymbolRow]'s own**:
 * the reference binds a console's history walk to `edgeRequested` on the prompt editor itself
 * (`console/src/history.ts:103-110`), with no settings gate at all - only the notebook's
 * `Alt ArrowUp`/`ArrowDown` pair is opt-in (`accessKernelHistory`). There is no reliable "caret at
 * the top of the field" gesture on a soft keyboard either, so two buttons stand in for the edge
 * request the same way the Run button already stands in for Shift+Enter - not a port of the
 * notebook's own opt-in row. For the same reason the symbol row this publishes while the prompt has
 * the caret carries no second pair.
 *
 * ```kotlin
 * ConsoleInputRow(
 *   state = viewModel.input,
 *   running = lastEntryIsRunning,
 *   onSubmit = viewModel::submit,
 *   onSourceEdited = viewModel::inputEdited,
 *   onRecallPreviousEntry = viewModel::recallPreviousEntry,
 *   onRecallNextEntry = viewModel::recallNextEntry,
 * )
 * ```
 */
@Composable
fun ConsoleInputRow(
  state: TextFieldState,
  running: Boolean,
  onSubmit: () -> Unit,
  onSourceEdited: (String) -> Unit = {},
  onRecallPreviousEntry: () -> Unit = {},
  onRecallNextEntry: () -> Unit = {},
  /** `fileeditor:toggle-…-universal` covers the Console - the notebook's own pair, passed down. */
  autoClosingBrackets: Boolean = true,
  indentUnit: IndentUnit = IndentUnit.SPACES_4,
  /** `EditorSettings.symbolRowShown`, the same preference the notebook's cells read. */
  symbolRowShown: Boolean = true,
  modifier: Modifier = Modifier,
) {
  // What the symbol row types into; its pair is set straight, as `EditorScreen.kt` sets the notebook's.
  val input = remember { FocusedCellInput() }
  input.autoClosingBrackets = autoClosingBrackets
  input.indentUnit = indentUnit
  Column(modifier = modifier) {
    Row(modifier = Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.Bottom) {
      FocusRingIconButton(onClick = onRecallPreviousEntry) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = RECALL_PREVIOUS_ENTRY)
      }
      FocusRingIconButton(onClick = onRecallNextEntry) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = RECALL_NEXT_ENTRY)
      }
      // The weighted node `Row` actually reads parent data from - see this function's own doc
      // comment for why `CodeCellField`'s modifier could not carry `weight` itself.
      var focused by remember { mutableStateOf(false) }
      Box(modifier = Modifier.weight(1f)) {
        CodeCellField(
          state = state,
          onSourceEdited = onSourceEdited,
          // `RenameFileDialog`'s own reason: an empty field with no placeholder has nothing a
          // Maestro flow can tap by visible text, unlike a notebook cell that always has `[ ]:` or
          // a source line to land on.
          modifier = Modifier.fillMaxWidth()
            .cellEditorBox(editing = focused)
            .onFocusChanged { focused = it.isFocused }
            .semantics { testTagsAsResourceId = true }
            .testTag(CONSOLE_INPUT_FIELD_TAG),
          lineNumbersShown = false,
          autoClosingBrackets = autoClosingBrackets,
          indentUnit = indentUnit,
          input = input,
          fieldName = CONSOLE_INPUT_NAME,
        )
      }
      FocusRingIconButton(onClick = onSubmit, enabled = !running) {
        Icon(cellRunIcon, contentDescription = "Run")
      }
    }
    SymbolRowWhileTyping(input, symbolRowShown)
  }
}

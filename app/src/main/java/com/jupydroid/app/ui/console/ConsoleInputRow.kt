package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jupydroid.app.ui.editor.CodeCellField
import com.jupydroid.app.ui.editor.RECALL_NEXT_ENTRY
import com.jupydroid.app.ui.editor.RECALL_PREVIOUS_ENTRY
import com.jupydroid.app.ui.editor.cellRunIcon

/** Locates the input field in a test, the same role `EVALUATE_FIELD_TAG` plays for the debugger's. */
const val CONSOLE_INPUT_FIELD_TAG = "console-input-field"

/**
 * Where a Console entry is typed and run - P6b-4b.
 *
 * `Enter` is left doing exactly what [CodeCellField] already does with it - insert a newline - and
 * [onSubmit] is the dedicated action standing in for `Shift Enter`, per the design decision
 * `ConsoleViewModel`'s own doc comment records. [running] disables it the way `RunControl`'s own
 * `QUEUED` branch disables Run rather than drawing a spinner nothing here can cancel yet -
 * there is no interrupt wired to a Console (`docs/PLAN.md`'s own P6b-4a row).
 *
 * No line-number gutter: unlike a notebook's persistent cell, a submitted entry has no line a
 * breakpoint or a completion popup's gutter offset needs to survive past this one run.
 *
 * **The history pair is unconditional, unlike [com.jupydroid.app.ui.editor.SymbolRow]'s own**:
 * the reference binds a console's history walk to `edgeRequested` on the prompt editor itself
 * (`console/src/history.ts:103-110`), with no settings gate at all - only the notebook's
 * `Alt ArrowUp`/`ArrowDown` pair is opt-in (`accessKernelHistory`). There is no reliable "caret at
 * the top of the field" gesture on a soft keyboard either, so two buttons stand in for the edge
 * request the same way the Run button already stands in for Shift+Enter - not a port of the
 * notebook's own opt-in row.
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
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
    IconButton(onClick = onRecallPreviousEntry) {
      Icon(Icons.Filled.KeyboardArrowUp, contentDescription = RECALL_PREVIOUS_ENTRY)
    }
    IconButton(onClick = onRecallNextEntry) {
      Icon(Icons.Filled.KeyboardArrowDown, contentDescription = RECALL_NEXT_ENTRY)
    }
    CodeCellField(
      state = state,
      onSourceEdited = onSourceEdited,
      // `RenameFileDialog`'s own reason: an empty field with no placeholder has nothing a Maestro
      // flow can tap by visible text, unlike a notebook cell that always has `[ ]:` or a source
      // line to land on.
      modifier = Modifier.weight(1f)
        .semantics { testTagsAsResourceId = true }
        .testTag(CONSOLE_INPUT_FIELD_TAG),
      lineNumbersShown = false,
    )
    IconButton(onClick = onSubmit, enabled = !running) {
      Icon(cellRunIcon, contentDescription = "Run")
    }
  }
}

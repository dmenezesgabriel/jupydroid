package com.jupydroid.app.ui.console

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jupydroid.app.ui.editor.CodeCellField
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
 * ```kotlin
 * ConsoleInputRow(state = viewModel.input, running = lastEntryIsRunning, onSubmit = viewModel::submit)
 * ```
 */
@Composable
fun ConsoleInputRow(state: TextFieldState, running: Boolean, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
    CodeCellField(
      state = state,
      onSourceEdited = {},
      modifier = Modifier.weight(1f).testTag(CONSOLE_INPUT_FIELD_TAG),
      lineNumbersShown = false,
    )
    IconButton(onClick = onSubmit, enabled = !running) {
      Icon(cellRunIcon, contentDescription = "Run")
    }
  }
}

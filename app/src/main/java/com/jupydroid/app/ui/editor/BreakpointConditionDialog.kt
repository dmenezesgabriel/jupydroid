package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A breakpoint's own condition, reached by a long-press on an armed line's gutter dot -
 * [RenameFileDialog][com.jupydroid.app.ui.RenameFileDialog]'s own shape (a single `OutlinedTextField`
 * inside an `AlertDialog`, focused the moment it opens), applied to a Python expression instead of
 * a file name.
 *
 * **Clearing the field and confirming removes the condition** rather than needing a second
 * "remove" affordance: an empty expression is not a condition DAP could evaluate either, so the
 * text field's own emptiness already says what the user means.
 *
 * ```
 * BreakpointConditionDialog(
 *   currentCondition = breakpoint.condition,
 *   onConfirm = { condition -> viewModel.debugger.setCondition(cellId, line, condition) },
 *   onDismiss = { prompt = null },
 * )
 * ```
 */
@Composable
fun BreakpointConditionDialog(currentCondition: String?, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
  var condition by remember(currentCondition) { mutableStateOf(TextFieldValue(currentCondition.orEmpty())) }
  val field = remember { FocusRequester() }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Breakpoint condition") },
    text = {
      OutlinedTextField(
        value = condition,
        onValueChange = { condition = it },
        modifier = Modifier.focusRequester(field),
        singleLine = true,
        label = { Text("Expression") },
        placeholder = { Text("e.g. x > 10") },
        // The IME's own default sentence-capitalization otherwise turns a condition beginning
        // with a lowercase identifier into one Python no longer recognizes - measured live on the
        // tablet, not guessed: the first Maestro run of P6a-16's own flow sent debugpy a literal
        // "I == 3" for a typed "i == 3", so the kernel never once found the name and the loop ran
        // to completion unconditioned. `CodeCellField` needs no such override, being multi-line
        // and rarely typed into starting at its own first character; a single-line expression
        // field is exactly that case every time.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
      )
      // Same reason RenameFileDialog's own field requests it here rather than beside the
      // AlertDialog call: the dialog's content is a composition of its own in its own window, so
      // an effect outside it runs before the field it points at is attached.
      LaunchedEffect(Unit) { field.requestFocus() }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(condition.text.trim().ifBlank { null }) }) { Text("Set") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

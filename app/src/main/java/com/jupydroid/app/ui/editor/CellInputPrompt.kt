package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.jupydroid.app.domain.model.KernelInputRequest

/**
 * The field a cell blocked in `input()` or `getpass()` is answered from, drawn at the foot of its
 * output area - JupyterLab's `Stdin` widget (`jupyterlab@7f0e690 packages/outputarea/src/widget.ts`),
 * and inline for the reason ADR-0082 gives: a modal would cover the cell that asked.
 *
 * Done submits whatever is typed, empty included - `input()` returning `''` is an answer. The field
 * takes focus when it appears, as the reference's does, so the keyboard is already up for it.
 *
 * ```
 * InputPromptField(KernelInputRequest("Name?", password = false, requestHeader = "{}"), onSubmit = { reply(it) })
 * ```
 */
@Composable
internal fun InputPromptField(request: KernelInputRequest, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
  var answer by remember(request) { mutableStateOf(TextFieldValue("")) }
  val focus = remember { FocusRequester() }
  LaunchedEffect(request) { focus.requestFocus() }
  OutlinedTextField(
    value = answer,
    onValueChange = { answer = it },
    modifier = modifier.focusRequester(focus).semantics { contentDescription = INPUT_PROMPT_DESCRIPTION },
    singleLine = true,
    label = { Text(request.prompt) },
    visualTransformation = if (request.password) PasswordVisualTransformation() else VisualTransformation.None,
    // What is typed goes to a program verbatim, so the IME must not capitalise or correct it.
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.None,
      autoCorrectEnabled = false,
      keyboardType = if (request.password) KeyboardType.Password else KeyboardType.Text,
      imeAction = ImeAction.Done,
    ),
    keyboardActions = KeyboardActions(onDone = { onSubmit(answer.text) }),
  )
}

/** What Maestro and the instrumented tests find the field by. */
internal const val INPUT_PROMPT_DESCRIPTION = "Input prompt"

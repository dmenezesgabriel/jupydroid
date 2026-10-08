package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import com.jupydroid.app.domain.model.IDLE_SHUTDOWN_MINUTES_RANGE
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.model.THREAD_CAP_RANGE
import com.jupydroid.app.syntax.ShellSyntax
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.CodeCellField
import com.jupydroid.app.ui.editor.cellEditorBox

/** The idle-shutdown field's tag, for a test and - as a resource id - for a Maestro flow. */
const val IDLE_SHUTDOWN_FIELD_TAG = "space-idle-shutdown-field"

/** The thread-cap field's tag, for a test and - as a resource id - for a Maestro flow. */
const val THREAD_CAP_FIELD_TAG = "space-thread-cap-field"

/** The startup-script field's tag, for a test and - as a resource id - for a Maestro flow. */
const val STARTUP_SCRIPT_FIELD_TAG = "space-startup-script-field"

/** The dialog's accept button. */
internal const val SAVE_SETTINGS = "Save"

/**
 * Edits one stopped Space's [SpaceSettings], opened from its row's Settings on the home.
 *
 * A dialog, as Rename and Delete are, rather than Winlator's detail screen
 * (`winlator@fb66541 app/src/main/java/com/winlator/ContainerDetailFragment.java`): it is reached
 * from the same row and changes the same record. **Save is held for any value the port would
 * refuse** - [IDLE_SHUTDOWN_MINUTES_RANGE] and [THREAD_CAP_RANGE] are the port's own ranges and
 * [refusals] the port's own variable rules, so the two cannot disagree - and what it hands [onSave]
 * is [space]'s settings with the edited fields changed. [environments] are shown, not edited: what
 * the Space's workspace holds, null while they are being read.
 *
 * ```
 * SpaceSettingsDialog(
 *   space, state.environments, onSave = { viewModel.configure(space.id, it) }, onDismiss = { editing = null },
 * )
 * ```
 */
@Composable
fun SpaceSettingsDialog(
  space: SpaceConfig,
  environments: List<PythonEnvironment>? = null,
  onSave: (SpaceSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  var idleText by remember(space) { mutableStateOf(space.settings.idleShutdownMinutes.toString()) }
  var capText by remember(space) { mutableStateOf(space.settings.threadCap?.toString().orEmpty()) }
  var variableRows by remember(space) { mutableStateOf(variableRowsOf(space.settings.environmentVariables)) }
  val script = remember(space) { TextFieldState(space.settings.startupScript) }
  val idleMinutes = idleText.trim().toIntOrNull()?.takeIf { it in IDLE_SHUTDOWN_MINUTES_RANGE }
  val threadCap = threadCapOf(capText)
  val savable = idleMinutes?.takeIf { threadCap.isSuccess && variableRows.refusals().all { it == null } }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Settings · ${space.name} (${space.id})") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        SettingsNumberField(idleText, idleMinutes == null, IDLE_SHUTDOWN_LABEL, onChange = { idleText = it })
        SettingsNumberField(capText, threadCap.isFailure, THREAD_CAP_LABEL, onChange = { capText = it })
        SpaceVariablesEditor(variableRows, onRowsChange = { variableRows = it })
        StartupScriptField(script)
        SpaceEnvironmentsSection(environments)
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          savable?.let {
            onSave(
              space.settings.copy(
                idleShutdownMinutes = it,
                environmentVariables = variableRows.toVariables(),
                threadCap = threadCap.getOrNull(),
                startupScript = script.text.toString(),
              ),
            )
          }
        },
        enabled = savable != null,
      ) {
        Text(SAVE_SETTINGS)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/**
 * The cap [capText] asks for: null when blank, which leaves the libraries their default, and a
 * failure when it is neither blank nor a number in [THREAD_CAP_RANGE].
 */
internal fun threadCapOf(capText: String): Result<Int?> {
  val trimmed = capText.trim()
  val cap = trimmed.toIntOrNull()?.takeIf { it in THREAD_CAP_RANGE }
  return when {
    trimmed.isEmpty() -> Result.success(null)
    cap != null -> Result.success(cap)
    else -> Result.failure(IllegalArgumentException("thread cap '$trimmed'; expected $THREAD_CAP_RANGE or blank"))
  }
}

/** What one number field of the dialog is called, says under itself, and is tagged as. */
private class SettingsNumberLabel(val label: String, val hint: String, val tag: String)

/** Said under the field whatever was typed, so the range and what `0` means are never a guess. */
private val IDLE_SHUTDOWN_LABEL = SettingsNumberLabel(
  label = "Idle shutdown (minutes)",
  hint = "${IDLE_SHUTDOWN_MINUTES_RANGE.first}-${IDLE_SHUTDOWN_MINUTES_RANGE.last}; 0 = never",
  tag = IDLE_SHUTDOWN_FIELD_TAG,
)

/** Blank rather than idle's `0` for "none": a cap of zero threads means nothing to any library. */
private val THREAD_CAP_LABEL = SettingsNumberLabel(
  label = "Thread cap",
  hint = "${THREAD_CAP_RANGE.first}-${THREAD_CAP_RANGE.last}; blank = library default",
  tag = THREAD_CAP_FIELD_TAG,
)

@Composable
private fun SettingsNumberField(
  text: String,
  isError: Boolean,
  label: SettingsNumberLabel,
  onChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = text,
    onValueChange = onChange,
    isError = isError,
    label = { Text(label.label) },
    supportingText = { Text(label.hint) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(label.tag),
  )
}

/**
 * Shell, in a code cell's own field - numbered, with its symbol row, and coloured by [ShellSyntax] -
 * framed as a cell is; said under itself when it runs and what a failure does. No typing rules, as
 * the file editor gives a `.sh` file none, and no completions, since no kernel is there to ask.
 */
@Composable
internal fun StartupScriptField(script: TextFieldState) {
  var focused by remember { mutableStateOf(false) }
  Column(Modifier.padding(top = Spacing.sm)) {
    Text("Startup script", style = MaterialTheme.typography.labelLarge)
    CodeCellField(
      state = script,
      onSourceEdited = {},
      highlighter = remember { ShellSyntax() },
      typing = InputTransformation { },
      completionEnabled = false,
      modifier = Modifier.fillMaxWidth()
        .cellEditorBox(editing = focused)
        .onFocusChanged { focused = it.isFocused }
        .semantics { testTagsAsResourceId = true }
        .testTag(STARTUP_SCRIPT_FIELD_TAG),
    )
    Text(
      "sh, before the server on every start; a non-zero exit fails the start",
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

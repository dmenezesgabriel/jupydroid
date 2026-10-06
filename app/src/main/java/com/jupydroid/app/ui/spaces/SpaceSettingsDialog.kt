package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import com.jupydroid.app.domain.model.IDLE_SHUTDOWN_MINUTES_RANGE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings

/** The idle-shutdown field's tag, for a test and - as a resource id - for a Maestro flow. */
const val IDLE_SHUTDOWN_FIELD_TAG = "space-idle-shutdown-field"

/** The dialog's accept button. */
internal const val SAVE_SETTINGS = "Save"

/**
 * Edits one stopped Space's [SpaceSettings], opened from its row's Settings on the home.
 *
 * A dialog, as Rename and Delete are, rather than Winlator's detail screen
 * (`winlator@fb66541 app/src/main/java/com/winlator/ContainerDetailFragment.java`): it is reached
 * from the same row and changes the same record. **Save is held for any value the port would
 * refuse** - [IDLE_SHUTDOWN_MINUTES_RANGE] is the port's own range and [refusals] the port's own
 * variable rules, so the two cannot disagree - and what it hands [onSave] is [space]'s settings
 * with the edited fields changed.
 *
 * ```
 * SpaceSettingsDialog(space, onSave = { viewModel.configure(space.id, it) }, onDismiss = { editing = null })
 * ```
 */
@Composable
fun SpaceSettingsDialog(space: SpaceConfig, onSave: (SpaceSettings) -> Unit, onDismiss: () -> Unit) {
  var idleText by remember(space) { mutableStateOf(space.settings.idleShutdownMinutes.toString()) }
  var variableRows by remember(space) { mutableStateOf(variableRowsOf(space.settings.environmentVariables)) }
  val idleMinutes = idleText.trim().toIntOrNull()?.takeIf { it in IDLE_SHUTDOWN_MINUTES_RANGE }
  val savable = idleMinutes?.takeIf { variableRows.refusals().all { it == null } }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Settings · ${space.name} (${space.id})") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        IdleShutdownField(idleText, isError = idleMinutes == null, onChange = { idleText = it })
        SpaceVariablesEditor(variableRows, onRowsChange = { variableRows = it })
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          savable?.let {
            onSave(space.settings.copy(idleShutdownMinutes = it, environmentVariables = variableRows.toVariables()))
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

@Composable
private fun IdleShutdownField(idleText: String, isError: Boolean, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = idleText,
    onValueChange = onChange,
    isError = isError,
    label = { Text("Idle shutdown (minutes)") },
    supportingText = { Text(IDLE_SHUTDOWN_HINT) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag(IDLE_SHUTDOWN_FIELD_TAG),
  )
}

/** Said under the field whatever was typed, so the range and what `0` means are never a guess. */
private val IDLE_SHUTDOWN_HINT =
  "${IDLE_SHUTDOWN_MINUTES_RANGE.first}-${IDLE_SHUTDOWN_MINUTES_RANGE.last}; 0 = never"

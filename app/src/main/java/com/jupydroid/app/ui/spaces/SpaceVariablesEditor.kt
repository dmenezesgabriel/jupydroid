package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.Spacing
import com.jupydroid.extension.api.FocusRingIconButton
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.glyphBesideText

/** The button that appends an empty row. */
const val ADD_VARIABLE = "Add variable"

/** The plus beside [ADD_VARIABLE] at the default font scale: Material's own icon size. */
private val ADD_VARIABLE_GLYPH = 24.dp

/** The plus beside [ADD_VARIABLE], for a test that reads its size against the label's line. */
internal const val ADD_VARIABLE_GLYPH_TAG = "Add variable glyph"

/** Row [index]'s name field, for a test and - as a resource id - for a Maestro flow. */
fun variableNameTag(index: Int): String = "space-variable-name-$index"

/** Row [index]'s value field. */
fun variableValueTag(index: Int): String = "space-variable-value-$index"

/** What the remove button of the row named [name] announces. */
fun removeVariableLabel(name: String): String = "Remove variable $name"

/**
 * The Space's own variables as editable rows, each name field carrying its row's refusal.
 *
 * A name/value pair per row, VS Code's `terminal.integrated.env.*` editor shape and Winlator's
 * `AddEnvVarDialog` list (`winlator@fb66541 contentdialog/AddEnvVarDialog.java`), without Winlator's
 * no-spaces rule: that is its own parser's limit, and a process environment has no such limit.
 *
 * ```
 * SpaceVariablesEditor(rows, onRowsChange = { rows = it })
 * ```
 */
@Composable
fun SpaceVariablesEditor(rows: List<SpaceVariableRow>, onRowsChange: (List<SpaceVariableRow>) -> Unit) {
  val refusals = rows.refusals()
  Column {
    Text("Environment variables")
    rows.forEachIndexed { index, row ->
      SpaceVariableRowFields(
        index = index,
        row = row,
        refusal = refusals[index],
        onChange = { changed -> onRowsChange(rows.toMutableList().also { it[index] = changed }) },
        onRemove = { onRowsChange(rows.filterIndexed { at, _ -> at != index }) },
      )
    }
    FocusRingTextButton(onClick = { onRowsChange(rows + SpaceVariableRow("", "")) }) {
      val plus = LocalDensity.current.glyphBesideText(ADD_VARIABLE_GLYPH, MaterialTheme.typography.labelLarge.fontSize)
      Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(plus).testTag(ADD_VARIABLE_GLYPH_TAG))
      Text(ADD_VARIABLE)
    }
  }
}

@Composable
private fun SpaceVariableRowFields(
  index: Int,
  row: SpaceVariableRow,
  refusal: String?,
  onChange: (SpaceVariableRow) -> Unit,
  onRemove: () -> Unit,
) {
  Row(verticalAlignment = Alignment.Top) {
    OutlinedTextField(
      value = row.name,
      onValueChange = { onChange(row.copy(name = it)) },
      isError = refusal != null,
      label = { Text("Name") },
      supportingText = refusal?.let { { Text(it) } },
      singleLine = true,
      modifier = Modifier.weight(1f).semantics { testTagsAsResourceId = true }.testTag(variableNameTag(index)),
    )
    Spacer(Modifier.width(Spacing.sm))
    OutlinedTextField(
      value = row.value,
      onValueChange = { onChange(row.copy(value = it)) },
      label = { Text("Value") },
      singleLine = true,
      modifier = Modifier.weight(1f).semantics { testTagsAsResourceId = true }.testTag(variableValueTag(index)),
    )
    FocusRingIconButton(onClick = onRemove) {
      Icon(Icons.Filled.Close, contentDescription = removeVariableLabel(row.name))
    }
  }
}

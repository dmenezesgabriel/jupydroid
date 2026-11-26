package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.environments.EnvironmentRow
import com.jupydroid.app.ui.environments.NO_ENVIRONMENTS

/** The section's tag, for a test and - as a resource id - for a Maestro flow. */
const val SPACE_ENVIRONMENTS_TAG = "space-environments"

/**
 * The Python environments in a Space's workspace, as `SpaceEnvironments` found them: each by its
 * path, and for one without `ipykernel`, the command that adds it - selectable, so it can be copied
 * into the Space's terminal. One registered and deleted since says so, with the command that removes
 * its kernel - IntelliJ's `invalid` mark on an interpreter it cannot run (`intellij-community@406a27a
 * python/python-sdk/backend/src/com/jetbrains/python/sdk/impl/PySdkPresentationInfoImpl.kt`), with
 * the fix said where it runs. [environments] null is a read still in flight. Each unregistered row
 * offers Register, pressable only with ipykernel present, which hands [onRegister] its path;
 * [refusal] is the last one Settings' own acts met, shown here because the dialog covers the home.
 * The row itself is `ui/environments/EnvironmentRow.kt`'s, shared with the Environments panel.
 */
@Composable
internal fun SpaceEnvironmentsSection(
  environments: List<PythonEnvironment>?,
  onRegister: (String) -> Unit = {},
  refusal: String? = null,
) {
  Column(Modifier.padding(top = Spacing.sm).semantics { testTagsAsResourceId = true }.testTag(SPACE_ENVIRONMENTS_TAG)) {
    Text("Environments", style = MaterialTheme.typography.labelLarge)
    refusal?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    when {
      environments == null -> Text("Reading…", style = MaterialTheme.typography.bodySmall)
      environments.isEmpty() -> Text(NO_ENVIRONMENTS, style = MaterialTheme.typography.bodySmall)
      else -> environments.forEach { EnvironmentRow(it, onRegister) }
    }
  }
}

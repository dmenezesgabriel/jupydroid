package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.codeTextStyle

/** The section's tag, for a test and - as a resource id - for a Maestro flow. */
const val SPACE_ENVIRONMENTS_TAG = "space-environments"

/** Said under an environment a kernel can start in. */
internal const val HAS_IPYKERNEL = "ipykernel installed"

/** Said when the workspace holds no environment where the scan looks. */
internal const val NO_ENVIRONMENTS = "None found directly in ~ or in a folder like ~/envs"

/**
 * The Python environments in a Space's workspace, as `SpaceEnvironments` found them: each by its
 * path, and for one without `ipykernel`, the command that adds it - selectable, so it can be copied
 * into the Space's terminal. [environments] null is a read still in flight.
 */
@Composable
internal fun SpaceEnvironmentsSection(environments: List<PythonEnvironment>?) {
  Column(Modifier.padding(top = Spacing.sm).semantics { testTagsAsResourceId = true }.testTag(SPACE_ENVIRONMENTS_TAG)) {
    Text("Environments", style = MaterialTheme.typography.labelLarge)
    when {
      environments == null -> Text("Reading…", style = MaterialTheme.typography.bodySmall)
      environments.isEmpty() -> Text(NO_ENVIRONMENTS, style = MaterialTheme.typography.bodySmall)
      else -> environments.forEach { EnvironmentRow(it) }
    }
  }
}

@Composable
private fun EnvironmentRow(environment: PythonEnvironment) {
  Column(Modifier.padding(top = Spacing.xs)) {
    Text(environment.path, style = MaterialTheme.typography.bodyMedium)
    if (environment.hasIpykernel) {
      Text(HAS_IPYKERNEL, style = MaterialTheme.typography.bodySmall)
      return@Column
    }
    Text("No ipykernel, so no kernel can start in it. Add it with:", style = MaterialTheme.typography.bodySmall)
    SelectionContainer { Text(environment.ipykernelInstallCommand, style = codeTextStyle()) }
  }
}

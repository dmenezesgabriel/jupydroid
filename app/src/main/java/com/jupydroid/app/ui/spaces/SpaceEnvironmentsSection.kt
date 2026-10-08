package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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

/** Register's accessible name on the row of the environment at [path], for a test and a Maestro flow. */
internal fun registerLabel(path: String): String = "Register $path as kernel"

/** What a registered environment's row says in place of Register. */
internal fun registeredLabel(kernelName: String): String = "Registered as kernel $kernelName"

/**
 * The Python environments in a Space's workspace, as `SpaceEnvironments` found them: each by its
 * path, and for one without `ipykernel`, the command that adds it - selectable, so it can be copied
 * into the Space's terminal. [environments] null is a read still in flight. Each unregistered row
 * offers Register, pressable only with ipykernel present, which hands [onRegister] its path;
 * [refusal] is the last one Settings' own acts met, shown here because the dialog covers the home.
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

@Composable
private fun EnvironmentRow(environment: PythonEnvironment, onRegister: (String) -> Unit) {
  Column(Modifier.padding(top = Spacing.xs)) {
    Text(environment.path, style = MaterialTheme.typography.bodyMedium)
    if (environment.hasIpykernel) {
      Text(HAS_IPYKERNEL, style = MaterialTheme.typography.bodySmall)
    } else {
      Text("No ipykernel, so no kernel can start in it. Add it with:", style = MaterialTheme.typography.bodySmall)
      SelectionContainer { Text(environment.ipykernelInstallCommand, style = codeTextStyle()) }
    }
    environment.registeredAs?.let {
      Text(registeredLabel(it), style = MaterialTheme.typography.bodySmall)
      return@Column
    }
    TextButton(
      onClick = { onRegister(environment.path) },
      enabled = environment.hasIpykernel,
      modifier = Modifier.semantics { contentDescription = registerLabel(environment.path) },
    ) {
      Text("Register as kernel")
    }
  }
}

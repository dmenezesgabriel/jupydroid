package com.jupydroid.app.ui.environments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.editor.codeTextStyle
import com.jupydroid.extension.api.FocusRingTextButton

/** Said under an environment a kernel can start in. */
internal const val HAS_IPYKERNEL = "ipykernel installed"

/** Said when the workspace holds no environment where the scan looks. */
internal const val NO_ENVIRONMENTS = "None found in ~, a folder like ~/envs, or a project folder"

/** Register's accessible name on the row of the environment at [path], for a test and a Maestro flow. */
internal fun registerLabel(path: String): String = "Register $path as kernel"

/**
 * What the row of a registered environment deleted since says: its spec still names it, which the
 * catalogue no longer offers, and making it again where it was brings the kernel back.
 */
internal fun goneLabel(kernelName: String): String =
  "Gone. Kernel $kernelName still names it: make it here again and the kernel comes back, or remove the kernel with:"

/** What a registered environment's row says in place of Register. */
internal fun registeredLabel(kernelName: String): String = "Registered as kernel $kernelName"

/**
 * One environment's row, shared by the Space's Settings section and the Environments panel: its
 * path, whether a kernel can start in it - and for one that cannot, the command that fixes it,
 * selectable so it can be pasted into the Space's terminal - then Register, pressable only with
 * ipykernel present, which hands [onRegister] its path. A registered-and-deleted-since row says so
 * with the command that removes its kernel instead - IntelliJ's `invalid` mark on an interpreter
 * it cannot run (`intellij-community@406a27a
 * python/python-sdk/backend/src/com/jetbrains/python/sdk/impl/PySdkPresentationInfoImpl.kt`), with
 * the fix said where it runs.
 */
@Composable
internal fun EnvironmentRow(environment: PythonEnvironment, onRegister: (String) -> Unit) {
  Column(Modifier.padding(top = Spacing.xs)) {
    Text(environment.path, style = MaterialTheme.typography.bodyMedium)
    if (environment.isMissing) {
      Text(goneLabel(environment.registeredAs.orEmpty()), style = MaterialTheme.typography.bodySmall)
      SelectionContainer { Text(environment.kernelRemoveCommand, style = codeTextStyle()) }
      return@Column
    }
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
    FocusRingTextButton(
      onClick = { onRegister(environment.path) },
      enabled = environment.hasIpykernel,
      modifier = Modifier.semantics { contentDescription = registerLabel(environment.path) },
    ) {
      Text("Register as kernel")
    }
  }
}

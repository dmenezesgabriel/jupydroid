package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.theme.Spacing
import com.jupydroid.extension.api.FocusRingDropdownMenuItem
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.glyphBesideText

/**
 * The control's accessible name, carrying the current choice so a test and a Maestro flow read the
 * state off the one node they press - `cellTypeDescription`'s rule (`ui/editor/CellTypeControl.kt`).
 */
internal fun defaultKernelDescription(displayName: String): String = "Default kernel: $displayName"

/**
 * What the control shows for a stored default the read choices no longer list - IntelliJ's `invalid`
 * mark (`intellij-community@406a27a
 * python/python-sdk/backend/src/com/jetbrains/python/sdk/impl/PySdkPresentationInfoImpl.kt`) - since
 * the server will start `python3` in its place.
 */
internal fun missingKernelLabel(name: String): String = "$name (missing)"

/** The drop-down arrow beside the kernel's name at the default font scale: Material's own icon size. */
private val DEFAULT_KERNEL_CARET = 24.dp

/** The drop-down arrow beside the kernel's name, for a test that reads its size against the name's line. */
internal const val DEFAULT_KERNEL_CARET_TAG = "Default kernel caret"

/** A choice's accessible name in the open menu. */
internal fun defaultKernelPickDescription(displayName: String): String = "Make $displayName the default kernel"

/**
 * The Space's default kernel, picked among [choices] - the kernelspecs on its disk, null while they
 * are read. The control shows [selected] by its display name, by its bare name while [choices] are
 * read, and as [missingKernelLabel] once they are read and do not list it. A pick hands [onPick] the
 * spec's name, which is what the server is given.
 *
 * A `TextButton` anchoring a `DropdownMenu`, as [com.jupydroid.app.ui.editor.CellTypeControl] is,
 * and with no check mark on the current choice for the reason given there: a check is a state no
 * Maestro flow can read, while the trigger's name carries it.
 *
 * ```
 * SpaceDefaultKernelField(kernelChoices, selected = defaultKernel, onPick = { defaultKernel = it })
 * ```
 */
@Composable
internal fun SpaceDefaultKernelField(choices: List<KernelSpec>?, selected: String, onPick: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  val shown = when {
    choices == null -> selected
    else -> choices.firstOrNull { it.name == selected }?.displayName ?: missingKernelLabel(selected)
  }
  Column(Modifier.padding(top = Spacing.sm)) {
    Text("Default kernel", style = MaterialTheme.typography.labelLarge)
    Box {
      FocusRingTextButton(
        onClick = { open = true },
        enabled = choices != null,
        modifier = Modifier.semantics { contentDescription = defaultKernelDescription(shown) },
      ) {
        Text(shown)
        val caret = LocalDensity.current.glyphBesideText(
          DEFAULT_KERNEL_CARET,
          MaterialTheme.typography.labelLarge.fontSize,
        )
        Icon(
          Icons.Filled.ArrowDropDown,
          contentDescription = null,
          modifier = Modifier.size(caret).testTag(DEFAULT_KERNEL_CARET_TAG),
        )
      }
      DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        choices.orEmpty().forEach { spec ->
          FocusRingDropdownMenuItem(
            modifier = Modifier.semantics { contentDescription = defaultKernelPickDescription(spec.displayName) },
            text = { Text(spec.displayName) },
            onClick = {
              open = false
              onPick(spec.name)
            },
          )
        }
      }
    }
    Text("What a new notebook in this Space starts", style = MaterialTheme.typography.bodySmall)
  }
}

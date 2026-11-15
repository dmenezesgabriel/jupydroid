package com.jupydroid.extension.api

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A `DropdownMenuItem` that draws [LocalFocusRingColor] while it holds keyboard focus.
 *
 * material3 1.4.0's `DropdownMenuItem` names `ripple()` itself (`Menu.kt`), so the host's
 * `FocusRingIndication` never reaches it and a Tab changed only a plate at 1.14:1. The ring goes on
 * the `modifier`, which the item applies outside its own `clickable`, so it lands on the item's whole
 * row.
 *
 * Every menu item under `ui/` and in the extensions goes through this one, so a new one cannot miss
 * the ring (`ArchitectureGuardTest.uiLayer_callsNoMaterialDropdownMenuItem`, `FocusRingGuardTest`).
 *
 * ```
 * FocusRingDropdownMenuItem(text = { Text("Save") }, onClick = onSave)
 * ```
 */
@Composable
public fun FocusRingDropdownMenuItem(
  text: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: @Composable (() -> Unit)? = null,
  enabled: Boolean = true,
) {
  DropdownMenuItem(
    text = text,
    onClick = onClick,
    modifier = modifier.focusRing(),
    leadingIcon = leadingIcon,
    enabled = enabled,
  )
}

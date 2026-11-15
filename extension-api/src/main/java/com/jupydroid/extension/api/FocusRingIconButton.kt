package com.jupydroid.extension.api

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An `IconButton` that draws [LocalFocusRingColor] while it holds keyboard focus.
 *
 * material3 1.4.0's `IconButton` calls `ripple()` itself, so the host's `FocusRingIndication` never
 * reaches it and a Tab changed only a plate at 1.14:1 (`IconButtonKt` in material3 1.4.0). The ring
 * goes on the `modifier`, which `IconButton` applies before `minimumInteractiveComponentSize()`, so
 * it lands on the 48dp touch target and not on the 40dp circle inside it.
 *
 * Every icon button under `ui/` and in this module goes through this one, so a new one cannot miss
 * the ring (`ArchitectureGuardTest.uiLayer_callsNoMaterialIconButton`, `FocusRingGuardTest`).
 *
 * ```
 * FocusRingIconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
 * ```
 */
@Composable
public fun FocusRingIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  IconButton(onClick = onClick, modifier = modifier.focusRing(), enabled = enabled, content = content)
}

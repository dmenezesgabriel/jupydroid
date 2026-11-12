package com.jupydroid.app.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A `TextButton` that draws the palette's `focusBorder` while it holds keyboard focus.
 *
 * material3 1.4.0's `TextButton` is a `Surface(onClick)`, which calls `ripple()` itself, so
 * [FocusRingIndication] never reaches it and a Tab changed only a plate at 1.14:1 (`ButtonKt` and
 * `SurfaceKt` in material3 1.4.0). The ring goes on the `modifier`, which `Surface` applies before
 * `minimumInteractiveComponentSize()`, so it lands on the 48dp touch target.
 *
 * Every text button under `ui/` goes through this one, so a new one cannot miss the ring
 * (`ArchitectureGuardTest.uiLayer_callsNoMaterialTextButton`).
 *
 * ```
 * FocusRingTextButton(onClick = onConfirm) { Text("OK") }
 * ```
 */
@Composable
internal fun FocusRingTextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: ButtonColors = ButtonDefaults.textButtonColors(),
  content: @Composable RowScope.() -> Unit,
) {
  TextButton(onClick = onClick, modifier = modifier.focusRing(), enabled = enabled, colors = colors, content = content)
}

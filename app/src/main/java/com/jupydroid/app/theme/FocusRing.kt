package com.jupydroid.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp

/**
 * The ring's width: JupyterLab's `--jp-focus-outline-width` (`jupyterlab@7f0e6906
 * packages/theme-light-extension/style/variables.css:460`). VS Code draws 1px; a touch tablet at
 * one pane is read at both before this is settled.
 */
private val RING_WIDTH = 2.dp

/**
 * Strokes the focus ring just inside this scope's bounds, as VS Code's `outline-offset: -1px` does
 * (`vscode@3f3380c6 src/vs/workbench/browser/media/style.css:257-272`), so a control that fills its
 * row never has the ring clipped by the row's neighbour.
 *
 * This is the one place the ring's geometry lives: [FocusRingIndication] and [focusRing] both draw
 * through it, so a control reached by either looks the same.
 */
internal fun DrawScope.drawFocusRing(color: Color) {
  val stroke = RING_WIDTH.toPx()
  drawRect(
    color = color,
    topLeft = Offset(stroke / 2, stroke / 2),
    size = Size(size.width - stroke, size.height - stroke),
    style = Stroke(stroke),
  )
}

/**
 * Draws the palette's `focusBorder` on this node's edge while a control inside it holds keyboard
 * focus.
 *
 * **For the controls [FocusRingIndication] cannot reach.** material3 1.4.0 names `ripple()` inside
 * `IconButton`, `Surface` (so `TextButton`), `DropdownMenuItem`, the toggles and the rail's items
 * (`NavigationItem.kt` draws its own with `indication = null`), and none of them reads
 * `LocalIndication`. Each takes a `modifier` that wraps its whole bounds, so this goes there.
 *
 * `hasFocus` rather than `isFocused`, because the node this goes on is a container and the
 * focusable is a descendant (`ui/editor/CellInputFocus.kt` asks the same question). The ring shows
 * only in keyboard input mode: a finger tap can focus a control, and the platform keeps touch focus
 * apart from the focus a Tab gives, so a tap draws no ring.
 *
 * ```
 * NavigationRailItem(selected, onClick, icon, modifier = Modifier.focusRing())
 * ```
 */
@Composable
internal fun Modifier.focusRing(): Modifier {
  var focused by remember { mutableStateOf(false) }
  val ring = chromePalette.focusBorder
  val inputMode = LocalInputModeManager.current
  return onFocusChanged { focused = it.hasFocus }.drawWithContent {
    drawContent()
    if (focused && inputMode.inputMode == InputMode.Keyboard) drawFocusRing(ring)
  }
}

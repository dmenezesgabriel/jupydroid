package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.jupydroid.app.theme.Spacing

/**
 * How far the terminal's text sits in from the pane's left and right edges. One name because three
 * things must agree on it: the padding the canvas is drawn inside, which is the width
 * [terminalGridSize] counts columns of, and the offset a press subtracts to find its cell.
 * VS Code insets the left only, by 20px (`vscode@3f3380c6
 * src/vs/workbench/contrib/terminal/browser/media/terminal.css:76-77`); a phone has no gutter to
 * align to, so both sides take the same `Spacing.md` (ADR-0144).
 */
val terminalMargin: Dp = Spacing.md

/** Below this, `TerminalEmulator.resize` itself throws (its own `< 2` guard). */
private const val MINIMUM_CELLS = 2

/**
 * How many whole [cell]-sized glyphs fit in [containerPixels] - the layout-driven half of
 * 3a, kept a pure function of two sizes so it needs no `BoxWithConstraints`
 * of its own to test. A partial cell at the edge draws nothing, so a remainder is dropped rather
 * than rounded up; a container too small for even [MINIMUM_CELLS] still gets that many, matching
 * the floor [com.jupydroid.app.terminal.TerminalEmulator.resize] itself enforces.
 *
 * ```
 * terminalGridSize(containerPixels = IntSize(800, 600), cell = Size(10f, 20f))  // 80 x 30
 * ```
 */
fun terminalGridSize(containerPixels: IntSize, cell: Size): IntSize = IntSize(
  width = (containerPixels.width / cell.width).toInt().coerceAtLeast(MINIMUM_CELLS),
  height = (containerPixels.height / cell.height).toInt().coerceAtLeast(MINIMUM_CELLS),
)

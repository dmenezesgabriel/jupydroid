package com.jupydroid.app.ui.terminal

import androidx.compose.ui.unit.IntSize

/** Below this, `TerminalEmulator.resize` itself throws (P4h-3's own `< 2` guard). */
private const val MINIMUM_CELLS = 2

/**
 * How many whole [cell]-sized glyphs fit in [containerPixels] - the layout-driven half of
 * 3a, kept a pure function of two sizes so it needs no `BoxWithConstraints`
 * of its own to test. A partial cell at the edge draws nothing, so a remainder is dropped rather
 * than rounded up; a container too small for even [MINIMUM_CELLS] still gets that many, matching
 * the floor [com.jupydroid.app.terminal.TerminalEmulator.resize] itself enforces.
 *
 * ```
 * terminalGridSize(containerPixels = IntSize(800, 600), cell = IntSize(10, 20))  // 80 x 30
 * ```
 */
fun terminalGridSize(containerPixels: IntSize, cell: IntSize): IntSize = IntSize(
  width = (containerPixels.width / cell.width).coerceAtLeast(MINIMUM_CELLS),
  height = (containerPixels.height / cell.height).coerceAtLeast(MINIMUM_CELLS),
)

package com.jupydroid.app.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.jupydroid.app.ui.editor.codeTextStyle

/**
 * One glyph's width and line height at [style], in pixels - Compose's own equivalent of Termux's
 * `Paint.measureText("X")` / `Paint.getFontSpacing()` (`TerminalRenderer.java:44-47`), through
 * [codeTextStyle] rather than naming `FontFamily.Monospace` again
 * (`scripts/check_monospace_home.py`'s own rule). `docs/PLAN.md`'s P4i-2c.
 *
 * "X" rather than a blank: it is the glyph every column's width is actually measured against,
 * where a space risks a font substituting a narrower fallback glyph for it.
 *
 * `TextMeasurer.measure(...).size` gives both dimensions in one call - `LineNumbers.kt`'s own
 * pattern - so no separate ascent/line-spacing bookkeeping is needed the way `Paint` forces on
 * Termux: [com.jupydroid.app.terminal.TerminalCanvas] draws through `drawText`'s layout-result
 * overload, not hand-placed baselines.
 *
 * ```
 * val cell = terminalCellMetrics()   // cell.width, cell.height, in pixels
 * ```
 */
@Composable
fun terminalCellMetrics(style: TextStyle = codeTextStyle()): IntSize {
  val measurer = rememberTextMeasurer()
  return measurer.measure(GLYPH, style).size
}

private const val GLYPH = "X"

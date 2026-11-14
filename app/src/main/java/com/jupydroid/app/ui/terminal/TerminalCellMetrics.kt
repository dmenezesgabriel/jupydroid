package com.jupydroid.app.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.jupydroid.app.ui.editor.codeTextStyle

/**
 * One glyph's advance and line height at [style], in pixels - Compose's own equivalent of Termux's
 * `Paint.measureText("X")` / `Paint.getFontSpacing()` (`TerminalRenderer.java:44-47`), through
 * [codeTextStyle] rather than naming `FontFamily.Monospace` again
 * (`scripts/check_monospace_home.py`'s own rule). 2c.
 *
 * "X" rather than a blank: it is the glyph every column's width is actually measured against,
 * where a space risks a font substituting a narrower fallback glyph for it.
 *
 * The width is `getLineRight(0)`, the font's own fractional advance, because `size.width` is that
 * advance rounded up to a whole pixel: a cell counted at the rounded width and drawn at the advance
 * leaves the last glyph of a full line short of the right margin (ADR-0144), where Termux keeps
 * `mFontWidth` a `float` (`TerminalRenderer.java:47`). The height is `size.height`, already a whole
 * pixel as Termux's `mFontLineSpacing` is an `int`. One measurement gives both, so no separate
 * ascent/line-spacing bookkeeping is needed the way `Paint` forces on Termux:
 * [com.jupydroid.app.terminal.TerminalCanvas] draws through `drawText`'s layout-result overload,
 * not hand-placed baselines.
 *
 * ```
 * val cell = terminalCellMetrics()   // cell.width, cell.height, in pixels
 * ```
 */
@Composable
fun terminalCellMetrics(style: TextStyle = codeTextStyle()): Size {
  val measurer = rememberTextMeasurer()
  val layout = measurer.measure(GLYPH, style)
  return Size(width = layout.getLineRight(0), height = layout.size.height.toFloat())
}

private const val GLYPH = "X"

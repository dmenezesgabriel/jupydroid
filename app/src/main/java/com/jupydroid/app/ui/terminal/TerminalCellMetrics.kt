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
 * The width is what one more glyph adds to a run, because a lone glyph's layout is rounded to a whole
 * pixel (14.0 on the SM-X216B, where a run of them steps 14.81) and `size.width` rounds it up again,
 * so a cell counted at either leaves the last glyph of a full line short of the right margin
 * (ADR-0144), where Termux keeps `mFontWidth` a `float` (`TerminalRenderer.java:47`).
 * [TerminalCanvas] draws runs, so the pitch is read off two of them: the difference cancels the
 * constant the run's right edge carries (`TerminalCellMetricsTest`). The height is `size.height`,
 * already a whole pixel as Termux's `mFontLineSpacing` is an `int`. No separate ascent/line-spacing
 * bookkeeping is needed the way `Paint` forces on Termux:
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
  fun runRight(glyphs: Int) = measurer.measure(GLYPH.repeat(glyphs), style).getLineRight(0)
  val advance = (runRight(2 * PITCH_RUN_GLYPHS) - runRight(PITCH_RUN_GLYPHS)) / PITCH_RUN_GLYPHS
  return Size(width = advance, height = measurer.measure(GLYPH, style).size.height.toFloat())
}

private const val GLYPH = "X"

/** Long enough that the font's rounding of one glyph is a rounding error in the pitch, short enough to measure fast. */
private const val PITCH_RUN_GLYPHS = 64

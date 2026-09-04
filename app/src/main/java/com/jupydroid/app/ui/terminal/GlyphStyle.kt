package com.jupydroid.app.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalColors
import com.jupydroid.app.terminal.TerminalRow

/**
 * What one [CellStyle] becomes for Compose to draw: a resolved colour pair, a [TextStyle] and the
 * text a run of columns holds - split out of `TerminalCanvas.kt` on detekt's own `TooManyFunctions`
 * cap, and a real seam besides: this is style resolution, `TerminalCanvas.kt` is layout and drawing.
 *
 * Mirrors `TerminalRenderer.drawTextRun` (termux-app `3df69d1d`, Apache 2.0 - see `TerminalGrid`'s
 * own citation).
 */
internal fun textOf(row: TerminalRow, startColumn: Int, endColumn: Int): String {
  val start = row.findStartOfColumn(startColumn)
  val end = row.findStartOfColumn(endColumn)
  return String(row.text, start, end - start)
}

internal fun textOfRow(row: TerminalRow): String = String(row.text, 0, row.spaceUsed).trimEnd()

/** Weight, slant and decoration from [runStyle]'s effect bits - `TerminalRenderer.drawTextRun`'s own mapping. */
internal fun styleFor(base: TextStyle, runStyle: CellStyle, foreColor: Int): TextStyle {
  val effect = runStyle.effect
  val bold = effect and (CellStyle.BOLD or CellStyle.BLINK) != 0
  return base.copy(
    color = Color(foreColor),
    fontWeight = if (bold) FontWeight.Bold else null,
    fontStyle = if (effect and CellStyle.ITALIC != 0) FontStyle.Italic else null,
    textDecoration = textDecorationFor(effect),
  )
}

private fun textDecorationFor(effect: Int): TextDecoration? {
  val underline = effect and CellStyle.UNDERLINE != 0
  val strikethrough = effect and CellStyle.STRIKETHROUGH != 0
  return when {
    underline && strikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
    underline -> TextDecoration.Underline
    strikethrough -> TextDecoration.LineThrough
    else -> null
  }
}

/**
 * Foreground and background, resolved from an index or taken as-is when already truecolor - the
 * same check `TerminalRenderer.drawTextRun` uses (`(color & 0xff000000) != 0xff000000`) - with
 * bold's bright-colour rule applied first, while the foreground is still a palette index, and
 * inverse video and dim applied after.
 */
internal fun resolvedRunColors(runStyle: CellStyle, colors: TerminalColors): Pair<Int, Int> {
  var fore = runStyle.foreColor
  var back = runStyle.backColor
  val effect = runStyle.effect
  if (!isTruecolor(fore) && effect and (CellStyle.BOLD or CellStyle.BLINK) != 0 && fore in 0 until BRIGHT_OFFSET) {
    fore += BRIGHT_OFFSET
  }
  fore = resolvedColor(fore, colors)
  back = resolvedColor(back, colors)
  if (effect and CellStyle.INVERSE != 0) {
    val swap = fore
    fore = back
    back = swap
  }
  if (effect and CellStyle.DIM != 0) fore = dimmed(fore)
  return fore to back
}

private fun isTruecolor(color: Int): Boolean = (color and OPAQUE_ALPHA) == OPAQUE_ALPHA

private fun resolvedColor(indexOrArgb: Int, colors: TerminalColors): Int =
  if (isTruecolor(indexOrArgb)) indexOrArgb else colors.at(indexOrArgb)

/** libvte's dim, taken from xterm - two thirds of each channel, `TerminalRenderer.drawTextRun`'s own arithmetic. */
private fun dimmed(color: Int): Int {
  val red = (0xFF and (color shr RED_SHIFT)) * 2 / 3
  val green = (0xFF and (color shr GREEN_SHIFT)) * 2 / 3
  val blue = (0xFF and color) * 2 / 3
  return OPAQUE_ALPHA or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

private const val BRIGHT_OFFSET = 8
private const val OPAQUE_ALPHA = 0xFF shl 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

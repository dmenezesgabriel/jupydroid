package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a line number beside every logical line of the text this modifier decorates, and
 * reserves the room they need.
 *
 * Positions come from the field's own [layout] rather than from counting newlines, which is the
 * only way a wrapped line keeps one number: a line that takes two visual rows must not push the
 * next number two rows down.
 *
 * ```
 * BasicTextField(state = state, modifier = Modifier.lineNumbers(state.text, layout, style))
 * ```
 *
 * @param layout the field's latest [TextLayoutResult], or null before it has laid out once.
 * @param width from [gutterWidth]; passed in rather than computed here because the completion
 *   popup anchors against the same edge and the two must not disagree.
 */
@Composable
fun Modifier.lineNumbers(
  text: CharSequence,
  layout: TextLayoutResult?,
  style: TextStyle,
  width: Dp,
): Modifier {
  val starts = logicalLineStarts(text)
  val measurer = rememberTextMeasurer()
  // Measured once per line count rather than per frame: a TextMeasurer's cache is small, and a
  // thirty-line cell would miss it on every draw.
  val numbers = remember(starts.size, style) { List(starts.size) { measurer.measure("${it + 1}", style) } }
  val digits = with(LocalDensity.current) { (width - NUMBER_GAP).roundToPx() }

  return drawBehind {
    if (layout == null) return@drawBehind
    numbers.forEachIndexed { index, number ->
      // Right-aligned, so the digits line up with each other rather than with the code.
      val x = (digits - number.size.width).toFloat()
      drawText(number, topLeft = Offset(x, layout.getLineTop(layout.getLineForOffset(starts[index]))))
    }
  }.padding(start = width)
}

/** How far the code is pushed right to make room for the numbers. */
@Composable
fun gutterWidth(text: CharSequence, style: TextStyle): Dp {
  val measurer = rememberTextMeasurer()
  val lineCount = logicalLineStarts(text).size
  val digits = remember(lineCount, style) {
    measurer.measure("0".repeat(gutterDigits(lineCount)), style).size.width
  }
  return with(LocalDensity.current) { digits.toDp() } + NUMBER_GAP
}

/**
 * Where each logical line begins: offset 0, then every offset just past a newline.
 *
 * Offsets into the source rather than the laid-out text, which is the same thing here - the
 * output transformation only styles, so it inserts and removes nothing.
 */
fun logicalLineStarts(text: CharSequence): List<Int> {
  val starts = mutableListOf(0)
  text.forEachIndexed { index, char -> if (char == '\n') starts += index + 1 }
  return starts
}

/** How wide the gutter is, in digits. Two from the start so nine-to-ten does not shift the code. */
fun gutterDigits(lineCount: Int): Int = maxOf(MIN_DIGITS, lineCount.toString().length)

private const val MIN_DIGITS = 2
private val NUMBER_GAP = 8.dp

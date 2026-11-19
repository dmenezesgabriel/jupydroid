package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.editorPalette

/**
 * The diagnostics' own underline, drawn under the code rather than only beside it - the
 * half of the reference UX the gutter mark alone was missing: VS Code's `squiggly-*`
 * decorations (`markerDecorationsService` + the 6x3 SVG wave tile `codeEditorWidget.ts`
 * registers), IntelliJ's `EffectType.WAVE_UNDERSCORE`, and `@codemirror/lint`'s
 * `cm-lintRange-*` mark. All three put the mark on the text; only then does a gutter mark
 * say *where to look*.
 *
 * Appended after the field's start padding (see [CodeCellField]), so the draw scope's
 * x-coordinates are the text's own - `TextLayoutResult`'s coordinates, one-to-one - and a
 * horizontally scrolled field carries its squiggles with it.
 */
@Composable
internal fun Modifier.diagnosticSquiggles(diagnostics: List<CellDiagnostic>, layout: TextLayoutResult?): Modifier {
  if (diagnostics.isEmpty()) return this
  val palette = editorPalette
  return drawBehind {
    if (layout == null) return@drawBehind
    val text = layout.layoutInput.text.toString()
    diagnostics.forEach { diagnostic ->
      normalizeDiagnosticRange(text, diagnostic.start, diagnostic.end, diagnostic.severity)
        ?.let { drawSquiggleSpan(layout, it, severityColour(diagnostic.severity, palette), diagnostic.severity) }
    }
  }
}

/**
 * What VS Code's `_createDecorationRange` makes of the raw range before it is decorated
 * (`markerDecorationsService.ts:190-224`): the drawable span as an [IntRange] into [text],
 * or null when nothing drawable survives.
 *
 * - a **hint** never spans lines: it renders `start + 2`, the three dots the `dotdotdot`
 *   SVG draws, clamped to its line's end (`model.validateRange` does that part);
 * - an **empty** range grows to the word it sits in (`model.getWordAtPosition`), unless it
 *   lands at or past the line's last non-whitespace character - "behind eol" - where VS
 *   Code keeps it and renders it one character wide;
 * - a **whole-line** marker - the range is its line's content, with or without the newline
 *   - loses its leading indent (VS Code's `startColumn == 1 && endColumn == MAX` trim);
 * - a range that crosses into the next line's content is none of these - no trimming.
 */
internal fun normalizeDiagnosticRange(text: CharSequence, start: Int, end: Int, severity: Int): IntRange? {
  var s = start.coerceIn(0, text.length)
  var e = end.coerceIn(s, text.length)
  if (severity == SEVERITY_HINT) {
    e = minOf(s + HINT_SPAN, lineEnd(text, s))
  } else if (s == e) {
    val lastCode = lastNonWhitespaceOnLine(text, s)
    if (lastCode != null && s <= lastCode) {
      wordAround(text, s)?.let {
        s = it.first
        e = it.last + 1
      }
    }
    if (s == e) e = minOf(s + 1, text.length)
  } else if (s == lineStart(text, s) && e >= lineEnd(text, s) && e <= lineEnd(text, s) + 1) {
    firstNonWhitespace(text, s, e)?.let { s = it }
  }
  return if (e > s) s until e else null
}

/**
 * One span's underline on [layout], per visual row it covers: the wave is CodeMirror's
 * `cm-lintRange` path (`m0 2.5 l2 -1.5 l1 0 l2 1.5 l1 0` in a 6x3 tile, stroke .7) rather
 * than a freehand zigzag - flats at top and bottom are what makes it read as a wave and
 * not a sparkline. Hints draw VS Code's three dots (`dotdotdot`) instead.
 */
private fun DrawScope.drawSquiggleSpan(layout: TextLayoutResult, span: IntRange, color: Color, severity: Int) {
  layout.rowsOf(span).forEach { row ->
    if (severity == SEVERITY_HINT) {
      drawHintDots(row.left, row.bottom, color)
    } else {
      drawWave(row.left, row.right, row.bottom, color)
    }
  }
}

/** The wave's 6x3 tile repeated along `[x0, x1)`, its bottom edge on the line's bottom. */
private fun DrawScope.drawWave(x0: Float, x1: Float, lineBottom: Float, color: Color) {
  val unit = WAVE_HEIGHT.toPx() / WAVE_TILE_ROWS
  val yLow = lineBottom - unit * WAVE_BASE_ROW
  val yHigh = lineBottom - unit * WAVE_CREST_ROW
  val path = Path()
  var x = x0
  path.moveTo(x, yLow)
  while (x < x1) {
    path.lineTo(minOf(x + WAVE_CREST_START * unit, x1), yHigh)
    path.lineTo(minOf(x + WAVE_CREST_END * unit, x1), yHigh)
    path.lineTo(minOf(x + WAVE_FALL_END * unit, x1), yLow)
    path.lineTo(minOf(x + WAVE_TILE * unit, x1), yLow)
    x += WAVE_TILE * unit
  }
  drawPath(path, color = color, style = Stroke(width = WAVE_STROKE.toPx()))
}

/** VS Code's hint decoration - `dotdotdot`, three dots under the start of the span. */
private fun DrawScope.drawHintDots(x0: Float, lineBottom: Float, color: Color) {
  val radius = HINT_DOT_RADIUS.toPx()
  repeat(HINT_DOT_COUNT) {
    val cx = x0 + radius + it * HINT_DOT_PERIOD.toPx()
    drawCircle(color, radius = radius, center = Offset(cx, lineBottom - radius))
  }
}

private fun lineStart(text: CharSequence, offset: Int): Int = text.lastIndexOf('\n', offset - 1) + 1

private fun lineEnd(text: CharSequence, offset: Int): Int =
  text.indexOf('\n', offset).let { if (it < 0) text.length else it }

private fun firstNonWhitespace(text: CharSequence, start: Int, end: Int): Int? =
  (start until end).firstOrNull { !text[it].isWhitespace() }

private fun lastNonWhitespaceOnLine(text: CharSequence, offset: Int): Int? {
  val end = lineEnd(text, offset)
  return (end - 1 downTo lineStart(text, offset)).firstOrNull { !text[it].isWhitespace() }
}

/** The identifier span containing [offset] - VS Code's `getWordAtPosition` for Python. */
private fun wordAround(text: CharSequence, offset: Int): IntRange? {
  if (offset >= text.length || !text[offset].isPythonWordChar()) return null
  var first = offset
  var last = offset
  while (first > 0 && text[first - 1].isPythonWordChar()) first--
  while (last + 1 < text.length && text[last + 1].isPythonWordChar()) last++
  return first..last
}

private fun Char.isPythonWordChar(): Boolean = isLetterOrDigit() || this == '_'

/** `start + 2` - VS Code's room for the hint's three dots. */
private const val HINT_SPAN = 2

/**
 * The wave tile's stops and rows, measured from the `cm-lintRange`/`squiggly` 6x3 SVG:
 * `m0 2.5 l2 -1.5 l1 0 l2 1.5 l1 0` - rise to 2, flat crest to 3, fall to 5, flat to 6;
 * its foot rides row 0.5 of three, its crest row 2.
 */
private const val WAVE_TILE_ROWS = 3f
private const val WAVE_BASE_ROW = 0.5f
private const val WAVE_CREST_ROW = 2.0f
private const val WAVE_CREST_START = 2f
private const val WAVE_CREST_END = 3f
private const val WAVE_FALL_END = 5f
private const val WAVE_TILE = 6f

/** The 3-unit height both references give the wave tile; 6 units wide rides along. */
private val WAVE_HEIGHT = 3.dp

/** CodeMirror's `stroke-width=".7"` on a 3-unit tile - a hair under a dp at 3.dp. */
private val WAVE_STROKE = 0.9.dp

/** The `dotdotdot` SVG's own geometry: `r=1`, three dots, centres four units apart. */
private const val HINT_DOT_COUNT = 3

/** The SVG's `r=1` - one dp's dot. */
private val HINT_DOT_RADIUS = 1.dp

/** The SVG's centre spacing - four dp apart, so the trio spans the hint's two characters. */
private val HINT_DOT_PERIOD = 4.dp

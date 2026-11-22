package com.jupydroid.app.ui.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult

/** One diagnostic's drawn span on one visual row, in the text layout's own coordinates. */
internal data class DiagnosticSpanRect(val line: Int, val rect: Rect)

/**
 * The rect [span] fills on each visual row it covers, in [TextLayoutResult]'s own coordinates:
 * what the squiggle is drawn under and what a tap on it ([diagnosticTaps]) is tested against.
 */
internal fun TextLayoutResult.rowsOf(span: IntRange): List<Rect> =
  (getLineForOffset(span.first)..getLineForOffset(span.last)).mapNotNull { line ->
    val segStart = if (line == getLineForOffset(span.first)) span.first else getLineStart(line)
    val segEnd = if (line == getLineForOffset(span.last)) span.last + 1 else getLineEnd(line)
    if (segEnd <= segStart) return@mapNotNull null
    Rect(getBoundingBox(segStart).left, getLineTop(line), getBoundingBox(segEnd - 1).right, getLineBottom(line))
  }

/** Every row of every diagnostic, so a tap's target is what the finger saw underlined. */
internal fun diagnosticSpanRects(
  diagnostics: List<CellDiagnostic>,
  layout: TextLayoutResult,
): List<DiagnosticSpanRect> {
  val text = layout.layoutInput.text.toString()
  return diagnostics.flatMap { diagnostic ->
    val span = normalizeDiagnosticRange(text, diagnostic.start, diagnostic.end, diagnostic.severity)
      ?: return@flatMap emptyList()
    layout.rowsOf(span).map { DiagnosticSpanRect(diagnostic.line, it) }
  }
}

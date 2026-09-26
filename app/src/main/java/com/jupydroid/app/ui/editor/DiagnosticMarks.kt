package com.jupydroid.app.ui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.theme.EditorPalette

/**
 * How a cell's language-server diagnostics appear in the gutter - `docs/PLAN.md` P5d-2b, kept
 * beside rather than inside [LineNumbers.kt], which sits at the file's own function budget.
 */

/**
 * A severity-coloured square centred in the mark column - CodeMirror's lint-gutter icon's own
 * job (`@codemirror/lint` `lintGutter`), a shape a breakpoint's circle does not already say.
 * [LineNumbers.gutterDrawing] paints it **under** the dot: a line carrying both reads as the
 * breakpoint badged by its worst diagnostic, the same layered-marks vocabulary the condition
 * badge (`withQuestionBadgeIfNeeded`) uses rather than two columns.
 */
internal fun DrawScope.drawDiagnosticMark(severity: Int, metrics: GutterMetrics, top: Float, rowHeight: Float) {
  val half = minOf(metrics.breakpointColumnPx.toFloat(), rowHeight) / DIAGNOSTIC_SHRINK
  drawRect(
    color = severityColour(severity, metrics.palette),
    topLeft = Offset(metrics.breakpointColumnPx / 2f - half, top + rowHeight / 2f - half),
    size = Size(half * 2, half * 2),
  )
}

/** The palette colour an LSP severity number draws with; unknown numbers read as the default, error. */
internal fun severityColour(severity: Int, palette: EditorPalette): Color = when (severity) {
  SEVERITY_WARNING -> palette.diagnosticWarning
  SEVERITY_INFORMATION -> palette.diagnosticInformation
  SEVERITY_HINT -> palette.diagnosticHint
  else -> palette.diagnosticError
}

/**
 * The painted marks said aloud - a square carries no semantics, so the field states its own
 * diagnostics for TalkBack and for the flow that proves them. CodeMirror announces lint-gutter
 * entries the same way.
 */
internal fun Modifier.diagnosticSemantics(diagnostics: List<CellDiagnostic>): Modifier =
  if (diagnostics.isEmpty()) this else semantics { contentDescription = diagnosticSummary(diagnostics) }

/** The diagnostic square is a fraction wider than the dot that may sit on it - it must peek out. */
private const val DIAGNOSTIC_SHRINK = 2.0f

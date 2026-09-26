package com.jupydroid.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jupydroid.app.theme.EditorPalette
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.editorPalette

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

/** Who raised a diagnostic and its rule - `pyflakes · E305`, or null when neither came. */
internal fun attributionOf(diagnostic: CellDiagnostic): String? =
  listOfNotNull(diagnostic.source, diagnostic.code).joinToString(" · ").ifEmpty { null }

/**
 * The rect a pressed mark anchors its popup to - the whole of [line]'s row across the gutter,
 * so the popup opens just beside where the code starts rather than on the pixels of the square.
 * Null when [line] names no logical line of [layout]'s text.
 */
internal fun diagnosticMarkRect(layout: TextLayoutResult, starts: List<Int>, line: Int, gutterPx: Int): IntRect? {
  val index = line - 1
  if (index !in starts.indices) return null
  val top = layout.getLineTop(layout.getLineForOffset(starts[index]))
  val lastOffset = if (index + 1 < starts.size) starts[index + 1] - 1 else layout.layoutInput.text.length
  val bottom = layout.getLineBottom(layout.getLineForOffset(lastOffset.coerceAtLeast(starts[index])))
  return IntRect(0, top.toInt(), gutterPx, bottom.toInt())
}

/**
 * What the mark has to say once it is tapped - `docs/PLAN.md` P5e-1.
 *
 * VS Code answers this content on the marker's hover (`editor.contrib.hover`'s
 * `markerHoverParticipant`): severity, the message, and who raised it. A tap on the square is
 * that hover performed with a finger, so the popup lists the line's findings the same way -
 * the mark's own colour leading, the message, then `source · code` where the server sent them.
 * Anchored on the line's row and dismisses like every popup.
 *
 * ```
 * DiagnosticDetailPopup(diagnostics, anchor = markRect, onDismiss = { detailLine = null })
 * ```
 */
@Composable
internal fun DiagnosticDetailPopup(diagnostics: List<CellDiagnostic>, anchor: IntRect, onDismiss: () -> Unit) {
  if (diagnostics.isEmpty()) return
  val bottomInset = WindowInsets.ime.union(WindowInsets.navigationBars).getBottom(LocalDensity.current)
  // `focusable = true` is what makes outside taps reach `onDismissRequest`: a non-focusable
  // Popup never receives the outside-press notification, so the card would hang until the line
  // lost every finding. Found live on the tablet - `DiagnosticMarkTapTest.tappingElsewhereDismisses`.
  Popup(
    popupPositionProvider = CompletionPopupPosition(anchor, bottomInset),
    onDismissRequest = onDismiss,
    properties = PopupProperties(focusable = true),
  ) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = DETAIL_ELEVATION,
      modifier = Modifier.semantics { contentDescription = "Diagnostic detail" },
    ) {
      Column(Modifier.widthIn(max = DETAIL_MAX_WIDTH).padding(vertical = Spacing.xs)) {
        diagnostics.forEach { DiagnosticDetailRow(it) }
      }
    }
  }
}

@Composable
private fun DiagnosticDetailRow(diagnostic: CellDiagnostic) {
  val palette = editorPalette
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
  ) {
    Canvas(Modifier.size(DETAIL_GLYPH)) {
      drawCircle(color = severityColour(diagnostic.severity, palette))
    }
    Column(Modifier.padding(start = Spacing.sm)) {
      Text(
        text = "${severityName(diagnostic.severity)}: ${diagnostic.message}",
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
      )
      attributionOf(diagnostic)?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** The diagnostic square is a fraction wider than the dot that may sit on it - it must peek out. */
private const val DIAGNOSTIC_SHRINK = 2.0f

/** The square leading a detail row - the mark's own colour so the row reads as its expansion. */
private val DETAIL_GLYPH = 10.dp

/** As wide as the completion popup and no wider - see `CellCompletions.MAX_WIDTH`'s reasoning. */
private val DETAIL_MAX_WIDTH = 280.dp

/** `ElevationTokens.Level2` - the same step [CompletionPopup] takes off the cell it floats over. */
private val DETAIL_ELEVATION = 3.dp

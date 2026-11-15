package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import com.jupydroid.extension.api.glyphBesideText

/**
 * How a cell's language-server diagnostics appear in the gutter, kept
 * beside rather than inside [LineNumbers.kt], which sits at the file's own function budget.
 */

/**
 * The mark's glyph **is** the severity - a real icon rather than a geometric shape, since
 * the hand-drawn circle/triangle/square set read as coloured blobs at gutter size while an
 * outlined glyph reads as the sign it is. The vocabulary is VS Code's codicons (`error`,
 * `warning`, `info`, `lightbulb`) drawn in Material's Outlined set - the subtle treatment
 * neither VS Code nor IntelliJ can show, since they draw no per-line mark at all: CodeMirror's
 * `lintGutter` does, so a mark stays, and its being the detail popup's tap target is the one
 * reason it must.
 *
 * Drawn under the breakpoint's dot, so a line carrying both still reads as the breakpoint
 * badged by its worst diagnostic - the layering this draw order buys.
 */
internal fun DrawScope.drawDiagnosticMark(severity: Int, metrics: GutterMetrics, top: Float, rowHeight: Float) {
  val side = minOf(metrics.breakpointColumnPx.toFloat(), rowHeight) * MARK_ICON
  val center = Offset(metrics.breakpointColumnPx / 2f, top + rowHeight / 2f)
  val painter = metrics.markPainters[severity] ?: metrics.markPainters.getValue(SEVERITY_ERROR)
  translate(center.x - side / 2f, center.y - side / 2f) {
    with(painter) {
      draw(Size(side, side), colorFilter = ColorFilter.tint(severityColour(severity, metrics.palette)))
    }
  }
}

/**
 * The glyph an LSP severity number draws with - VS Code's codicon vocabulary rendered in
 * Material's Outlined set: `error` is the circle-x, `warning` the triangle, `info` the
 * circle-i and a hint the `lightbulb` quick-fix wears. Unknown numbers read as the default,
 * error - [severityColour]'s own fallback.
 */
internal fun severityIcon(severity: Int): ImageVector = when (severity) {
  SEVERITY_WARNING -> Icons.Outlined.Warning
  SEVERITY_INFORMATION -> Icons.Outlined.Info
  SEVERITY_HINT -> Icons.Outlined.Lightbulb
  else -> Icons.Outlined.Cancel
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
 * What the mark has to say once it is tapped.
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
  val glyph = LocalDensity.current.glyphBesideText(DETAIL_GLYPH, MaterialTheme.typography.bodySmall.fontSize)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
  ) {
    // The mark's own glyph leads the row, the way a VS Code marker hover leads with the
    // severity icon - the row reads as the gutter mark's expansion rather than a new shape.
    Icon(
      imageVector = severityIcon(diagnostic.severity),
      contentDescription = null,
      tint = severityColour(diagnostic.severity, palette),
      modifier = Modifier.size(glyph).testTag(DETAIL_GLYPH_TAG),
    )
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

/**
 * How much of the mark cell the glyph fills - Material's vectors draw their own padding
 * into the 24-unit viewport, so a mark sized near the cell reads at the dot's own weight
 * rather than larger than it.
 */
private const val MARK_ICON = 0.9f

/**
 * The glyph leading a detail row - the mark's own icon, tinted by severity - at the default font scale. It grows
 * with the row's message from there (`glyphBesideText`).
 */
private val DETAIL_GLYPH = 12.dp

internal const val DETAIL_GLYPH_TAG = "Diagnostic detail glyph"

/** As wide as the completion popup and no wider - see `CellCompletions.MAX_WIDTH`'s reasoning. */
private val DETAIL_MAX_WIDTH = 280.dp

/** `ElevationTokens.Level2` - the same step [CompletionPopup] takes off the cell it floats over. */
private val DETAIL_ELEVATION = 3.dp

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
import androidx.compose.ui.graphics.Color
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
 * How a cell's language-server diagnostics are named, coloured and read: the severity vocabulary,
 * the accessibility summary and the card a tap on a squiggle ([diagnosticTaps]) opens.
 */

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
 * The painted squiggles said aloud - canvas carries no semantics, so the field states its own
 * diagnostics for TalkBack and for the flow that proves them. CodeMirror announces lint-gutter
 * entries the same way.
 */
internal fun Modifier.diagnosticSemantics(diagnostics: List<CellDiagnostic>): Modifier =
  if (diagnostics.isEmpty()) this else semantics { contentDescription = diagnosticSummary(diagnostics) }

/** Who raised a diagnostic and its rule - `pyflakes · E305`, or null when neither came. */
internal fun attributionOf(diagnostic: CellDiagnostic): String? =
  listOfNotNull(diagnostic.source, diagnostic.code).joinToString(" · ").ifEmpty { null }

/**
 * The rect the card anchors to - the whole of [line]'s row across the gutter, so the card
 * opens just beside where the code starts rather than on the squiggle the finger is on.
 * Null when [line] names no logical line of [layout]'s text.
 */
internal fun diagnosticLineRect(layout: TextLayoutResult, starts: List<Int>, line: Int, gutterPx: Int): IntRect? {
  val index = line - 1
  if (index !in starts.indices) return null
  val top = layout.getLineTop(layout.getLineForOffset(starts[index]))
  val lastOffset = if (index + 1 < starts.size) starts[index + 1] - 1 else layout.layoutInput.text.length
  val bottom = layout.getLineBottom(layout.getLineForOffset(lastOffset.coerceAtLeast(starts[index])))
  return IntRect(0, top.toInt(), gutterPx, bottom.toInt())
}

/**
 * What a squiggle has to say once it is tapped.
 *
 * VS Code answers this content on the marker's hover (`editor.contrib.hover`'s
 * `markerHoverParticipant`): severity, the message, and who raised it. A tap on the squiggle is
 * that hover performed with a finger, so the popup lists the line's findings the same way -
 * the severity's own icon leading, the message, then `source · code` where the server sent them.
 * Anchored on the line's row and dismisses like every popup.
 *
 * ```
 * DiagnosticDetailPopup(diagnostics, anchor = lineRect, onDismiss = { detailLine = null })
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
 * The glyph leading a detail row - the mark's own icon, tinted by severity - at the default font scale. It grows
 * with the row's message from there (`glyphBesideText`).
 */
private val DETAIL_GLYPH = 12.dp

internal const val DETAIL_GLYPH_TAG = "Diagnostic detail glyph"

/** As wide as the completion popup and no wider - see `CellCompletions.MAX_WIDTH`'s reasoning. */
private val DETAIL_MAX_WIDTH = 280.dp

/** `ElevationTokens.Level2` - the same step [CompletionPopup] takes off the cell it floats over. */
private val DETAIL_ELEVATION = 3.dp

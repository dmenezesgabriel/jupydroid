package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import com.jupydroid.app.theme.editorPalette

/**
 * The "this is where you landed" mark a go-to-definition paints for a beat.
 *
 * VS Code's `_openReference` decorates the target range with `symbolHighlight` for 350 ms
 * (`goToCommands.ts`), tinted `editor.symbolHighlightBackground` - which the color registry
 * resolves to `editorFindMatchHighlight` (`editorColorRegistry.ts`), the find-match colour, so
 * this draws with the same `searchMatch` token a search highlight does. jupyterlab-lsp flashes
 * the target the same way. Unlike a diagnostic squiggle the mark covers whole lines the range
 * spans - `getPathForRange` answers the range's own shape, wrap-aware and all.
 *
 * Appended after the field's start padding like [diagnosticSquiggles], so the draw scope's
 * coordinates are the `TextLayoutResult`'s own.
 */
@Composable
internal fun Modifier.symbolFlash(range: TextRange?, layout: TextLayoutResult?): Modifier {
  if (range == null) return this
  val color = editorPalette.searchMatch
  return drawBehind {
    if (layout == null) return@drawBehind
    val end = layout.layoutInput.text.length
    val start = range.start.coerceIn(0, end)
    val path = layout.getPathForRange(start, range.end.coerceIn(start, end))
    drawPath(path, color)
  }
}

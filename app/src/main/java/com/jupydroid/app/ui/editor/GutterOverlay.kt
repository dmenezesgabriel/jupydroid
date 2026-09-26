package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.theme.EditorPalette
import com.jupydroid.app.theme.editorPalette

/**
 * The gutter drawn as its own fixed sibling rather than folded into the field's modifier chain -
 * `docs/PLAN.md`'s P6c-5. Once a code cell can scroll horizontally, a gutter reached by chaining
 * `Modifier.horizontalScroll` into the same chain [lineNumbers] uses scrolled away with the code
 * instead of staying beside it - measured on a real device, not assumed
 * (`evidence/p6c-5-word-wrap/05-scrolled-toward-the-marker.png`, captured before this existed: the
 * translation a scroll modifier applies to its own placement reaches every draw call in the same
 * chain, including ones textually declared before it). A sibling `Box`, outside the scrolled
 * field's own layout entirely, cannot inherit that translation - it draws and hit-tests
 * identically to [lineNumbers], sharing every helper it uses rather than keeping its own copy, and
 * paints [EditorPalette.cellBackground] behind the numbers so scrolled code cannot show through
 * under them.
 *
 * @param modifier positions this within the caller's own `Box`. Not
 *   [androidx.compose.foundation.layout.BoxScope.matchParentSize]: that fills the *whole* field,
 *   not just the gutter's own column - height alone comes from [layout], width from [width].
 */
@Composable
fun GutterOverlay(
  text: CharSequence,
  layout: TextLayoutResult?,
  style: TextStyle,
  width: Dp,
  breakpoints: List<Breakpoint> = emptyList(),
  diagnostics: List<CellDiagnostic> = emptyList(),
  onGutterTap: (line: Int) -> Unit = {},
  onGutterLongPress: (line: Int) -> Unit = {},
  stoppedLine: Int? = null,
  modifier: Modifier = Modifier,
) {
  if (width <= 0.dp) return
  val metrics = rememberGutterMetrics(text, style, width, breakpoints, diagnostics)
  val heightDp = with(LocalDensity.current) { (layout?.size?.height ?: 0).toDp() }
  Box(
    modifier
      .width(width)
      .height(heightDp)
      .background(editorPalette.cellBackground)
      .gutterGestures(metrics, layout, onGutterTap, onGutterLongPress)
      .gutterDrawing(metrics, layout, stoppedLine)
      .diagnosticSemantics(diagnostics),
  )
}

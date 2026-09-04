package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalColors
import com.jupydroid.app.terminal.TerminalEmulator
import com.jupydroid.app.terminal.TerminalGrid
import com.jupydroid.app.terminal.TerminalRow
import com.jupydroid.app.ui.editor.codeTextStyle

/**
 * The grid [emulator] holds, drawn as batched-run glyphs on a `Canvas` - `docs/PLAN.md`'s P4i-2d,
 * replacing `TerminalPaneScreen`'s plain `Text`-per-row stand-in. Mirrors `TerminalRenderer.render`'s
 * own run-batching loop (termux-app `3df69d1d`, Apache 2.0 - see `TerminalGrid`'s own citation): a
 * run boundary is a style change, not a character change, so equal-styled neighbours draw in one
 * [drawText] call rather than one per cell. `GlyphStyle.kt` is where a run's [CellStyle] becomes a
 * resolved colour pair and a Compose [TextStyle]; this file is layout and drawing only.
 *
 * The caller is responsible for forcing a fresh draw on every mutation - `TerminalPaneScreen`'s own
 * `key(state.redraw)` wrapper, since `emulator.screen.grid` is mutated in place and this composable
 * has no state of its own to observe it by.
 *
 * The cell size is [terminalCellMetrics]'s real device measurement. [onGridSizeChanged] reports how
 * many whole cells this composable's own measured area holds - `docs/PLAN.md`'s P4i-3a - through a
 * `BoxWithConstraints` rather than a self-sized `Box`, since the grid this now draws is driven by
 * the container's real size instead of dictating it. **The IME is deliberately excluded, not
 * missed**: under `enableEdgeToEdge()` the window itself does not shrink for the keyboard
 * (`CellCompletions.kt`'s own finding), but `TerminalInputField`'s own `imePadding` still steals the
 * room back from this composable's `weight(1f)` sibling slot in `TerminalPaneScreen` - so
 * `WindowInsets.ime`'s current bottom inset is added back before the grid is computed, the same
 * technique `CellCompletions.kt` already uses rather than a new one. Resizing *for* the keyboard is
 * P4j's, by the plan's own words - not this composable reacting to a shrink that was never real.
 * Reporting is layout-only: the caller decides what a changed grid means
 * ([com.jupydroid.app.ui.terminal.TerminalPaneViewModel.resize] is `TerminalPaneScreen`'s), keeping
 * this file "layout and drawing only" as its own opening line still claims.
 *
 * **A transparent [Text] per row sits over the same grid, and is the point rather than an
 * accident.** `Canvas` carries no semantics of its own, so a screen reader - and every existing
 * test/Maestro flow built against `onNodeWithText` before this sub-step - would otherwise find
 * nothing here at all. `color = Color.Transparent` keeps the pixels the `Canvas` alone draws;
 * P4i-1's own reason for one node per row rather than one blob - a screen reader must not read a
 * whole screenful as one utterance - applies exactly as it did before this replaced `Text`s that
 * were themselves visible.
 *
 * ```
 * TerminalCanvas(
 *   emulator = viewModel.emulator,
 *   onGridSizeChanged = { grid, cell -> viewModel.resize(grid, cell.width, cell.height) },
 * )
 * ```
 */
@Composable
fun TerminalCanvas(
  emulator: TerminalEmulator,
  onGridSizeChanged: (grid: IntSize, cellPixels: IntSize) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
) {
  val cell = terminalCellMetrics()
  val style = codeTextStyle()
  val renderer = GlyphRenderer(cell, style, rememberTextMeasurer(), emulator.screen.colors)
  val screen = emulator.screen
  val imeBottomPixels = WindowInsets.ime.getBottom(LocalDensity.current)

  BoxWithConstraints(modifier = modifier) {
    val effectiveHeightPixels = constraints.maxHeight + imeBottomPixels
    val gridSize = terminalGridSize(IntSize(constraints.maxWidth, effectiveHeightPixels), cell)
    LaunchedEffect(gridSize, cell) { onGridSizeChanged(gridSize, cell) }

    Canvas(modifier = Modifier.matchParentSize()) {
      drawRect(Color(renderer.colors.at(CellStyle.COLOR_INDEX_BACKGROUND)))
      for (row in 0 until screen.rows) drawRow(screen.grid, row, screen.columns, renderer)
      if (emulator.isCursorEnabled) drawCursor(screen.cursor.row, screen.cursor.column, renderer)
    }
    Column {
      for (row in 0 until screen.rows) {
        Text(text = textOfRow(screen.grid.rowAt(row)), style = style, color = Color.Transparent)
      }
    }
  }
}

/** What every run needs to draw itself - one parameter instead of four, kept under detekt's cap. */
private class GlyphRenderer(
  val cell: IntSize,
  val style: TextStyle,
  val measurer: TextMeasurer,
  val colors: TerminalColors,
)

private fun DrawScope.drawRow(grid: TerminalGrid, rowIndex: Int, columns: Int, renderer: GlyphRenderer) {
  val row = grid.rowAt(rowIndex)
  var column = 0
  while (column < columns) {
    val runStyle = grid.styleAt(rowIndex, column)
    var end = column + 1
    while (end < columns && grid.styleAt(rowIndex, end) == runStyle) end++
    drawRun(row, rowIndex, column, end, runStyle, renderer)
    column = end
  }
}

/**
 * One run of equal-styled columns: a background rect only when it differs from the terminal's own
 * default (`TerminalRenderer.drawTextRun`'s own "only draw non-default background"), then the run's
 * text - skipped entirely under [CellStyle.INVISIBLE], which the reference also draws nothing for.
 */
private fun DrawScope.drawRun(
  row: TerminalRow,
  rowIndex: Int,
  startColumn: Int,
  endColumn: Int,
  runStyle: CellStyle,
  renderer: GlyphRenderer,
) {
  val topLeft = Offset((startColumn * renderer.cell.width).toFloat(), (rowIndex * renderer.cell.height).toFloat())
  val (foreColor, backColor) = resolvedRunColors(runStyle, renderer.colors)
  if (backColor != renderer.colors.at(CellStyle.COLOR_INDEX_BACKGROUND)) {
    val runWidth = ((endColumn - startColumn) * renderer.cell.width).toFloat()
    drawRect(Color(backColor), topLeft = topLeft, size = Size(runWidth, renderer.cell.height.toFloat()))
  }
  if (runStyle.effect and CellStyle.INVISIBLE != 0) return
  val text = textOf(row, startColumn, endColumn)
  drawText(renderer.measurer, text, topLeft, styleFor(renderer.style, runStyle, foreColor))
}

private fun DrawScope.drawCursor(row: Int, column: Int, renderer: GlyphRenderer) {
  val topLeft = Offset((column * renderer.cell.width).toFloat(), (row * renderer.cell.height).toFloat())
  val size = Size(renderer.cell.width.toFloat(), renderer.cell.height.toFloat())
  drawRect(Color(renderer.colors.at(CellStyle.COLOR_INDEX_CURSOR)), topLeft = topLeft, size = size)
}

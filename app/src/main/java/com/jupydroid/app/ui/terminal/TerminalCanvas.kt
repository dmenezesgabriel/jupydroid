package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
 * The cell size is [terminalCellMetrics]'s real device measurement, handed once to
 * [TerminalEmulator.resize] (P4i-2b's own entry point) so `CSI 14/16 t` answers with it too - P4i-3
 * is what drives that resize from the screen's own layout size and on every real size change; this
 * only needs it once, so the report and the grid agree from the first frame.
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
 * TerminalCanvas(emulator = viewModel.emulator)
 * ```
 */
@Composable
fun TerminalCanvas(emulator: TerminalEmulator, modifier: Modifier = Modifier) {
  val cell = terminalCellMetrics()
  val style = codeTextStyle()
  val renderer = GlyphRenderer(cell, style, rememberTextMeasurer(), emulator.screen.colors)
  val screen = emulator.screen

  LaunchedEffect(cell) { emulator.resize(screen.columns, screen.rows, cell.width, cell.height) }

  val density = LocalDensity.current
  val width = with(density) { (cell.width * screen.columns).toDp() }
  val height = with(density) { (cell.height * screen.rows).toDp() }
  Box(modifier = modifier.width(width).height(height)) {
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

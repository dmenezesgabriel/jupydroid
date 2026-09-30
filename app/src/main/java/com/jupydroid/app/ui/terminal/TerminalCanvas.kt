package com.jupydroid.app.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * The grid [emulator] holds, drawn as batched-run glyphs on a `Canvas` - 2d,
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
 * many whole cells this composable's own measured area holds - through a
 * `BoxWithConstraints` rather than a self-sized `Box`, since the grid this now draws is driven by
 * the container's real size instead of dictating it. **The IME genuinely shrinks it now -
 * 3b**: `imePadding()` sits on this composable's own `BoxWithConstraints`, the
 * same rule `TerminalExtraKeysRow`/`SymbolRow`/`NotebookBar` each already follow (their own inset,
 * not an ancestor's or a sibling's) - so a keyboard opening is just another size change, read by
 * the same [LaunchedEffect] that already answers a rotation, the way Termux's own
 * `TerminalView.onSizeChanged` -> `updateSize()` treats every size change alike.
 * P4i-3a's own compensation - adding the IME's bottom inset back before computing the grid - answered
 * a `Column` layout this file no longer has: `TerminalCanvas` and the input field were a weighted
 * `Column`, so the field's own `imePadding` shrank its `weight(1f)` sibling's incoming constraints
 * too (`docs/DECISIONS.md`, "`imePadding` on a shared ancestor pulls a sibling's own height budget
 * negative"). P4j-1 replaced that `Column` with a `Box` - independent overlapping siblings, where
 * one child's own `imePadding` shrinks only its own layout - so the compensation was left cancelling
 * out a squeeze that could no longer reach it, measured actually inflating the grid past what the
 * pty had ever been told (`docs/DECISIONS.md`'s own P4j-3b entry).
 *
 * **The analogy two paragraphs up was wrong, and it shipped a doubled IME inset.** This
 * composable is `TerminalPaneScreen`'s *content*, not a bottom bar - `TerminalExtraKeysRow` is the
 * bar, and it is the one `EditorScreen.kt`'s own rule names: *"every other surface that can be on
 * screen under an IME carries its own `imePadding`... so the cell list has never had to"* (the
 * main content does, unless no bottom bar exists at all). `TerminalPaneScreen` always has one, so
 * this file's own `imePadding` here was reserving the keyboard's height a second time on top of
 * what `Scaffold`'s content padding already reflects from the bar's own growth - measured on
 * device, not re-read from source, at a 27-row shrink where one keyboard's worth is closer to 19
 * (`TerminalPaneScreenTest`, the finding about the double-counted IME inset). Left
 * unchanged here on purpose: `TerminalPaneScreen` now consumes the inset before this composable
 * ever sees it (`Modifier.consumeWindowInsets(WindowInsets.ime)`), so this `imePadding` becomes a
 * no-op there while staying correct for this composable's own isolated test and any other caller
 * with no bottom bar of its own.
 *
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
 *
 * @param textStyle [codeTextStyle]'s own default for every other caller; the terminal's own
 *   font-size setting is what `TerminalPaneScreen` resolves and passes
 *   here instead, one style shared by both [terminalCellMetrics]'s measurement and the glyphs
 *   themselves so the two can never drift apart.
 * @param topRow the external row this draws at its own top edge - `0` is the live screen
 *   (every existing caller's default), negative is scrollback. The
 *   cursor is drawn only at `topRow == 0`: its own row is always among the *live* screen's
 *   external rows, so scrolling away from `0` moves it out of the window this draws, the same
 *   side effect Termux's own `TerminalRenderer.render` gets for free from iterating
 *   `[topRow, topRow + rows)` - made an explicit condition here since this file's loop bounds
 *   are a parameter now rather than always `0`.
 */
@Composable
fun TerminalCanvas(
  emulator: TerminalEmulator,
  onGridSizeChanged: (grid: IntSize, cellPixels: IntSize) -> Unit = { _, _ -> },
  modifier: Modifier = Modifier,
  textStyle: TextStyle = codeTextStyle(),
  topRow: Int = 0,
) {
  val cell = terminalCellMetrics(textStyle)
  val renderer = GlyphRenderer(cell, textStyle, rememberTextMeasurer(), emulator.screen.colors)
  val screen = emulator.screen

  BoxWithConstraints(modifier = modifier.imePadding()) {
    val gridSize = terminalGridSize(IntSize(constraints.maxWidth, constraints.maxHeight), cell)
    LaunchedEffect(gridSize, cell) { onGridSizeChanged(gridSize, cell) }

    Canvas(modifier = Modifier.matchParentSize()) {
      drawRect(Color(renderer.colors.at(CellStyle.COLOR_INDEX_BACKGROUND)))
      for (row in topRow until topRow + screen.rows) drawRow(screen.grid, row, topRow, screen.columns, renderer)
      if (topRow == 0 && emulator.isCursorEnabled) drawCursor(screen.cursor.row, screen.cursor.column, renderer)
    }
    Column {
      for (row in topRow until topRow + screen.rows) {
        Text(text = textOfRow(screen.grid.rowAt(row)), style = textStyle, color = Color.Transparent)
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

/**
 * [rowIndex] is the external row read from [grid] (negative once scrolled back); [topRow] is
 * what [TerminalCanvas] currently has at its own top edge, so `rowIndex - topRow` - never
 * negative, since the loop that calls this never passes a [rowIndex] below [topRow] - is where
 * this actually draws, the pixel row staying `0`-based regardless of how far into scrollback
 * [rowIndex] reaches.
 */
private fun DrawScope.drawRow(grid: TerminalGrid, rowIndex: Int, topRow: Int, columns: Int, renderer: GlyphRenderer) {
  val row = grid.rowAt(rowIndex)
  val drawRowIndex = rowIndex - topRow
  var column = 0
  while (column < columns) {
    val runStyle = grid.styleAt(rowIndex, column)
    var end = column + 1
    while (end < columns && grid.styleAt(rowIndex, end) == runStyle) end++
    drawRun(row, drawRowIndex, column, end, runStyle, renderer)
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

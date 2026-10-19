package com.jupydroid.app.ui.terminal

import com.jupydroid.app.richtext.plainTextWebLinks
import com.jupydroid.app.terminal.TerminalGrid
import com.jupydroid.app.terminal.hyperlinkAt

/**
 * Where the web address printed over ([column], [row]) goes, or `null` when the cell is in none -
 * what a tap on the terminal opens, after asking (`TerminalOpenLinkDialog`).
 *
 * The cell's whole logical line is searched, every row a wrap joins to it, so an address a program
 * printed wider than the screen is one link from any of its rows. The pattern is the one cell
 * outputs link with ([plainTextWebLinks]), JupyterLab's, as its terminal finds printed addresses
 * through `@xterm/addon-web-links` (jupyterlab@7f0e6906 packages/terminal/src/widget.ts). Here, not
 * in `terminal/`, so the emulator depends on no renderer; an extension because [TerminalGrid] is at
 * detekt's `TooManyFunctions` cap.
 *
 * ```
 * grid.webLinkAt(column = 5, row = 0)   // "http://x.y" over "a http://x.y"
 * ```
 */
fun TerminalGrid.webLinkAt(column: Int, row: Int): String? {
  if (selectedText(column, row, column, row).isBlank()) return null
  val first = firstRowOfLine(row)
  val line = selectedText(0, first, columns - 1, lastRowOfLine(row))
  // The read ends on the tapped cell, which prints, so no trailing space is dropped from it.
  val tapped = selectedText(0, first, column, row).length - 1
  return line.plainTextWebLinks().firstOrNull { tapped >= it.start && tapped < it.end }?.destination
}

/**
 * What a tap on ([column], [row]) opens, after asking: the cell's own `OSC 8` link when it is a web
 * one, otherwise the address printed over it ([webLinkAt]), otherwise `null`.
 *
 * An `OSC 8` URI is whatever the program wrote, so only `http` and `https` are followed, which is
 * JupyterLab's `ALLOWED_LINK_SCHEMES` (jupyterlab@7f0e6906 packages/terminal/src/widget.ts). A link
 * of another scheme is not offered, and the text under it is read as printed text instead.
 *
 * ```
 * grid.linkToOpenAt(column = 0, row = 0)   // "https://a.b" over "docs" written under OSC 8
 * ```
 */
fun TerminalGrid.linkToOpenAt(column: Int, row: Int): String? =
  hyperlinkAt(column, row)?.takeIf { it.substringBefore(':').lowercase() in WEB_SCHEMES }
    ?: webLinkAt(column, row)

private val WEB_SCHEMES = setOf("http", "https")

private fun TerminalGrid.firstRowOfLine(row: Int): Int {
  var first = row
  while (first > -activeTranscriptRows && rowAt(first - 1).lineWrap) first--
  return first
}

private fun TerminalGrid.lastRowOfLine(row: Int): Int {
  var last = row
  while (last < screenRows - 1 && rowAt(last).lineWrap) last++
  return last
}

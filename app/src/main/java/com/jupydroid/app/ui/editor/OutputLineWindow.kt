package com.jupydroid.app.ui.editor

/**
 * The part of a long text output that is drawn: `text[0, headEnd)` and `text[tailStart, end)`,
 * with [hiddenLines] lines between them left out of the layout - never out of the model, so a save,
 * a search and the collapsed preview still read every line.
 */
data class OutputLineWindow(val headEnd: Int, val tailStart: Int, val hiddenLines: Int)

/** Past this many lines a text output draws its head and tail - `notebook.output.textLineLimit`'s default. */
const val OUTPUT_LINE_LIMIT = 30

/** How many of the last lines stay drawn - the `5` in `truncatedArrayOfString`. */
private const val OUTPUT_TAIL_LINES = 5

/**
 * The window over [text], or null when it has [limit] lines or fewer and is drawn whole.
 *
 * VS Code's cut: the first `limit - 5` lines and the last 5, the rest behind a "view more"
 * (vscode@3f3380c6 extensions/notebook-renderers/src/textHelper.ts, `truncatedArrayOfString`).
 * Its split counts the empty string after a final line feed as a line; here a final `\n` ends the
 * last line instead, because every `print` leaves one and would otherwise spend a tail row on
 * nothing.
 *
 * ```kotlin
 * outputLineWindowOf((0 until 1000).joinToString("\n"))  // head 25 lines, 970 hidden, tail 5
 * ```
 */
fun outputLineWindowOf(text: String, limit: Int = OUTPUT_LINE_LIMIT): OutputLineWindow? {
  val lineCount = text.count { it == '\n' } + if (text.isEmpty() || text.endsWith('\n')) 0 else 1
  if (lineCount <= limit) return null
  return OutputLineWindow(
    headEnd = text.lineFeedAt(limit - OUTPUT_TAIL_LINES - 1),
    tailStart = text.lineFeedAt(lineCount - OUTPUT_TAIL_LINES - 1) + 1,
    hiddenLines = lineCount - limit,
  )
}

/** The index of the [ordinal]th `\n`, from zero - walked rather than listed, as a runaway has 10⁵ of them. */
private fun String.lineFeedAt(ordinal: Int): Int {
  var at = -1
  repeat(ordinal + 1) { at = indexOf('\n', at + 1) }
  return at
}

/** What the row between head and tail says - `hiddenCellsLabel`'s sentence, counting lines. */
fun hiddenLinesLabel(count: Int): String = if (count == 1) "1 line hidden" else "$count lines hidden"

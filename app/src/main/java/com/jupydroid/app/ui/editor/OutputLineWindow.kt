package com.jupydroid.app.ui.editor

/**
 * The part of a long text output that is drawn: `text[0, headEnd)` and `text[tailStart, end)`,
 * with [hiddenLines] lines between them left out of the layout - never out of the model, so a save,
 * a search and the collapsed preview still read every line.
 */
data class OutputLineWindow(val headEnd: Int, val tailStart: Int, val hiddenLines: Int)

/**
 * Whether any of [range] falls between head and tail, where nothing is drawn - so a search hit cut
 * in half by the window counts as hidden, since half a match drawn is not the match.
 *
 * ```kotlin
 * outputLineWindowOf(text)?.hides(hit.range)  // true for a hit on line 500 of 1000
 * ```
 */
fun OutputLineWindow.hides(range: IntRange): Boolean = range.first < tailStart && range.last >= headEnd

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

/**
 * How many lines one `Text` holds when an output is drawn whole. Android's `Layout.drawText` hands
 * the `Text`'s whole string to native code for every line it draws (`GetStringChars`), so one
 * `Text` of 200k lines costs lines x length per draw; a chunk bounds the length.
 */
const val OUTPUT_CHUNK_LINES = 1000

/**
 * Where [text] is split to be drawn whole, [linesPerChunk] lines per range. Each range stops before
 * the `\n` that ends its last line - the seam between two stacked `Text`s, as between head and tail -
 * and the last runs to the end, so a final `\n` stays its own and opens no empty chunk.
 *
 * ```kotlin
 * outputTextChunksOf("a\nb\nc\n", linesPerChunk = 2)  // [0..2, 4..5]: "a\nb", "c\n"
 * ```
 */
fun outputTextChunksOf(text: String, linesPerChunk: Int = OUTPUT_CHUNK_LINES): List<IntRange> {
  val chunks = mutableListOf<IntRange>()
  var start = 0
  var seam = text.lineFeedAfter(start, linesPerChunk)
  while (seam != -1 && seam < text.length - 1) {
    chunks += start until seam
    start = seam + 1
    seam = text.lineFeedAfter(start, linesPerChunk)
  }
  return chunks + listOf(start until text.length)
}

/** The [ordinal]th `\n` at or after [from], counting from one, or -1 when the text runs out first. */
private fun String.lineFeedAfter(from: Int, ordinal: Int): Int {
  var at = from - 1
  repeat(ordinal) { at = indexOf('\n', at + 1).takeIf { it != -1 } ?: return -1 }
  return at
}

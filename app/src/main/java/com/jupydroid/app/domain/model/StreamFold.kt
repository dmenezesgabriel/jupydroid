package com.jupydroid.app.domain.model

/**
 * Adds [output] to a cell's outputs the way JupyterLab's output model does: a stream joins the
 * stream before it when their names match, and `\r`/`\b`/`\n` are interpreted as it lands, so a
 * progress bar rewrites its own line instead of leaving one output per repaint
 * (jupyterlab@7f0e690 packages/outputarea/src/model.ts, `_add` and `Private.processText`).
 *
 * The cursor is carried across chunks, as JupyterLab carries `_streamIndex`, rather than re-running
 * a stateless pass over the joined text as VS Code does (vscode@3f3380c
 * src/vs/workbench/contrib/notebook/common/notebookCommon.ts, `formatStreamText`). The two disagree
 * once a `\r` ends a chunk: `"abcdef\r"`, `"xy"`, `"z"` is `xyzdef` here and `xycdefz` there
 * (StreamFoldTest.theCursorCarriesAcrossChunks).
 * JupyterLab's answer is the one a notebook saved here must match, and each chunk costs only its
 * own length.
 *
 * The match is on the last output alone: JupyterLab's `_lastStreamName` resets on every
 * non-stream output and on `clear()`, and ours empties the list where that one resets the name.
 *
 * ```kotlin
 * listOf(CellOutput.Stream(STDOUT, "\r0%")).withOutputFolded(CellOutput.Stream(STDOUT, "\r50%"))
 * // == listOf(CellOutput.Stream(STDOUT, "50%"))
 * ```
 */
fun List<CellOutput>.withOutputFolded(output: CellOutput): List<CellOutput> {
  val last = lastOrNull()
  return when {
    output !is CellOutput.Stream -> this + output
    last is CellOutput.Stream && last.name == output.name -> dropLast(1) + last.overwrittenBy(output.text)
    else -> this + CellOutput.Stream(output.name, "", cursor = 0).overwrittenBy(output.text)
  }
}

/** The characters `processText` acts on; everything between them is a run of text. */
private val STREAM_CONTROLS = charArrayOf('\n', '\b', '\r')

/**
 * Text runs, and each control character alone - the units `processText` walks in turn.
 *
 * Scanned by index rather than with `Regex.findAll`, which makes a `Matcher` per match - and an
 * Android `Matcher` copies its whole input into ICU, so a chunk cost tokens x its own length on a
 * device (StreamFoldOnArtTest.oneMessageOfManyLinesFoldsInLinearTime).
 */
private fun streamTokensOf(chunk: String): Sequence<String> = sequence {
  var start = 0
  while (start < chunk.length) {
    val control = chunk.indexOfAny(STREAM_CONTROLS, start)
    val end = when (control) {
      -1 -> chunk.length
      start -> start + 1
      else -> control
    }
    yield(chunk.substring(start, end))
    start = end
  }
}

/**
 * Applies a chunk's tokens to one buffer, so the stream's text is copied once per message rather
 * than once per token - a print loop is two tokens a line, and rebuilding the whole text for each
 * froze the editor for minutes on 200k lines (StreamFoldTest.aRunawayPrintFoldsInLinearTime).
 *
 * Fixes: 6afbdf9a5dda ("feat(editor): adjacent streams fold and \r rewrites the line")
 */
private fun CellOutput.Stream.overwrittenBy(chunk: String): CellOutput.Stream {
  val buffer = StringBuilder(text)
  val landed = streamTokensOf(chunk).fold(cursor) { at, token -> buffer.applying(token, at) }
  return copy(text = buffer.toString(), cursor = landed)
}

/** One token applied in place; returns where the cursor lands. */
private fun StringBuilder.applying(token: String, cursor: Int): Int = when (token) {
  "\n" -> append('\n').length
  "\r" -> lastIndexOf("\n", cursor - 1) + 1
  "\b" -> backspaced(cursor)
  else -> {
    // `replace` clamps its end to the length, as `slice` does in JupyterLab's `processText`.
    replace(cursor, cursor + token.length, token)
    cursor + token.length
  }
}

/**
 * Removes the character before the cursor, unless that is a line feed. Mid-line this also drops
 * the character *at* the cursor, which is JupyterLab's own slice
 * (`text.slice(0, idx0 - 1) + text.slice(idx0 + 1)`), kept so a notebook saved here holds the text
 * JupyterLab would have saved. At the end of the text - every progress bar - the two agree.
 */
private fun StringBuilder.backspaced(cursor: Int): Int {
  if (cursor == 0 || this[cursor - 1] == '\n') return cursor
  delete(cursor - 1, cursor + 1)
  return cursor - 1
}

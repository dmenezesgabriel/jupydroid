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

/** Text runs, and each control character alone - the units `processText` walks in turn. */
private val STREAM_TOKENS = Regex("[\n\b\r]|[^\n\b\r]+")

private fun CellOutput.Stream.overwrittenBy(chunk: String): CellOutput.Stream =
  STREAM_TOKENS.findAll(chunk).fold(this) { stream, token -> stream.applying(token.value) }

private fun CellOutput.Stream.applying(token: String): CellOutput.Stream = when (token) {
  "\n" -> copy(text = text + "\n", cursor = text.length + 1)
  "\r" -> copy(cursor = text.lastIndexOf('\n', cursor - 1) + 1)
  "\b" -> backspaced()
  else -> copy(
    text = text.take(cursor) + token + text.drop(cursor + token.length),
    cursor = cursor + token.length,
  )
}

/**
 * Removes the character before the cursor, unless that is a line feed. Mid-line this also drops
 * the character *at* the cursor, which is JupyterLab's own slice
 * (`text.slice(0, idx0 - 1) + text.slice(idx0 + 1)`), kept so a notebook saved here holds the text
 * JupyterLab would have saved. At the end of the text - every progress bar - the two agree.
 */
private fun CellOutput.Stream.backspaced(): CellOutput.Stream {
  if (cursor == 0 || text[cursor - 1] == '\n') return this
  return copy(text = text.take(cursor - 1) + text.drop(cursor + 1), cursor = cursor - 1)
}

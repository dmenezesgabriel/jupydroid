package com.jupydroid.app.terminal

/** Characters [start] until [end] of an [SgrText]'s text, drawn with [style]. */
data class SgrRun(val start: Int, val end: Int, val style: CellStyle)

/**
 * Text that coloured itself with `ESC [ … m`, as what it says and how each stretch of it is drawn -
 * a traceback, a `print("\x1b[31m…")`, a `__repr__` from `rich`.
 *
 * **The terminal's own SGR walk, not a second parser.** Each sequence goes through
 * [EscapeParameters] and [GraphicRendition] exactly as the emulator's do, so 16, 256 and
 * truecolour, sub-parameters and the malformed forms all mean here what they mean in a shell.
 * What differs is only what the text is laid into: a string, not a grid.
 *
 * Every control sequence that is not a colour is dropped, since nothing in an output can move a
 * cursor or erase a line - `ansiSpan`'s choice (jupyterlab@7f0e690
 * `packages/rendermime/src/renderers.ts`). A sequence the text ends inside is kept as written, as
 * `handleANSIOutput` keeps it (vscode@3f3380c6 `extensions/notebook-renderers/src/ansi.ts`).
 * Stretches in [CellStyle.NORMAL] carry no run.
 *
 * ```
 * SgrText.parse("\u001b[31mred\u001b[0m")   // SgrText("red", [SgrRun(0, 3, red)])
 * ```
 */
data class SgrText(val text: String, val runs: List<SgrRun>) {
  /** The text without its trailing whitespace, every run clipped to what is left of it. */
  fun trimEnd(): SgrText {
    val kept = text.trimEnd()
    val clipped = runs.mapNotNull { run -> run.copy(end = minOf(run.end, kept.length)).takeIf { it.start < it.end } }
    return SgrText(kept, clipped)
  }

  companion object {
    /** [raw] split into its text and the runs its escapes describe. */
    fun parse(raw: String): SgrText = SgrReader(raw).read()
  }
}

/** One pass over the raw text; the style and where its stretch began are the walk's state. */
private class SgrReader(private val raw: String) {
  private val text = StringBuilder()
  private val runs = mutableListOf<SgrRun>()
  private val parameters = EscapeParameters()
  private var style = CellStyle.NORMAL
  private var runStart = 0

  fun read(): SgrText {
    var at = 0
    while (at < raw.length) {
      val final = finalByteOfSequenceAt(at)
      if (final < 0) text.append(raw[at]) else apply(raw.substring(at + INTRODUCER_LENGTH, final), raw[final])
      at = if (final < 0) at + 1 else final + 1
    }
    closeRun()
    return SgrText(text.toString(), runs.toList())
  }

  /**
   * Where the control sequence starting at [at] ends, or -1 when none starts there or it never
   * ends. A byte outside ECMA-48's parameter, intermediate and final ranges ends the search: a
   * newline inside is the text going on, not more of the sequence.
   */
  private fun finalByteOfSequenceAt(at: Int): Int {
    if (raw[at] != ESC || raw.getOrNull(at + 1) != '[') return -1
    val end = (at + INTRODUCER_LENGTH until raw.length).firstOrNull { raw[it] !in PARAMETER_AND_INTERMEDIATE_BYTES }
    return end?.takeIf { raw[it] in FINAL_BYTES } ?: -1
  }

  /** A colour when it is a plain `m` - digits and separators only, no private marker - else nothing. */
  private fun apply(body: String, final: Char) {
    if (final != 'm' || body.any { it !in SGR_PARAMETER_BYTES }) return
    parameters.startSequence()
    body.forEach { parameters.accept(it.code) }
    val next = GraphicRendition.applied(parameters, style)
    if (next == style) return
    closeRun()
    style = next
    runStart = text.length
  }

  /** Records the stretch the current style covered, joining the run before it when they touch. */
  private fun closeRun() {
    if (style == CellStyle.NORMAL || runStart == text.length) return
    val previous = runs.lastOrNull()
    if (previous != null && previous.end == runStart && previous.style == style) {
      runs[runs.lastIndex] = previous.copy(end = text.length)
      return
    }
    runs += SgrRun(runStart, text.length, style)
  }

  private companion object {
    const val ESC = '\u001b'

    /** `ESC [`. */
    const val INTRODUCER_LENGTH = 2

    val FINAL_BYTES = '@'..'~'
    val PARAMETER_AND_INTERMEDIATE_BYTES = ' '..'?'
    val SGR_PARAMETER_BYTES = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ';', ':')
  }
}

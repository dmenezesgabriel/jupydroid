package com.jupydroid.app.terminal

/**
 * Which of the two graphic sets is invoked, and what the DEC Special Graphics set draws.
 *
 * The state is `TerminalEmulator`'s `mUseLineDrawingG0` / `mUseLineDrawingG1` /
 * `mUseLineDrawingUsesG0` (termux-app `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under
 * that repository's `LICENSE.md` exception); the shape is JediTerm's `GraphicSetState`
 * (`jediterm@377b76e6`, Apache 2.0 at the reader's option per its `README.md`),
 * which keeps the designation apart from the parser. Two graphic sets rather than ISO 2022's four:
 * that is the whole of what `ESC ( C` and `ESC ) C` can designate here, as it is upstream.
 *
 * **The table is 0x5F..0x7E, blank first** - see `LineDrawingCharsetTest` for the three positions
 * where the two references disagree and how `vttest`'s own character-set page settles each.
 *
 * ```
 * val charset = LineDrawingCharset()
 * charset.designateG0(useLineDrawing = true)
 * charset.substitute('q'.code)   // '─'.code
 * ```
 */
class LineDrawingCharset {
  private var g0IsLineDrawing = false
  private var g1IsLineDrawing = false
  private var invokedIsG0 = true

  /** `ESC ( C` - designate G0, which is line drawing when `C` is `0`. */
  fun designateG0(useLineDrawing: Boolean) {
    g0IsLineDrawing = useLineDrawing
  }

  /** `ESC ) C` - designate G1. */
  fun designateG1(useLineDrawing: Boolean) {
    g1IsLineDrawing = useLineDrawing
  }

  /** SI invokes G0 into GL, SO invokes G1 - the locking shifts. */
  fun mapToGL(graphicSet: Int) {
    invokedIsG0 = graphicSet == 0
  }

  /** What [codePoint] draws as, given whichever set is invoked. */
  fun substitute(codePoint: Int): Int {
    val drawing = if (invokedIsG0) g0IsLineDrawing else g1IsLineDrawing
    val drawable = drawing && codePoint in FIRST..LAST
    return if (drawable) GRAPHICS[codePoint - FIRST].code else codePoint
  }

  /** What DECSC puts away of this state, and [restore] puts back. */
  fun snapshot(): GraphicSetState = GraphicSetState(g0IsLineDrawing, g1IsLineDrawing, invokedIsG0)

  fun restore(state: GraphicSetState) {
    g0IsLineDrawing = state.g0IsLineDrawing
    g1IsLineDrawing = state.g1IsLineDrawing
    invokedIsG0 = state.invokedIsG0
  }

  /** Back to ASCII in both sets, with G0 invoked. */
  fun reset() = restore(GraphicSetState.ASCII)

  private companion object {
    /** `_`, whose graphic is the blank. */
    const val FIRST = 0x5F

    /** `~`, whose graphic is the centred dot. */
    const val LAST = 0x7E

    /** The 32 graphics of `FIRST..LAST`, in order. */
    const val GRAPHICS = " ◆▒␉␌␍␊°±␤␋┘┐┌└┼⎺⎻─⎼⎽├┤┴┬│≤≥π≠£·"
  }
}

/**
 * The three flags of a [LineDrawingCharset], as one value DECSC can put away.
 *
 * The reference keeps `mUseLineDrawingG0` / `mUseLineDrawingG1` / `mUseLineDrawingUsesG0` on both
 * the emulator and its `SavedScreenState`, copying each field twice per save and restore; named,
 * the two halves cannot drift apart. The name is JediTerm's `GraphicSetState`.
 */
data class GraphicSetState(val g0IsLineDrawing: Boolean, val g1IsLineDrawing: Boolean, val invokedIsG0: Boolean) {
  companion object {
    /** What a terminal starts in, and what a reset puts it back to. */
    val ASCII = GraphicSetState(g0IsLineDrawing = false, g1IsLineDrawing = false, invokedIsG0 = true)
  }
}

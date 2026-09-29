package com.jupydroid.app.ui.editor

/** The code and cursor a completion was asked about, kept so a stale answer can be recognised. */
data class CompletionQuery(val code: String, val cursorOffset: Int)

/**
 * What to ask the kernel about, or null when the cursor is nowhere worth asking about.
 *
 * A round trip per keystroke is not free - the kernel's shell channel is serial - so this is
 * the rule that decides when one is worth making. It is only a *trigger*; how much of the code
 * a chosen match replaces is the kernel's answer to give, in `CompletionResult`.
 *
 * ```
 * completionQueryAt("np.ar", cursor = 5)  // CompletionQuery("np.ar", 5)
 * ```
 */
fun completionQueryAt(code: String, cursor: Int): CompletionQuery? {
  if (cursor !in 0..code.length) return null
  // At the end of the identifier, not inside it: completing from the middle would replace the
  // half the user meant to keep.
  val nextIsMoreOfTheWord = cursor < code.length && code[cursor].isIdentifierChar()
  val typed = code.take(cursor).takeLastWhile { it.isIdentifierChar() }
  return if (nextIsMoreOfTheWord || typed.length < MIN_PREFIX) null else CompletionQuery(code, cursor)
}

/**
 * The identifier ending at [cursor] as (start, endExclusive) code-unit offsets - the span a
 * completion match with no `textEdit` of its own replaces (`NotebookCompletion`). Null
 * when the cursor sits on no word, which makes the match a pure insertion.
 */
fun identifierSpanEndingAt(code: String, cursor: Int): Pair<Int, Int>? {
  if (cursor !in 0..code.length) return null
  val start = cursor - code.take(cursor).takeLastWhile { it.isIdentifierChar() }.length
  return if (start == cursor) null else start to cursor
}

/** A dot is not one, which is what makes `np.ar` a question about `ar`. */
private fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_'

/** One letter matches most of a kernel's namespace, so it is a round trip that buys nothing. */
private const val MIN_PREFIX = 2

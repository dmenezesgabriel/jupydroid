package com.jupydroid.app.syntax

/** A bracket and its partner, as offsets into the source they were found in. */
data class BracketPair(val open: Int, val close: Int)

/**
 * The bracket the cursor is touching and the one that closes it, or null when it touches none
 * or the partner is missing.
 *
 * Takes the [spans] the highlighter already produced rather than lexing again, and that is what
 * keeps `print("(")` down to one pair: a bracket inside a string or a comment is punctuation,
 * not structure. An f-string interpolation is deliberately *not* string - see [PythonSyntax] -
 * so brackets inside one still count.
 *
 * ```
 * bracketPairAround("f(x)", cursor = 4, spans = PythonSyntax().spansOf("f(x)"))  // (1, 3)
 * ```
 */
fun bracketPairAround(source: String, cursor: Int, spans: List<SyntaxSpan>): BracketPair? {
  val literal = literalMask(source.length, spans)
  // Behind the cursor first: that is the bracket just typed, and the one editors highlight.
  return pairAt(source, cursor - 1, literal) ?: pairAt(source, cursor, literal)
}

private fun pairAt(source: String, at: Int, literal: BooleanArray): BracketPair? {
  if (at !in source.indices || literal[at]) return null
  val here = source[at]
  return when (here) {
    in CLOSER_OF -> partnerOf(source, at..source.lastIndex, here, CLOSER_OF.getValue(here), literal)
      ?.let { BracketPair(open = at, close = it) }
    in OPENER_OF -> partnerOf(source, at downTo 0, here, OPENER_OF.getValue(here), literal)
      ?.let { BracketPair(open = it, close = at) }
    else -> null
  }
}

/**
 * Walks [scan] counting depth, so a partner is only the one at the same nesting level. Starts on
 * the bracket itself, which is what takes the count from 0 to 1.
 */
private fun partnerOf(source: String, scan: IntProgression, same: Char, partner: Char, literal: BooleanArray): Int? {
  var depth = 0
  for (i in scan) {
    if (literal[i]) continue
    if (source[i] == same) {
      depth++
    } else if (source[i] == partner && --depth == 0) {
      return i
    }
  }
  return null
}

/** Which offsets are inside a string or a comment, and so hold punctuation rather than structure. */
private fun literalMask(length: Int, spans: List<SyntaxSpan>): BooleanArray {
  val mask = BooleanArray(length)
  spans.filter { it.kind == SyntaxKind.STRING || it.kind == SyntaxKind.COMMENT }
    .forEach { span -> for (i in span.start..<minOf(span.end, length)) mask[i] = true }
  return mask
}

private val CLOSER_OF = mapOf('(' to ')', '[' to ']', '{' to '}')
private val OPENER_OF = CLOSER_OF.entries.associate { (open, close) -> close to open }

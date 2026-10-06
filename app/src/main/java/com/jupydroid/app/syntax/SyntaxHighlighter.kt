package com.jupydroid.app.syntax

/**
 * The colourable categories a cell's text falls into - the shared vocabulary of every language
 * the editor highlights, with each highlighter emitting only the subset that means something to
 * it. Python never produces a [HEADING]; markdown never produces a [KEYWORD].
 *
 * Deliberately few per language: a cell is not an IDE.
 */
enum class SyntaxKind {
  PLAIN,

  // Python
  KEYWORD,
  STRING,
  NUMBER,
  COMMENT,
  FUNCTION,

  // Markdown. A heading is not a keyword and a quote is not a comment, so they are named for
  // what they are rather than mapped onto the Python set.
  HEADING,
  EMPHASIS,
  STRONG,
  CODE,
  LINK,
  QUOTE,

  /** A bullet or number that introduces a list item. */
  MARKER,

  // Shell. An expansion or an assignment's name - what the reference's shell mode styles `def`.
  VARIABLE,
}

/** A half-open range of the source, `[start, end)`, and what it is. */
data class SyntaxSpan(val start: Int, val end: Int, val kind: SyntaxKind)

/**
 * Turns source into spans a renderer can colour.
 *
 * The seam that keeps the editor from knowing which language it is showing, or how the
 * highlighting is produced - today an ANTLR-generated lexer, later possibly something else
 * for another language.
 *
 * ```
 * PythonSyntax().spansOf("x = 42")
 * ```
 */
fun interface SyntaxHighlighter {
  fun spansOf(source: String): List<SyntaxSpan>
}

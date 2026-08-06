package com.jupydroid.app.syntax

/** The colourable categories a cell's text falls into. Deliberately few: a cell is not an IDE. */
enum class SyntaxKind { PLAIN, KEYWORD, STRING, NUMBER, COMMENT, FUNCTION }

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

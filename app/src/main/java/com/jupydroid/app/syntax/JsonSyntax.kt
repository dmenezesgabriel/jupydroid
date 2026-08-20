package com.jupydroid.app.syntax

/**
 * JSON, hand-lexed: strings, numbers, and the three literals.
 *
 * Not ANTLR like [PythonSyntax], because JSON has no ambiguity to resolve and a generated parser
 * would refuse a file the moment it stopped being valid - which is precisely when someone is
 * editing it. This scans and never fails: an unterminated string is coloured to the end of the
 * input and the caller still gets a rendered file.
 *
 * Structure - braces, brackets, commas, colons - is left plain. It carries no meaning a colour
 * would add, and the reference's own mode gives it no class of its own either.
 *
 * ```
 * JsonSyntax().spansOf("""{"n": 1}""")
 * ```
 */
class JsonSyntax : SyntaxHighlighter {
  override fun spansOf(source: String): List<SyntaxSpan> {
    val spans = mutableListOf<SyntaxSpan>()
    var at = 0
    while (at < source.length) {
      at = when {
        source[at] == '"' -> spans.addString(source, at)
        source.startsWithNumberAt(at) -> spans.addNumber(source, at)
        else -> spans.addLiteralOrSkip(source, at)
      }
    }
    return spans
  }
}

/** From the opening quote to the closing one, or to the end when there is not a closing one. */
private fun MutableList<SyntaxSpan>.addString(source: String, start: Int): Int {
  var at = start + 1
  while (at < source.length) {
    // A backslash consumes whatever follows it, so `\"` stays inside the string.
    if (source[at] == '\\') {
      at++
    } else if (source[at] == '"') {
      break
    }
    at++
  }
  val end = minOf(at + 1, source.length)
  add(SyntaxSpan(start, end, SyntaxKind.STRING))
  return end
}

private fun MutableList<SyntaxSpan>.addNumber(source: String, start: Int): Int {
  var at = start + 1
  while (at < source.length && (source[at].isDigit() || source[at] in NUMBER_TAIL)) at++
  add(SyntaxSpan(start, at, SyntaxKind.NUMBER))
  return at
}

/** `true`, `false` and `null` - the reference's mode calls them atoms and colours them as such. */
private fun MutableList<SyntaxSpan>.addLiteralOrSkip(source: String, at: Int): Int {
  val literal = LITERALS.firstOrNull { source.startsWith(it, at) && !source.isWordAt(at + it.length) }
  if (literal == null) return at + 1
  add(SyntaxSpan(at, at + literal.length, SyntaxKind.KEYWORD))
  return at + literal.length
}

/** A leading `-` counts, so `-1` is one number rather than punctuation and a number. */
private fun String.startsWithNumberAt(at: Int): Boolean =
  this[at].isDigit() || (this[at] == '-' && at + 1 < length && this[at + 1].isDigit())

private fun String.isWordAt(at: Int): Boolean = at < length && (this[at].isLetterOrDigit() || this[at] == '_')

/** `.` for the fraction, `eE` for the exponent, `+-` for its sign. */
private const val NUMBER_TAIL = ".eE+-"

private val LITERALS = listOf("true", "false", "null")

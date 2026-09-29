package com.jupydroid.app.syntax

import com.jupydroid.app.syntax.python.PythonLexer
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token

/**
 * Python highlighting from the ANTLR grammar vendored in `tools/grammars/python`.
 *
 * The lexer is generated at build time and never written by hand - see ADR-0038, "The
 * lexer is generated, never hand-written". This class only maps token types onto
 * [SyntaxKind]; nothing here parses anything.
 *
 * Comments arrive on the hidden channel rather than the default one, which is exactly why
 * the `python3_14` grammar was chosen: the older one discards them.
 */
class PythonSyntax : SyntaxHighlighter {
  override fun spansOf(source: String): List<SyntaxSpan> {
    if (source.isEmpty()) return emptyList()
    var previous = Token.INVALID_TYPE
    return tokensOf(source).mapNotNull { token ->
      val kind = kindOf(token.type, previous)
      if (token.channel == Token.DEFAULT_CHANNEL) previous = token.type
      // INDENT and DEDENT are synthetic and zero-width, carrying inverted ranges like
      // [33..32]; emitting those would make negative-length spans.
      val end = token.stopIndex + 1
      if (kind == null || token.startIndex < 0 || end <= token.startIndex) {
        null
      } else {
        SyntaxSpan(token.startIndex, minOf(end, source.length), kind)
      }
    }
  }

  /**
   * Every channel, not just the default one: comments live on the hidden channel and are the
   * whole reason this grammar was picked.
   *
   * Error listeners are removed because half-written code is the normal case in a cell, not
   * an edge case - a lexer complaining to stderr on every keystroke helps nobody.
   */
  private fun tokensOf(source: String): List<Token> {
    val lexer = PythonLexer(CharStreams.fromString(source)).apply { removeErrorListeners() }
    return CommonTokenStream(lexer).apply { fill() }.tokens
  }

  /**
   * [previous] is the last token on the default channel. One token of lookbehind is all it
   * takes to colour a definition's name, which no lexer can know on its own: a `NAME` is only
   * a function because `def` came before it.
   */
  private fun kindOf(type: Int, previous: Int): SyntaxKind? = when {
    type == PythonLexer.NAME && previous in DEFINES -> SyntaxKind.FUNCTION
    type in KEYWORDS -> SyntaxKind.KEYWORD
    type == PythonLexer.COMMENT || type == PythonLexer.TYPE_COMMENT -> SyntaxKind.COMMENT
    type == PythonLexer.NUMBER -> SyntaxKind.NUMBER
    type in STRINGS -> SyntaxKind.STRING
    else -> null
  }

  private companion object {
    /**
     * FSTRING_MIDDLE only - deliberately not the whole f-string. The grammar splits it into
     * START/MIDDLE/END around the interpolation, so `{name}` stays uncoloured as the
     * expression it is, which is how VS Code renders it too.
     */
    val STRINGS = setOf(
      PythonLexer.STRING,
      PythonLexer.FSTRING_START,
      PythonLexer.FSTRING_MIDDLE,
      PythonLexer.FSTRING_END,
      // t-strings: PEP 750 template literals, which this grammar already knows about.
      PythonLexer.TSTRING_START,
      PythonLexer.TSTRING_MIDDLE,
      PythonLexer.TSTRING_END,
    )

    /** What makes the name after it a definition rather than a reference. */
    val DEFINES = setOf(PythonLexer.DEF, PythonLexer.CLASS)

    val KEYWORDS = setOf(
      PythonLexer.FALSE, PythonLexer.NONE, PythonLexer.TRUE, PythonLexer.AND, PythonLexer.AS,
      PythonLexer.ASSERT, PythonLexer.ASYNC, PythonLexer.AWAIT, PythonLexer.BREAK,
      PythonLexer.CLASS, PythonLexer.CONTINUE, PythonLexer.DEF, PythonLexer.DEL,
      PythonLexer.ELIF, PythonLexer.ELSE, PythonLexer.EXCEPT, PythonLexer.FINALLY,
      PythonLexer.FOR, PythonLexer.FROM, PythonLexer.GLOBAL, PythonLexer.IF,
      PythonLexer.IMPORT, PythonLexer.IN, PythonLexer.IS, PythonLexer.LAMBDA,
      PythonLexer.NONLOCAL, PythonLexer.NOT, PythonLexer.OR, PythonLexer.PASS,
      PythonLexer.RAISE, PythonLexer.RETURN, PythonLexer.TRY, PythonLexer.WHILE,
      PythonLexer.WITH, PythonLexer.YIELD,
      // `match` and `case` are absent on purpose: Python makes them soft keywords, and this
      // grammar lexes them as NAME, which is the correct answer rather than a gap.
    )
  }
}

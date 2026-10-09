package com.jupydroid.app.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonSyntaxTest {
  private val syntax: SyntaxHighlighter = PythonSyntax()

  @Test
  fun spansOf_marksKeywordsNumbersAndNames() {
    val spans = syntax.spansOf("x = 42")

    assertEquals(SyntaxKind.NUMBER, kindAt(spans, "x = 42".indexOf("42")))
    assertEquals(SyntaxKind.PLAIN, kindAt(spans, 0))
  }

  @Test
  fun spansOf_marksTheKeywordsThatOpenABlock() {
    val source = "def greet():"

    assertEquals(SyntaxKind.KEYWORD, kindAt(syntax.spansOf(source), 0))
    assertEquals(SyntaxKind.FUNCTION, kindAt(syntax.spansOf(source), source.indexOf("greet")))
  }

  /**
   * The reason `python3_14` was chosen over `python3`: the older grammar sends comments to
   * `-> skip`, so they never reach the token stream and cannot be coloured at all.
   */
  @Test
  fun spansOf_marksComments() {
    val source = "# a note\nx = 1"

    assertEquals(SyntaxKind.COMMENT, kindAt(syntax.spansOf(source), 0))
  }

  @Test
  fun spansOf_marksStringsIncludingTripleQuotedOnesThatSpanLines() {
    val source = "s = \"\"\"one\ntwo\"\"\""

    val kind = kindAt(syntax.spansOf(source), source.indexOf("one"))

    assertEquals(SyntaxKind.STRING, kind)
  }

  /**
   * f-strings tokenise structurally in this grammar, so the interpolation is an expression
   * rather than part of the literal - which is how VS Code colours it too.
   */
  @Test
  fun spansOf_treatsAnFStringInterpolationAsCodeNotString() {
    val source = "s = f'hi {name} 42'"

    val spans = syntax.spansOf(source)

    assertEquals(SyntaxKind.STRING, kindAt(spans, source.indexOf("hi")))
    assertEquals(SyntaxKind.PLAIN, kindAt(spans, source.indexOf("name")))
  }

  /**
   * INDENT and DEDENT are synthetic, zero-width, and carry inverted ranges like [33..32].
   * Emitting them would produce negative-length spans and crash the renderer.
   */
  @Test
  fun spansOf_neverProducesAnEmptyOrBackwardsSpan() {
    val spans = syntax.spansOf("def f():\n    if x:\n        return 1\n")

    assertTrue("every span must advance, got $spans", spans.all { it.end > it.start })
  }

  @Test
  fun spansOf_staysInsideTheSourceItWasGiven() {
    val source = "x = 'abc'"

    val spans = syntax.spansOf(source)

    assertTrue("spans must not overrun, got $spans", spans.all { it.end <= source.length })
    assertTrue(spans.all { it.start >= 0 })
  }

  /** Cells are edited constantly, so half-written code is the normal case, not the edge. */
  @Test
  fun spansOf_toleratesIncompleteCodeWithoutThrowing() {
    listOf("def f(", "x = 'unterminated", "if True:", "", "   ", "@").forEach { source ->
      syntax.spansOf(source) // must not throw
    }
  }

  /**
   * Every f-string passes through these while it is typed - the quote is open and no `{` has
   * been - and the vendored lexer base threw `NoSuchElementException` on each, from the editor's
   * highlighting pass, which took the whole app down on the keystroke.
   */
  @Test
  fun spansOf_toleratesAnFStringStillBeingTyped() {
    listOf("f\"", "f'", "print(f\")", "x = f\"ab", "f\"\n").forEach { source ->
      syntax.spansOf(source) // must not throw
    }
    assertEquals(SyntaxKind.STRING, kindAt(syntax.spansOf("x = f\"ab"), "x = ".length))
  }

  private fun kindAt(spans: List<SyntaxSpan>, offset: Int): SyntaxKind =
    spans.firstOrNull { offset >= it.start && offset < it.end }?.kind ?: SyntaxKind.PLAIN
}

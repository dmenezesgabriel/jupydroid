package com.jupydroid.app.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `|` marks the cursor; the expected offsets are into the source with that marker removed. */
class BracketPairTest {
  private val syntax: SyntaxHighlighter = PythonSyntax()

  @Test
  fun matchesTheOpenerJustBeforeTheCursor() {
    assertEquals(BracketPair(open = 1, close = 3), matchIn("f(|x)"))
  }

  @Test
  fun matchesTheCloserJustBeforeTheCursor() {
    assertEquals(BracketPair(open = 1, close = 3), matchIn("f(x)|"))
  }

  /** The cursor sits between two brackets constantly; the one behind it is the one meant. */
  @Test
  fun prefersTheBracketBehindTheCursorOverTheOneInFrontOfIt() {
    assertEquals(BracketPair(open = 3, close = 4), matchIn("f(g()|)"))
  }

  @Test
  fun matchesTheBracketInFrontWhenNothingIsBehind() {
    assertEquals(BracketPair(open = 0, close = 5), matchIn("|(a, b)"))
  }

  @Test
  fun countsNestingRatherThanTakingTheFirstPartnerItSees() {
    assertEquals(BracketPair(open = 1, close = 9), matchIn("f(g(h(x)))|"))
  }

  @Test
  fun matchesEveryBracketShape() {
    assertEquals(BracketPair(open = 2, close = 5), matchIn("xs[1:]|"))
    assertEquals(BracketPair(open = 4, close = 11), matchIn("d = {'a': 1}|"))
  }

  /** `print("(")` has one real pair; the lexer is what keeps the literal one out of it. */
  @Test
  fun ignoresBracketsInsideAString() {
    assertEquals(BracketPair(open = 5, close = 9), matchIn("print(\"(\")|"))
  }

  @Test
  fun ignoresBracketsInsideAComment() {
    assertNull(matchIn("f(x)  # )|"))
  }

  /** An f-string interpolation is code, so the brackets inside it are real ones. */
  @Test
  fun matchesBracketsInsideAnFStringInterpolation() {
    assertEquals(BracketPair(open = 8, close = 12), matchIn("s = f'{d[|'k']}'"))
  }

  @Test
  fun findsNothingWhenTheCursorTouchesNoBracket() {
    assertNull(matchIn("answer = 42|"))
    assertNull(matchIn("|"))
  }

  /** Half-written code is the normal state of a cell, so an unclosed bracket must not throw. */
  @Test
  fun findsNothingWhenThePartnerIsMissing() {
    assertNull(matchIn("f(|"))
    assertNull(matchIn("x = a)|"))
  }

  /**
   * P5e-4: the caret inside a call no `)` has closed yet is where signature help is worth
   * asking - VS Code's `parameterHintsModel` asks at the caret inside the call's parens.
   */
  @Test
  fun aCallStillOpen_isInside() {
    assertTrue(insideIn("os.path.join(|"))
    assertTrue(insideIn("join(path, sep='/', |"))
    assertTrue(insideIn("join(path, |"))
    assertTrue(insideIn("f(g(|)"))
  }

  /** A `)` behind the caret closed the call - nothing to hint. */
  @Test
  fun aCallAlreadyClosed_isNotInside() {
    assertFalse(insideIn("join(path)|"))
    assertFalse(insideIn("join()|"))
    assertFalse(insideIn("answer = 42|"))
    assertFalse(insideIn("|"))
  }

  /**
   * `f(")", arg|` is the case the literal mask exists for: without it the string's `)` would
   * eat the call's `(` and read the caret as outside a call it is plainly inside.
   */
  @Test
  fun aCloserInsideAString_closesNoRealParen() {
    assertTrue(insideIn("f(\")\", arg|"))
    assertTrue(insideIn("f(')', arg|"))
  }

  /** Parens inside literals are punctuation, so they open no call either. */
  @Test
  fun parensInsideLiterals_openNoCall() {
    assertFalse(insideIn("x = '(|'"))
    assertFalse(insideIn("f(x)  # (|"))
  }

  /** A multi-line call is still one call - `join(path,\n  |` hints the same signature. */
  @Test
  fun aCallAcrossLines_isInside() {
    assertTrue(insideIn("join(path,\n     |"))
  }

  /** A grouping paren is inside-unclosed too; the server answers nothing for it, which is fine. */
  @Test
  fun anyParenCounts_notJustCalls() {
    assertTrue(insideIn("x = (1 + |"))
  }

  private fun matchIn(marked: String): BracketPair? {
    val cursor = marked.indexOf('|')
    require(cursor >= 0) { "no cursor in \"$marked\"" }
    val source = marked.replace("|", "")
    return bracketPairAround(source, cursor, syntax.spansOf(source))
  }

  private fun insideIn(marked: String): Boolean {
    val cursor = marked.indexOf('|')
    require(cursor >= 0) { "no cursor in \"$marked\"" }
    val source = marked.replace("|", "")
    return insideUnclosedParen(source, cursor, syntax.spansOf(source))
  }
}

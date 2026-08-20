package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Private.parseQuery` (`packages/documentsearch/src/searchmodel.ts:398-427`), rule by rule.
 *
 * The reference builds **one** `RegExp` from four inputs and every option is a transformation of
 * the pattern text rather than a branch further down, which is why this is a pure function with no
 * document anywhere near it.
 */
class SearchQueryTest {
  private fun matches(query: SearchQuery, text: String): List<String> =
    (query.compile() as CompiledQuery.Matching).regex.findAll(text).map { it.value }.toList()

  /** `'gim'` when `caseSensitive` is false - the default the search box opens with. */
  @Test
  fun aPlainQueryIgnoresCase() {
    assertEquals(listOf("Cat", "cat"), matches(SearchQuery("cat"), "Cat and cat"))
  }

  @Test
  fun caseSensitiveMatchesOnlyTheCaseThatWasTyped() {
    assertEquals(listOf("cat"), matches(SearchQuery("cat", caseSensitive = true), "Cat and cat"))
  }

  /**
   * `queryString.replace(/[-[\]/{}()*+?.\\^$|]/g, '\\$&')`: a query that is not a regex is text,
   * so `a.b` is three characters and not "a, anything, b".
   */
  @Test
  fun aQueryThatIsNotARegexIsLiteral() {
    assertEquals(listOf("a.b"), matches(SearchQuery("a.b"), "a.b axb"))
  }

  /** Every character in the reference's own escape set, in one query. */
  @Test
  fun everyMetacharacterTheReferenceEscapesIsLiteral() {
    val punctuation = "-[]/{}()*+?.\\^\$|"
    assertEquals(listOf(punctuation), matches(SearchQuery(punctuation), "x${punctuation}y"))
  }

  @Test
  fun aRegexQueryIsPassedThrough() {
    assertEquals(listOf("axb", "a.b"), matches(SearchQuery("a.b", useRegex = true), "axb a.b"))
  }

  /** `queryText = '\\b' + queryText + '\\b'`. */
  @Test
  fun wholeWordsRefusesTheHalfOfALongerWord() {
    assertEquals(listOf("cat"), matches(SearchQuery("cat", wholeWords = true), "cat category"))
  }

  /** The two compose: the escaping runs first, so the word boundary wraps the *escaped* text. */
  @Test
  fun wholeWordsWrapsTheEscapedTextAndNotTheRawOne() {
    assertEquals(listOf("c.t"), matches(SearchQuery("c.t", wholeWords = true), "c.t xc.ty"))
  }

  /** `'m'` is in both flag strings, so `^` is every line's start and not only the text's. */
  @Test
  fun theMultilineFlagIsAlwaysOn() {
    assertEquals(listOf("a", "a"), matches(SearchQuery("^a", useRegex = true), "ab\nab"))
  }

  /** An empty search expression ends the query there (`_updateSearch`'s `else` branch). */
  @Test
  fun anEmptyQuerySearchesForNothing() {
    assertEquals(CompiledQuery.Empty, SearchQuery("").compile())
  }

  /**
   * `if (ret.test('')) return null` - the reference's own comment says a pattern matching the
   * empty string would freeze the tab, and CodeMirror's demo finds nothing for `/^/` either.
   */
  @Test
  fun aPatternThatMatchesEverywhereSearchesForNothing() {
    assertEquals(CompiledQuery.Empty, SearchQuery("a*", useRegex = true).compile())
    assertEquals(CompiledQuery.Empty, SearchQuery("^", useRegex = true).compile())
    assertEquals(CompiledQuery.Empty, SearchQuery("$", useRegex = true).compile())
  }

  /**
   * The anchor is only rejected when it is the *whole* of the pattern - `^a` matches one character
   * and is a perfectly good search, which is what keeps the guard above from swallowing
   * [theMultilineFlagIsAlwaysOn]'s query.
   */
  @Test
  fun anAnchoredPatternThatStillMatchesSomethingIsKept() {
    assertTrue(
      "${SearchQuery("^a", useRegex = true).compile()}",
      SearchQuery("^a", useRegex = true).compile() is CompiledQuery.Matching,
    )
  }

  /**
   * `_parsingError` is shown beside the box rather than thrown - a half-typed `(` is what every
   * regex search looks like one keystroke in.
   */
  @Test
  fun anUnparseablePatternIsReportedWithTheOffendingText() {
    val failed = SearchQuery("(", useRegex = true).compile()
    assertTrue("$failed", failed is CompiledQuery.Invalid)
    assertTrue("$failed", (failed as CompiledQuery.Invalid).message.contains("("))
  }

  /** Escaping is what keeps a bare `(` from ever reaching the engine when it is not a regex. */
  @Test
  fun anUnbalancedBracketIsOrdinaryTextWhenItIsNotARegex() {
    assertEquals(listOf("("), matches(SearchQuery("("), "f(x)"))
  }
}

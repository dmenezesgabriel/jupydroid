package com.jupydroid.app.glob

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `parseRegExp` (`src/vs/base/common/glob.ts:108-256`), rule for rule, anchored `^…$` the way
 * `toRegExp` anchors it (`:525-536`).
 *
 * Every case here names the line of the reference it holds, because a glob that is nearly right is
 * a search that silently misses files.
 */
class PathGlobTest {
  private fun matches(pattern: String, path: String) = PathGlob(pattern).matches(path)

  /** `starsToRegExp(1)` is `[^/\\]*?` - one star stops at a separator. */
  @Test
  fun oneStarDoesNotCrossASeparator() {
    assertTrue(matches("*.py", "notes.py"))
    assertFalse(matches("*.py", "reports/notes.py"))
  }

  /** `starsToRegExp(2)` - a globstar crosses them. */
  @Test
  fun aGlobstarCrossesSeparators() {
    assertTrue(matches("**/*.py", "notes.py"))
    assertTrue(matches("**/*.py", "reports/deep/notes.py"))
  }

  /** *"Special case where we only have globstars"*: the whole pattern becomes `.*`. */
  @Test
  fun aPatternOfNothingButGlobstarsMatchesEverything() {
    assertTrue(matches("**", "notes.py"))
    assertTrue(matches("**", "reports/deep/notes.py"))
  }

  /** `?` is `NO_PATH_REGEX` - exactly one character, and never a separator. */
  @Test
  fun aQuestionMarkIsOneCharacterThatIsNotASeparator() {
    assertTrue(matches("?.py", "a.py"))
    assertFalse(matches("?.py", "ab.py"))
    assertFalse(matches("?.py", "a/.py"))
  }

  /** *"Converts {foo,bar} => [foo|bar]"* - each choice parsed as a pattern of its own. */
  @Test
  fun bracesAreAlternatives() {
    assertTrue(matches("notes.{py,txt}", "notes.py"))
    assertTrue(matches("notes.{py,txt}", "notes.txt"))
    assertFalse(matches("notes.{py,txt}", "notes.md"))
  }

  /** A choice is itself a pattern, which is why `parseRegExp` recurses into it. */
  @Test
  fun aBraceChoiceIsItselfAPattern() {
    assertTrue(matches("{**/*.py,notes.txt}", "reports/deep/a.py"))
    assertTrue(matches("{**/*.py,notes.txt}", "notes.txt"))
  }

  /** Brackets are a character class, with `-` kept as the range operator. */
  @Test
  fun bracketsAreACharacterClass() {
    assertTrue(matches("[abc].py", "b.py"))
    assertFalse(matches("[abc].py", "d.py"))
    assertTrue(matches("[a-c].py", "c.py"))
  }

  /** *"negation operator (only valid on first index in bracket)"* - both `^` and `!` mean it. */
  @Test
  fun bothCaretAndBangNegateABracket() {
    assertFalse(matches("[!a].py", "a.py"))
    assertTrue(matches("[!a].py", "b.py"))
    assertFalse(matches("[^a].py", "a.py"))
    assertTrue(matches("[^a].py", "b.py"))
  }

  /** `escapeRegExpCharacters` - a literal `.` is a `.`, not "any character". */
  @Test
  fun literalsAreEscaped() {
    assertTrue(matches("notes.py", "notes.py"))
    assertFalse(matches("notes.py", "notesxpy"))
  }

  /** `^…$`: the pattern is the whole path, not a piece of it. */
  @Test
  fun theWholePathIsMatched() {
    assertFalse(matches("reports", "reports/notes.py"))
    assertFalse(matches("notes", "notes.py"))
    assertTrue(matches("reports/notes.py", "reports/notes.py"))
  }

  /**
   * The tail separator, and the reference's own example for why it is there: a pattern of
   * "some", globstar, "star dot js" *"we want the `/` after some to be included in the RegEx to
   * prevent a folder called `something` to match as well"* (`glob.ts:236-241`). Spelled out rather
   * than quoted, because Kotlin nests block comments and the pattern itself opens one.
   */
  @Test
  fun aSegmentBeforeAGlobstarKeepsItsSeparator() {
    assertTrue(matches("some/**/*.js", "some/deep/a.js"))
    assertTrue(matches("some/**/*.js", "some/a.js"))
    assertFalse(matches("some/**/*.js", "something/a.js"))
  }

  /** *"if we have more than one globstar after another, just ignore it"*. */
  @Test
  fun repeatedGlobstarsCollapse() {
    assertTrue(matches("**/**/*.py", "reports/deep/a.py"))
    assertTrue(matches("**/**/*.py", "a.py"))
  }

  /** A separator inside a bracket is dropped - `glob(7)` forbids it in a range. */
  @Test
  fun aSeparatorInsideABracketIsDropped() {
    assertTrue(matches("[a/b].py", "a.py"))
    assertFalse(matches("[a/b].py", "/.py"))
  }

  /**
   * An unterminated brace swallows the rest of the segment, which is what the reference's
   * `inBraces` state does - `braceVal` accumulates and nothing is ever appended. A pattern being
   * typed is unterminated for most of the time it is being typed, so this is the ordinary case.
   */
  @Test
  fun anUnterminatedBraceSwallowsTheRest() {
    assertTrue(matches("notes{py", "notes"))
    assertFalse(matches("notes{py", "notes{py"))
  }
}

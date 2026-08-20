package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What one match becomes - `packages/codemirror/src/searchprovider.ts:344-352` (the current match)
 * and `:412-422` (all of them), which build the same two-step replacement:
 *
 * ```
 * const substitutedText = options?.regularExpression ? match.text.replace(query, newText) : newText;
 * const insertText = options?.preserveCase ? preserveCase(match.text, substitutedText) : substitutedText;
 * ```
 */
class SearchReplaceTest {
  private val plain = SearchQuery("cat")
  private val pattern = SearchQuery("c(a)t", useRegex = true)

  @Test
  fun plainTextIsReplacedByTheTextThatWasTyped() {
    assertEquals("dog", replacementFor("cat", "dog", plain, preserveCase = false))
  }

  /** `match.text.replace(this.query!, newText)` - so a group reference works. */
  @Test
  fun aRegexReplacementCanReferToItsGroups() {
    assertEquals("[a]", replacementFor("cat", "[$1]", pattern, preserveCase = false))
  }

  /** And only under the regex option: `$1` in plain text is the two characters it looks like. */
  @Test
  fun aGroupReferenceIsLiteralWhenTheQueryIsPlainText() {
    assertEquals("[\$1]", replacementFor("cat", "[\$1]", plain, preserveCase = false))
  }

  /**
   * **Kotlin throws where JavaScript does not.** `Regex.replace` raises on a group that does not
   * exist, while `String.prototype.replace` leaves `$9` as the characters it is. A replacement
   * being typed names a group that does not exist yet for most of the time it is being typed, so
   * this falls back to the literal text rather than taking the notebook down.
   */
  @Test
  fun aReplacementNamingAGroupThatIsNotThereIsTakenLiterally() {
    assertEquals("[\$9]", replacementFor("cat", "[\$9]", pattern, preserveCase = false))
  }

  /** `preserveCase`: `oldText.toUpperCase() === oldText` (`documentsearch/src/searchprovider.ts:176-186`). */
  @Test
  fun preserveCaseShoutsBackAtSomethingThatShouted() {
    assertEquals("DOG", replacementFor("CAT", "dog", plain, preserveCase = true))
  }

  @Test
  fun preserveCaseWhispersBackAtSomethingLowerCase() {
    assertEquals("dog", replacementFor("cat", "DOG", plain, preserveCase = true))
  }

  /** `toSentenceCase`: first letter up, **the rest down** - `first.toUpperCase() + suffix.toLowerCase()`. */
  @Test
  fun preserveCaseCapitalisesWhatWasCapitalised() {
    assertEquals("Dog", replacementFor("Cat", "dOG", plain, preserveCase = true))
  }

  /** Anything else is left exactly as typed - the reference's final `return newText`. */
  @Test
  fun preserveCaseLeavesAMixtureAlone() {
    assertEquals("dOg", replacementFor("cAt", "dOg", plain, preserveCase = true))
  }

  /** Off, the replacement is the characters that were typed and nothing else. */
  @Test
  fun withoutPreserveCaseTheReplacementIsVerbatim() {
    assertEquals("dog", replacementFor("CAT", "dog", plain, preserveCase = false))
  }

  /** The two compose, and the substitution runs first - that is the order in both call sites. */
  @Test
  fun theSubstitutionHappensBeforeTheCaseIsPreserved() {
    assertEquals("[A]", replacementFor("CAT", "[$1]", pattern, preserveCase = true))
  }
}

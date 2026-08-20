package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bar that replaces the notebook's own while it is being searched.
 *
 * Instrumented for [SelectionTopBarTest]'s reason: what is asserted is whether there is a box to
 * type into and a count to read, which no pure function can answer.
 */
@RunWith(AndroidJUnit4::class)
class FindTopBarTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun offersABoxToSearchIn() {
    compose.setContent { bar() }

    compose.onNodeWithContentDescription(FIND_BOX).assertIsDisplayed()
  }

  @Test
  fun reportsWhatWasTyped() {
    var typed: SearchQuery? = null
    compose.setContent { bar(onQueryChanged = { typed = it }) }

    compose.onNodeWithContentDescription(FIND_BOX).performTextInput("cat")

    assertEquals("cat", typed?.text)
  }

  /** `${currentIndex + 1}/${totalMatches}` (`packages/documentsearch/src/searchview.tsx:345-351`). */
  @Test
  fun showsWhereTheSearchIsInWhatItFound() {
    compose.setContent { bar(counter = "2/7") }

    compose.onNodeWithText("2/7").assertIsDisplayed()
  }

  /** The counter is named as well as read, or a screen reader is handed "2/7" with no noun. */
  @Test
  fun theCountIsNamedForAScreenReader() {
    compose.setContent { bar(counter = "2/7") }

    compose.onNodeWithContentDescription("$MATCH_COUNT 2/7").assertIsDisplayed()
  }

  /** `_parsingError` is shown, not swallowed - a regex is unparseable while it is being typed. */
  @Test
  fun showsWhyAPatternDidNotParse() {
    compose.setContent { bar(parseError = "cannot search for /(/: Unclosed group") }

    compose.onNodeWithText("cannot search for /(/: Unclosed group").assertIsDisplayed()
  }

  @Test
  fun aValidQueryLeavesNoErrorOnScreen() {
    compose.setContent { bar(query = SearchQuery("cat")) }

    compose.onNodeWithText("cat").assertIsDisplayed()
  }

  /** `documentsearch:end`. */
  @Test
  fun closesOnAsking() {
    var closed = false
    compose.setContent { bar(onClose = { closed = true }) }

    compose.onNodeWithContentDescription(CLOSE_SEARCH).performClick()

    assertEquals(true, closed)
  }

  /** `documentsearch:highlightNext` / `highlightPrevious`. */
  @Test
  fun stepsForwardAndBackThroughWhatItFound() {
    var nexts = 0
    var previouses = 0
    compose.setContent { bar(counter = "2/7", onNext = { nexts++ }, onPrevious = { previouses++ }) }

    compose.onNodeWithContentDescription(NEXT_MATCH).performClick()
    compose.onNodeWithContentDescription(PREVIOUS_MATCH).performClick()

    assertEquals(1, nexts)
    assertEquals(1, previouses)
  }

  /**
   * Greyed rather than absent with nothing found, so the bar does not change shape under a thumb
   * as the query is typed. Asserted here rather than in a flow because the accessibility tree keeps
   * `enabled` on a node a Maestro assertion cannot read (`docs/DECISIONS.md`).
   */
  @Test
  fun theArrowsAreDeadWhileThereIsNothingToStepThrough() {
    compose.setContent { bar(counter = "-/-") }

    compose.onNodeWithContentDescription(NEXT_MATCH).assertIsNotEnabled()
    compose.onNodeWithContentDescription(PREVIOUS_MATCH).assertIsNotEnabled()
  }

  @Test
  fun theArrowsComeAliveOnceSomethingIsFound() {
    compose.setContent { bar(counter = "1/2") }

    compose.onNodeWithContentDescription(NEXT_MATCH).assertIsEnabled()
    compose.onNodeWithContentDescription(PREVIOUS_MATCH).assertIsEnabled()
  }

  /**
   * `documentsearch:` Match Case, Match Whole Word and Use Regular Expression, behind the overflow
   * for [SelectionTopBar]'s reason - the bar already carries five targets at 360dp.
   */
  @Test
  fun theThreeQueryOptionsAreBehindTheOverflow() {
    compose.setContent { bar() }

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()

    compose.onNodeWithText(MATCH_CASE).assertIsDisplayed()
    compose.onNodeWithText(MATCH_WHOLE_WORD).assertIsDisplayed()
    compose.onNodeWithText(USE_REGULAR_EXPRESSION).assertIsDisplayed()
  }

  /** The label flips with the state, which is how a Maestro flow can read it at all. */
  @Test
  fun eachOptionReadsTheOtherWayRoundOnceItIsOn() {
    compose.setContent {
      bar(query = SearchQuery("cat", caseSensitive = true, wholeWords = true, useRegex = true))
    }

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()

    compose.onNodeWithText(IGNORE_CASE).assertIsDisplayed()
    compose.onNodeWithText(MATCH_ANYWHERE).assertIsDisplayed()
    compose.onNodeWithText(SEARCH_PLAIN_TEXT).assertIsDisplayed()
  }

  @Test
  fun pickingAnOptionReportsTheQueryItAsksFor() {
    var asked: SearchQuery? = null
    compose.setContent { bar(query = SearchQuery("cat"), onQueryChanged = { asked = it }) }

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()
    compose.onNodeWithText(MATCH_CASE).performClick()

    assertEquals(SearchQuery("cat", caseSensitive = true), asked)
  }

  @androidx.compose.runtime.Composable
  private fun bar(
    query: SearchQuery = SearchQuery(""),
    counter: String = "-/-",
    parseError: String? = null,
    onQueryChanged: (SearchQuery) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onClose: () -> Unit = {},
  ) = FindTopBar(
    query = query,
    counter = counter,
    parseError = parseError,
    onQueryChanged = onQueryChanged,
    onPrevious = onPrevious,
    onNext = onNext,
    onClose = onClose,
  )
}

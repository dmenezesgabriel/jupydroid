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
import com.jupydroid.app.domain.model.SearchQuery
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
   * `enabled` on a node a Maestro assertion cannot read.
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

  /** `documentsearch:startWithReplace` - the row is absent until it is asked for. */
  @Test
  fun theReplaceRowIsNotThereUntilItIsAskedFor() {
    compose.setContent { bar() }

    compose.onNodeWithContentDescription(REPLACE_BOX).assertDoesNotExist()

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()
    compose.onNodeWithText(SHOW_REPLACE).assertIsDisplayed()
  }

  @Test
  fun theReplaceRowCarriesABoxAndBothButtons() {
    compose.setContent { bar(replaceShown = true, counter = "1/3") }

    compose.onNodeWithContentDescription(REPLACE_BOX).assertIsDisplayed()
    compose.onNodeWithText(REPLACE_ONE).assertIsEnabled()
    compose.onNodeWithText(REPLACE_ALL).assertIsEnabled()
  }

  /** Dead while nothing is found, for the arrows' reason - two states that would look identical. */
  @Test
  fun neitherReplaceButtonIsLiveWithNothingFound() {
    compose.setContent { bar(replaceShown = true, counter = "-/-") }

    compose.onNodeWithText(REPLACE_ONE).assertIsNotEnabled()
    compose.onNodeWithText(REPLACE_ALL).assertIsNotEnabled()
  }

  @Test
  fun bothReplaceButtonsReportWhatWasAskedFor() {
    var replaced = 0
    var replacedAll = 0
    compose.setContent {
      bar(replaceShown = true, counter = "1/3", onReplace = { replaced++ }, onReplaceAll = { replacedAll++ })
    }

    compose.onNodeWithText(REPLACE_ONE).performClick()
    compose.onNodeWithText(REPLACE_ALL).performClick()

    assertEquals(1, replaced)
    assertEquals(1, replacedAll)
  }

  /** `preserveCase` is offered only while there is a replacement for it to change. */
  @Test
  fun preserveCaseIsOfferedOnlyBesideAReplacement() {
    compose.setContent { bar() }
    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()
    compose.onNodeWithText(PRESERVE_CASE).assertDoesNotExist()
  }

  @Test
  fun preserveCaseAppearsOnceTheReplaceRowIs() {
    compose.setContent { bar(replaceShown = true) }

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()

    compose.onNodeWithText(PRESERVE_CASE).assertIsDisplayed()
    compose.onNodeWithText(HIDE_REPLACE).assertIsDisplayed()
  }

  /** `output` - *"Search Cell Outputs"*, and not offered while a replacement is being written. */
  @Test
  fun theOutputFilterIsBehindTheOverflowAndGoesWhenReplaceArrives() {
    compose.setContent { bar() }
    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()
    compose.onNodeWithText(SEARCH_CELL_OUTPUTS).assertIsDisplayed()
  }

  @Test
  fun theOutputFilterIsNotOfferedBesideAReplacement() {
    compose.setContent { bar(replaceShown = true) }

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()

    compose.onNodeWithText(SEARCH_CELL_OUTPUTS).assertDoesNotExist()
  }

  /** `selection` - offered only once there is a run to narrow to, and named from its count. */
  @Test
  fun theSelectionFilterIsOfferedOnlyWhileCellsAreMarked() {
    compose.setContent { bar() }
    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()
    compose.onNodeWithText(searchInSelection(1)).assertDoesNotExist()
  }

  @Test
  fun theSelectionFilterCountsTheMarkedCells() {
    compose.setContent { bar(markedCells = 3) }

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()

    compose.onNodeWithText(searchInSelection(3)).assertIsDisplayed()
  }

  /** *"Search in %1 Selected Cell"* - the reference pluralises off the count and so does this. */
  @Test
  fun oneMarkedCellIsSingular() {
    compose.setContent { bar(markedCells = 1) }

    compose.onNodeWithContentDescription(SEARCH_OPTIONS).performClick()

    compose.onNodeWithText("Search in 1 selected cell").assertIsDisplayed()
  }

  @androidx.compose.runtime.Composable
  private fun bar(
    query: SearchQuery = SearchQuery(""),
    counter: String = "-/-",
    parseError: String? = null,
    replaceShown: Boolean = false,
    replaceText: String = "",
    preserveCase: Boolean = false,
    markedCells: Int = 0,
    onQueryChanged: (SearchQuery) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onClose: () -> Unit = {},
    onToggleReplaceShown: () -> Unit = {},
    onReplaceTextChanged: (String) -> Unit = {},
    onTogglePreserveCase: () -> Unit = {},
    onReplace: () -> Unit = {},
    onReplaceAll: () -> Unit = {},
  ) = FindTopBar(
    search = SearchState(
      query = query,
      parseError = parseError,
      replaceShown = replaceShown,
      replaceText = replaceText,
      preserveCase = preserveCase,
    ),
    counter = counter,
    markedCells = markedCells,
    onQueryChanged = onQueryChanged,
    onPrevious = onPrevious,
    onNext = onNext,
    onClose = onClose,
    onToggleReplaceShown = onToggleReplaceShown,
    onReplaceTextChanged = onReplaceTextChanged,
    onTogglePreserveCase = onTogglePreserveCase,
    onReplace = onReplace,
    onReplaceAll = onReplaceAll,
  )
}

package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
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

  @androidx.compose.runtime.Composable
  private fun bar(
    query: SearchQuery = SearchQuery(""),
    counter: String = "-/-",
    parseError: String? = null,
    onQueryChanged: (SearchQuery) -> Unit = {},
    onClose: () -> Unit = {},
  ) = FindTopBar(
    query = query,
    counter = counter,
    parseError = parseError,
    onQueryChanged = onQueryChanged,
    onClose = onClose,
  )
}

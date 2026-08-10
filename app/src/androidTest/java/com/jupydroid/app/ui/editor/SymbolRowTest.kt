package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the row reaches a *real* focused field.
 *
 * `FocusedCellInputTest` argues what each key does to a `TextFieldState`; what it cannot answer is
 * whether a field ever hands itself over. That is a question about focus, and this app has
 * shipped one bug of exactly that shape before - a rendered markdown cell that opened an editor
 * with no caret (`docs/PLAN.md`, audit A1c).
 */
@RunWith(AndroidJUnit4::class)
class SymbolRowTest {
  @get:Rule val compose = createComposeRule()

  private val edits = mutableListOf<String>()

  @Test
  fun aKeyTypesIntoTheCellThatHasTheCaret() {
    showFieldAndRow()

    compose.onNodeWithTag(FIELD).performClick()
    compose.onNodeWithText(":").performScrollTo().performClick()

    assertTyped("x:")
  }

  /** The parity that matters: the row's `(` must give what the keyboard's `(` gives. */
  @Test
  fun aBracketFromTheRowAutoCloses() {
    showFieldAndRow()

    compose.onNodeWithTag(FIELD).performClick()
    compose.onNodeWithText("(").performScrollTo().performClick()

    assertTyped("x()")
  }

  @Test
  fun tabIndents() {
    showFieldAndRow()

    compose.onNodeWithTag(FIELD).performClick()
    compose.onNodeWithText("Tab").performScrollTo().performClick()

    assertTyped("x    ")
  }

  /**
   * With nothing focused the keys are inert rather than throwing. Reachable in the moment between
   * a cell losing focus and the mode leaving EDIT, which is a frame the user can tap in.
   */
  @Test
  fun aKeyWithNothingFocusedDoesNothing() {
    showFieldAndRow()

    compose.onNodeWithText(":").performScrollTo().performClick()

    // Long enough that a report would have arrived if one were coming: the field debounces
    // edits by 150ms, so an immediate check here would pass whether or not a key did anything.
    compose.mainClock.advanceTimeBy(DEBOUNCE_GRACE_MS)
    compose.runOnIdle { assertEquals(emptyList<String>(), edits) }
  }

  /**
   * Waits for the edit to be *reported*, because the field debounces by 150ms before telling
   * anyone. `runOnIdle` does not wait for that, so asserting straight after a tap reads an empty
   * list and fails against a field that typed perfectly well - which is what it did.
   */
  private fun assertTyped(expected: String) {
    compose.waitUntil(timeoutMillis = REPORT_TIMEOUT_MS) { edits.isNotEmpty() }
    assertEquals(expected, edits.last())
  }

  private fun showFieldAndRow() {
    compose.setContent {
      JupyterDroidTheme {
        val input = remember { FocusedCellInput() }
        Column {
          CodeCellField(
            source = "x",
            onSourceEdited = { edits += it },
            modifier = androidx.compose.ui.Modifier.testTag(FIELD),
            input = input,
          )
          SymbolRow(input = input)
        }
      }
    }
  }

  private companion object {
    const val FIELD = "cell-field"
    const val REPORT_TIMEOUT_MS = 2_000L
    const val DEBOUNCE_GRACE_MS = 500L
  }
}

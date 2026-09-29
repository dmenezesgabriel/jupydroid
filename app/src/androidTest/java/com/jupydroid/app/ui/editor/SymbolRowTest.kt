package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
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
 * with no caret (`docs/DECISIONS.md`, audit A1c).
 */
@RunWith(AndroidJUnit4::class)
class SymbolRowTest {
  @get:Rule val compose = createComposeRule()

  private val edits = mutableListOf<String>()

  /** Which history key was tapped, so the row's two callbacks cannot be crossed. */
  private val recalled = mutableListOf<String>()

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

  /**
   * **A markdown cell is reachable from the row too**, and it was not: `MarkdownCellField` never
   * handed itself over, so in a markdown cell the row sat above the keyboard doing nothing - a
   * control that lights up and lies, which is what the `⋮` button exists to avoid elsewhere. It
   * surfaced because P2b's markdown flow drives a caret with `←` and the split then cut at the end
   * of the text rather than where the flow had put it.
   */
  @Test
  fun aKeyTypesIntoAMarkdownCellToo() {
    compose.setContent {
      JupyterDroidTheme {
        val input = remember { FocusedCellInput() }
        Column {
          MarkdownCellField(
            state = rememberTextFieldState("x"),
            onSourceEdited = { edits += it },
            modifier = androidx.compose.ui.Modifier.testTag(FIELD),
            input = input,
          )
          SymbolRow(input = input)
        }
      }
    }

    compose.onNodeWithTag(FIELD).performClick()
    compose.onNodeWithText("#").performScrollTo().performClick()

    assertTyped("x#")
  }

  /**
   * a Maestro flow needs to tell this row apart from a soft keyboard's
   * *own* Tab key, which on at least one real IME (Gboard) carries the identical accessible text
   * "Tab" (`resource-id: com.google.android.inputmethod.latin:id/key_pos_tab`, found live on the
   * physical tablet) - so `assertNotVisible: "Tab"` is not reliable once the keyboard is up.
   * [SYMBOL_ROW_TAG] is the row's own, unambiguous handle.
   */
  @Test
  fun theRowCarriesItsOwnTag() {
    showFieldAndRow()

    compose.onNodeWithTag(SYMBOL_ROW_TAG).assertIsDisplayed()
  }

  // --- notebook:access-*-history-entry ----------------------------------------------

  /**
   * **Absent, not disabled**, while `accessKernelHistory` is off - this app's own rule that a
   * control which can do nothing is not drawn, and the reference's too: both actions return on
   * their first line without the setting (`packages/notebook/src/actions.tsx:2616`, `:2634`), so
   * a key here would be one that lights up and lies.
   */
  @Test
  fun theHistoryKeysAreAbsentWhileKernelHistoryAccessIsOff() {
    showFieldAndRow(accessKernelHistory = false)

    compose.onNodeWithContentDescription(RECALL_PREVIOUS).assertDoesNotExist()
    compose.onNodeWithContentDescription(RECALL_NEXT).assertDoesNotExist()
  }

  @Test
  fun theHistoryKeysAreOnTheRowOnceKernelHistoryAccessIsOn() {
    showFieldAndRow(accessKernelHistory = true)

    compose.onNodeWithContentDescription(RECALL_PREVIOUS).performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription(RECALL_NEXT).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun theBackKeyAsksForThePreviousEntry() {
    showFieldAndRow(accessKernelHistory = true)

    compose.onNodeWithContentDescription(RECALL_PREVIOUS).performScrollTo().performClick()

    compose.runOnIdle { assertEquals(listOf("previous"), recalled) }
  }

  @Test
  fun theForwardKeyAsksForTheNextEntry() {
    showFieldAndRow(accessKernelHistory = true)

    compose.onNodeWithContentDescription(RECALL_NEXT).performScrollTo().performClick()

    compose.runOnIdle { assertEquals(listOf("next"), recalled) }
  }

  /** Every key on this row is a thumb target, the history pair included. */
  @Test
  fun theHistoryKeysAreBigEnoughToTap() {
    showFieldAndRow(accessKernelHistory = true)

    compose.onNodeWithContentDescription(
      RECALL_PREVIOUS,
    ).performScrollTo().assertIsAtLeastMinimumTouchTarget(compose.density)
    compose.onNodeWithContentDescription(
      RECALL_NEXT,
    ).performScrollTo().assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  private fun showFieldAndRow(accessKernelHistory: Boolean = false) {
    compose.setContent {
      JupyterDroidTheme {
        val input = remember { FocusedCellInput() }
        Column {
          CodeCellField(
            state = rememberTextFieldState("x"),
            onSourceEdited = { edits += it },
            modifier = androidx.compose.ui.Modifier.testTag(FIELD),
            input = input,
          )
          SymbolRow(
            input = input,
            accessKernelHistory = accessKernelHistory,
            onRecallPreviousEntry = { recalled += "previous" },
            onRecallNextEntry = { recalled += "next" },
          )
        }
      }
    }
  }

  private companion object {
    const val FIELD = "cell-field"

    /** The reference's own command labels, in this app's sentence case - see [SymbolRow]. */
    const val RECALL_PREVIOUS = "Previous kernel history entry"
    const val RECALL_NEXT = "Next kernel history entry"
    const val REPORT_TIMEOUT_MS = 2_000L
    const val DEBOUNCE_GRACE_MS = 500L
  }
}

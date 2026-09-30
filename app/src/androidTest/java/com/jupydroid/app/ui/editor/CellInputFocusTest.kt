package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That focus in a cell's input area is reported, and focus elsewhere is not.
 *
 * The wiring `CellModeTest` cannot see: the transitions are pure and tested on the JVM, but
 * whether anything ever *calls* them depends on a real focus system, and this app has already
 * shipped one bug of exactly that shape - a rendered markdown cell that opened an editor with
 * no caret.
 */
@RunWith(AndroidJUnit4::class)
class CellInputFocusTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun focusInsideTheInputAreaIsReportedAsEditing() {
    val reported = mutableListOf<Boolean>()
    compose.setContent { CellUnderTest(onFocusChanged = reported::add) }

    compose.onNodeWithTag("field").performClick()

    compose.runOnIdle { assertEquals(listOf(true), reported) }
  }

  /**
   * The reason the modifier is on the input area and not on the cell: a header button taking
   * focus is not the user typing, and treating it as such would put the notebook in edit mode
   * with the caret nowhere.
   */
  @Test
  fun focusOnSomethingBesideTheInputAreaIsNotReported() {
    val reported = mutableListOf<Boolean>()
    compose.setContent { CellUnderTest(onFocusChanged = reported::add) }

    compose.onNodeWithTag("beside").performClick()

    compose.runOnIdle { assertEquals(emptyList<Boolean>(), reported) }
  }

  @Test
  fun losingFocusToSomethingOutsideTheInputAreaIsReported() {
    val reported = mutableListOf<Boolean>()
    compose.setContent { CellUnderTest(onFocusChanged = reported::add) }

    compose.onNodeWithTag("field").performClick()
    compose.onNodeWithTag("beside").performClick()

    compose.runOnIdle { assertEquals(listOf(true, false), reported) }
  }

  /** A cell shaped like the real one: an input area with a field, and a control beside it. */
  @androidx.compose.runtime.Composable
  private fun CellUnderTest(onFocusChanged: (Boolean) -> Unit) {
    Column {
      androidx.compose.foundation.layout.Box(modifier = onCellInputFocus(onFocusChanged)) {
        BasicTextField(
          state = rememberTextFieldState(initialText = "print(1)"),
          modifier = Modifier.fillMaxWidth().testTag("field"),
        )
      }
      BasicTextField(
        state = rememberTextFieldState(initialText = "beside"),
        modifier = Modifier.fillMaxWidth().testTag("beside"),
      )
    }
  }
}

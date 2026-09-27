package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P5e-5's field half: a `caretLanding` arriving at the field whose cell it names.
 *
 * What VS Code's `_openReference` does in `goToCommands.ts` - `Range.collapseToStart` for the
 * caret, `.focus()` for the editor, the `symbolHighlight` mark for the target range - becomes a
 * `TextFieldState` edit, a `FocusRequester`, and a `symbolFlash` drawBehind here. The jump's
 * other half - scrolling the cell into view and saying "No definition found" on a miss - is the
 * screen's, and the Maestro flow's to prove end to end.
 *
 * Instrumented rather than JVM because the questions are what a composed `BasicTextField` holds
 * and takes, which no unit test can answer.
 */
@RunWith(AndroidJUnit4::class)
class DefinitionLandingTest {
  @get:Rule val compose = createComposeRule()

  private val state = TextFieldState(CODE)
  private var landing by mutableStateOf<DefinitionTarget?>(null)

  /** `Range.collapseToStart` - the caret takes the definition's start, not its selection. */
  @Test
  fun aLanding_movesTheCaretToTheTargetStart() {
    showField()

    landOn(DefinitionTarget(CELL_ID, offset = 4, endOffset = 17))

    compose.runOnIdle { assertEquals(TextRange(4), state.selection) }
  }

  /**
   * `.focus()` - the editor the jump lands in takes focus, so typing carries on there. The
   * UX choice the plan names: the landing field focuses and the IME may rise, exactly as the
   * reference's does.
   */
  @Test
  fun aLanding_focusesTheField() {
    showField()
    compose.onNodeWithTag(FIELD_TAG).assertIsNotFocused()

    landOn(DefinitionTarget(CELL_ID, offset = 4, endOffset = 17))

    compose.onNodeWithTag(FIELD_TAG).assertIsFocused()
  }

  /** The one-shot is reported so the state can clear it - [NotebookCellEdits.caretLanded]. */
  @Test
  fun aLanding_reportsItselfTaken() {
    var reported = 0
    showField(onCaretLanded = { reported++ })

    landOn(DefinitionTarget(CELL_ID, offset = 4, endOffset = 17))

    compose.runOnIdle { assertEquals(1, reported) }
  }

  /**
   * A target whose offset falls past the text - a cell edited between ask and landing - clamps
   * to the end rather than crash `state.edit`: the jump still lands, just less precisely.
   */
  @Test
  fun aLandingPastTheText_clampsToItsEnd() {
    showField()

    landOn(DefinitionTarget(CELL_ID, offset = CODE.length + 40, endOffset = CODE.length + 50))

    compose.runOnIdle { assertEquals(TextRange(CODE.length), state.selection) }
  }

  /**
   * The state clears the landing once it is taken; clearing must not re-run the effect and move
   * the caret back to 0. A second landing is a second non-null key, which *does* run again.
   */
  @Test
  fun aClearedLanding_leavesTheCaretAlone_andANewOneMovesItAgain() {
    showField()
    landOn(DefinitionTarget(CELL_ID, offset = 4, endOffset = 17))
    compose.runOnIdle { state.edit { selection = TextRange(0) } }

    landOn(null)
    compose.runOnIdle { assertEquals(TextRange(0), state.selection) }

    landOn(DefinitionTarget(CELL_ID, offset = 9, endOffset = 22))
    compose.runOnIdle { assertEquals(TextRange(9), state.selection) }
  }

  private fun landOn(target: DefinitionTarget?) {
    compose.runOnUiThread { landing = target }
    compose.waitForIdle()
  }

  private fun showField(onCaretLanded: () -> Unit = {}) {
    compose.setContent {
      JupyterDroidTheme {
        CodeCellField(
          state = state,
          onSourceEdited = {},
          caretLanding = landing,
          onCaretLanded = onCaretLanded,
          modifier = Modifier.testTag(FIELD_TAG),
        )
      }
    }
  }

  private companion object {
    const val CELL_ID = "cell-1"
    const val FIELD_TAG = "definition-field"
    const val CODE = "def compute_total(values):\n    return sum(values)\n\ntotal = compute_total(values)"
  }
}

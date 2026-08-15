package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A field on screen adopting a source it did not type.
 *
 * Nothing needed this until `notebook:split-cell-at-cursor`: no cell operation rewrote a cell that
 * stayed in the notebook, so the field seeded itself once and was the only author of its text
 * afterwards. Split rewrites the surviving cell, and its undo rewrites it back.
 *
 * **The hard half is not adopting - it is knowing when not to.** The field and the model disagree
 * routinely, because an edit is reported on a 150ms debounce; a field that is merely *ahead* looks
 * exactly like one that is behind. So the trigger is an explicit revision
 * ([EditorUiState.sourceRevisions]) and never a comparison, and the second case here is the one
 * that would silently eat keystrokes if that were got wrong.
 *
 * Instrumented rather than JVM because the question is what a composed `BasicTextField` holds,
 * which no unit test can answer.
 */
@RunWith(AndroidJUnit4::class)
class CodeCellFieldTest {
  @get:Rule val compose = createComposeRule()

  private val state = TextFieldState("first\nsecond")
  private var source by mutableStateOf("first\nsecond")
  private var revision by mutableIntStateOf(0)

  @Test
  fun aBumpedRevisionPutsTheNotebooksTextIntoTheFieldOnScreen() {
    showField()

    rewriteTo("second", revision = 1)

    compose.runOnIdle { assertEquals("second", state.text.toString()) }
  }

  /**
   * The debounce case, and the reason this is a counter rather than a diff: the model is one
   * report behind whatever was just typed, and adopting on that difference would revert it.
   */
  @Test
  fun aSourceChangeWithNoBumpIsIgnored() {
    showField()

    rewriteTo("the model catching up", revision = 0)

    compose.runOnIdle { assertEquals("first\nsecond", state.text.toString()) }
  }

  /**
   * The caret goes to the start of what arrived. For a split that is the split point itself - the
   * surviving fragment begins where the caret was - so typing carries on where it left off.
   */
  @Test
  fun theCaretLandsAtTheStartOfTheAdoptedText() {
    showField()

    rewriteTo("second", revision = 1)

    compose.runOnIdle { assertEquals(TextRange(0), state.selection) }
  }

  /** Two rewrites in a row - split, then undo - and the field has to notice both. */
  @Test
  fun aSecondBumpIsAdoptedToo() {
    showField()

    rewriteTo("second", revision = 1)
    rewriteTo("first\nsecond", revision = 2)

    compose.runOnIdle { assertEquals("first\nsecond", state.text.toString()) }
  }

  private fun rewriteTo(text: String, revision: Int) {
    compose.runOnUiThread {
      source = text
      this.revision = revision
    }
    compose.waitForIdle()
  }

  private fun showField() {
    compose.setContent {
      JupyterDroidTheme {
        CodeCellField(state = state, source = source, sourceRevision = revision, onSourceEdited = {})
      }
    }
  }
}

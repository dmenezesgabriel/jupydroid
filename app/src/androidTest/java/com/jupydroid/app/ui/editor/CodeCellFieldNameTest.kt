package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.spaces.StartupScriptField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A focusable text field a screen reader can only call "edit box" is a field with no name
 * (TalkBack's rule: every actionable node has one). The name is on the node that takes the text, so
 * the one string a screen reader speaks is the one a flow addresses.
 */
@RunWith(AndroidJUnit4::class)
class CodeCellFieldNameTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aFieldIsNamedByWhatItsCallerSaysItEdits() {
    compose.setContent {
      JupydroidTheme {
        CodeCellField(state = rememberTextFieldState(), onSourceEdited = {}, fieldName = "Console input")
      }
    }

    compose.onNodeWithContentDescription("Console input").performTextInput("1 + 1")

    assertEquals(1, namedNodes("Console input"))
    val namedAndTypeable = hasContentDescription("Console input") and hasSetTextAction()
    assertEquals(1, compose.onAllNodes(namedAndTypeable).fetchSemanticsNodes().size)
  }

  @Test
  fun aCallerThatSaysNothingStillGetsAName() {
    compose.setContent {
      JupydroidTheme { CodeCellField(state = rememberTextFieldState(), onSourceEdited = {}) }
    }

    assertEquals(1, namedNodes("Code editor"))
  }

  /** The heading above the field names the same thing, so it is drawn and cleared out of the tree. */
  @Test
  fun theStartupScriptHasOneNodeAnsweringToItsHeading() {
    compose.setContent {
      JupydroidTheme { StartupScriptField(rememberTextFieldState("export X=1")) }
    }

    assertEquals(1, namedNodes("Startup script"))
    assertEquals(0, compose.onAllNodes(hasText("Startup script")).fetchSemanticsNodes().size)
  }

  private fun namedNodes(name: String): Int = compose.onAllNodes(hasContentDescription(name)).fetchSemanticsNodes().size
}

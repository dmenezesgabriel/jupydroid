package com.jupydroid.app.ui.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A cell blocked in `input()` has a field to answer it from - whether there is something visible to
 * type into is exactly what no JVM test can say.
 */
@RunWith(AndroidJUnit4::class)
class CellInputPromptTest {
  @get:Rule val compose = createComposeRule()

  /** `input("Name?")` has printed nothing yet, and the field is still there to type into. */
  @Test
  fun aCellWaitingOnInputDrawsALabelledFieldWithNoOutputsAbove() {
    show(request = asked(password = false)) {}

    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).assertIsDisplayed()
    compose.onNodeWithText("Name?").assertIsDisplayed()
  }

  /** Done submits what was typed, as `input()` will receive it. */
  @Test
  fun doneSubmitsWhatWasTyped() {
    val submitted = mutableListOf<String>()
    show(request = asked(password = false)) { submitted += it }

    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).performTextInput("Ada")
    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).performImeAction()

    assertEquals(listOf("Ada"), submitted)
  }

  /** `getpass()`: what is typed is never on screen, yet it is what gets submitted. */
  @Test
  fun aPasswordPromptMasksWhatIsTyped() {
    val submitted = mutableListOf<String>()
    show(request = asked(password = true)) { submitted += it }

    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).performTextInput("abc")
    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).performImeAction()

    // `EditableText` is what the field draws; `InputText` - which `onNodeWithText` also matches -
    // is the raw value an accessibility service may read, and holds `abc` whatever is shown.
    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("•••")))
    assertEquals(listOf("abc"), submitted)
  }

  /** Nothing is waiting, so nothing is drawn to answer. */
  @Test
  fun aCellNotWaitingDrawsNoField() {
    show(request = null) {}

    compose.onNodeWithContentDescription(INPUT_PROMPT_DESCRIPTION).assertDoesNotExist()
  }

  private fun show(request: KernelInputRequest?, onAnswer: (String) -> Unit) {
    compose.setContent {
      JupydroidTheme {
        CellOutputs(
          outputs = emptyList(),
          hidden = false,
          scrolled = false,
          search = emptyList(),
          onExpand = {},
          inputRequest = request,
          onAnswerInput = onAnswer,
        )
      }
    }
  }

  private fun asked(password: Boolean) = KernelInputRequest(
    prompt = if (password) "Secret?" else "Name?",
    password = password,
    requestHeader = "{}",
  )
}

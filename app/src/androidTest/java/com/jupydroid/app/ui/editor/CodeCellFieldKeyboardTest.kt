package com.jupydroid.app.ui.editor

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the code field asks of the keyboard. Source is not prose: on SM-X216B, Gboard split a typed
 * `import getpass` into `import get pass` - a syntax error the user never wrote - and went on doing
 * so with `TYPE_TEXT_FLAG_AUTO_CORRECT` cleared. Read here off the [EditorInfo] the field hands the
 * IME, through Compose's own interception hook, rather than assumed from what the field wrote.
 */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class CodeCellFieldKeyboardTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theCodeFieldAsksTheKeyboardNotToAutocorrect() {
    val autocorrect = askedInputType() and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
    assertEquals("the field asked for autocorrect", 0, autocorrect)
  }

  @Test
  fun theCodeFieldAsksTheKeyboardForNoSuggestions() {
    val noSuggestions = askedInputType() and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    assertEquals("the field left suggestions on", InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, noSuggestions)
  }

  @Test
  fun theCodeFieldAsksForTheVisiblePasswordVariation() {
    val variation = askedInputType() and InputType.TYPE_MASK_VARIATION
    assertEquals(
      "the field kept a variation Gboard composes in",
      InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      variation,
    )
  }

  @Test
  fun theCodeFieldStaysMultiLineText() {
    val shape = askedInputType() and (InputType.TYPE_MASK_CLASS or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    assertEquals(
      "Enter must still break a line",
      InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
      shape,
    )
  }

  /** The variation is the keyboard's only: Compose's own password field would turn off copy and cut. */
  @Test
  fun theCodeFieldIsNoPasswordField() {
    askedInputType()
    compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
  }

  private fun askedInputType(): Int {
    var asked: EditorInfo? = null

    compose.setContent {
      JupyterDroidTheme {
        InterceptPlatformTextInput(
          interceptor = { request, _ ->
            asked = EditorInfo().also { request.createInputConnection(it) }
            awaitCancellation()
          },
        ) {
          CodeCellField(state = rememberTextFieldState("import getpass"), onSourceEdited = {})
        }
      }
    }
    compose.onNode(hasSetTextAction()).performClick()
    compose.waitUntil(TIMEOUT_MS) { asked != null }
    return requireNotNull(asked).inputType
  }

  private companion object {
    const val TIMEOUT_MS = 5_000L
  }
}

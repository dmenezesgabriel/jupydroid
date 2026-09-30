package com.jupydroid.app.ui.editor

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageMarkup
import com.jupydroid.app.domain.port.LanguageMarkupKind
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The menu row's ask reaches the field as a bumped [symbolAsk], the field
 * asks at the caret, and the answer draws as markdown - the fenced signature block and the
 * docstring line, VS Code's `editor.contrib.hover` shape with a finger. Dismissal runs through
 * `Popup.onDismissRequest`, which a BACK press drives; an outside tap is Maestro's half (see
 * `DiagnosticMarkTapTest` for the harness's reason).
 */
@RunWith(AndroidJUnit4::class)
class SymbolInfoPopupTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun asking_showsTheAnsweredMarkdown() {
    val asks = mutableIntStateOf(0)
    setContent(symbolAsk = asks)

    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsNotDisplayed()
    compose.runOnIdle { asks.intValue = 1 }

    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsDisplayed()
    compose.onNodeWithText("compute_total(values)").assertIsDisplayed()
    compose.onNodeWithText("Sum the values.").assertIsDisplayed()
  }

  @Test
  fun theAsk_goesAtTheCaret() {
    val asks = mutableIntStateOf(0)
    val positions = mutableListOf<Int>()
    val field = setContent(symbolAsk = asks) { _, offset ->
      positions += offset
      HOVER
    }

    compose.runOnIdle { field.edit { selection = TextRange(CARET_AFTER_TOTAL) } }
    compose.runOnIdle { asks.intValue = 1 }

    // The popup displaying proves the ask ran - it only draws a non-null answer - which makes
    // it the self-waiting signal `positions` cannot be.
    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsDisplayed()
    assertEquals(listOf(CARET_AFTER_TOTAL), positions)
  }

  @Test
  fun askingAgain_afterDismiss_reasks() {
    val asks = mutableIntStateOf(0)
    var answered = 0
    setContent(symbolAsk = asks) { _, _ ->
      answered++
      HOVER
    }

    compose.runOnIdle { asks.intValue = 1 }
    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsDisplayed()
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsNotDisplayed()

    compose.runOnIdle { asks.intValue = 2 }
    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsDisplayed()
    assertEquals(2, answered)
  }

  @Test
  fun aNullAnswer_showsNothing() {
    val asks = mutableIntStateOf(0)
    setContent(symbolAsk = asks) { _, _ -> null }

    compose.runOnIdle { asks.intValue = 1 }

    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsNotDisplayed()
  }

  @Test
  fun pressingBackDismissesThePopup() {
    val asks = mutableIntStateOf(0)
    setContent(symbolAsk = asks)

    compose.runOnIdle { asks.intValue = 1 }
    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsDisplayed()
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsNotDisplayed()
  }

  /**
   * VS Code's rule - `contentHoverController` cancels the hover on `onDidChangeModelContent`:
   * text that changed under the answer means the answer no longer describes it, so the card
   * leaves rather than keep quoting a symbol the source has since edited.
   */
  @Test
  fun typingAfterwards_clearsTheCard() {
    val asks = mutableIntStateOf(0)
    val field = setContent(symbolAsk = asks)

    compose.runOnIdle { asks.intValue = 1 }
    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsDisplayed()

    compose.runOnIdle { field.edit { append(' ') } }
    compose.onNodeWithContentDescription(HOVER_DESCRIPTION).assertIsNotDisplayed()
  }

  private fun setContent(
    symbolAsk: MutableIntState,
    hoverAt: suspend (code: String, cursorOffset: Int) -> LanguageHover? = { _, _ -> HOVER },
  ): TextFieldState {
    lateinit var field: TextFieldState
    compose.setContent {
      JupyterDroidTheme {
        Box(modifier = Modifier.width(FIELD_WIDTH).testTag(FIELD_TAG)) {
          field = rememberTextFieldState(CODE)
          CodeCellField(
            state = field,
            onSourceEdited = {},
            hoverAt = hoverAt,
            symbolAsk = symbolAsk.intValue,
          )
        }
      }
    }
    return field
  }

  private companion object {
    const val CODE = "total = compute_total(values)"

    /** The caret after `total` - offset 5, inside the name the hover would be asked for. */
    const val CARET_AFTER_TOTAL = 5
    const val FIELD_TAG = "symbol-info-field"
    val FIELD_WIDTH = 300.dp
    val HOVER = LanguageHover(
      LanguageMarkup(
        LanguageMarkupKind.MARKDOWN,
        "```python\ncompute_total(values)\n```\n\nSum the values.",
      ),
    )
  }
}
